package com.example.network

import com.example.engine.OptionsGreeksCalculator
import com.example.model.BuildupType
import com.example.model.Candle
import com.example.model.IndstocksChargesBreakdown
import com.example.model.IndstocksFundsSummary
import com.example.model.IndstocksHolding
import com.example.model.IndstocksInstrumentRow
import com.example.model.IndstocksMarginPreview
import com.example.model.IndstocksMarketDepthLevel
import com.example.model.IndstocksMarketDepthSnapshot
import com.example.model.IndstocksOptionChainResult
import com.example.model.IndstocksOptionLegSecurity
import com.example.model.IndstocksOrderModifyRequest
import com.example.model.IndstocksOrderPlacementResult
import com.example.model.IndstocksOrderRecord
import com.example.model.IndstocksOrderRequest
import com.example.model.IndstocksPosition
import com.example.model.IndstocksQuoteItem
import com.example.model.IndstocksSmartOrderModifyRequest
import com.example.model.IndstocksSmartOrderRequest
import com.example.model.IndstocksTradeRecord
import com.example.model.IndstocksUserProfile
import com.example.model.OptionStrike
import com.example.model.OptionsChainSnapshot
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HTTP REST client for INDstocks Trading API Suite v1 (https://api.indstocks.com).
 *
 * Strictly aligned with INDstocks OpenAPI 3.0 specification:
 * - Standard endpoints send `Authorization: <access_token>` (RAW token — NO "Bearer " prefix).
 * - `/generate/token` uses `x-api-key: <Client ID>` with JSON `{ "mpin": "...", "totp": "..." }`.
 * - GET endpoints accepting JSON bodies (`/margin`, `/order`, `/order/trades`) use an HTTP/1.1
 *   socket transport because OkHttp forbids request bodies on GET.
 * - Normalizes historical candle `ts` (seconds -> milliseconds), decodes `{ts, o, h, l, c, v}` objects,
 *   uses camelCase `/margin` payloads, handles nested option-chain `greeks` + `oi - previous_oi`,
 *   and supports all order/smart-order place, modify, and cancel endpoints.
 */
class IndstocksApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.indstocks.com"
        const val NIFTY_UNDERLYING_SCRIP = "40000001"
        const val NIFTY_INDEX_SCRIP_CODE = "NSE_40000001"
        const val INDIA_VIX_SCRIP_CODE = "NSE_26017"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun sanitizeToken(rawToken: String): String {
            return rawToken.trim()
                .removePrefix("Bearer ")
                .removePrefix("bearer ")
                .trim()
        }

        fun sanitizeRemarks(raw: String, fallback: String = "NithixNiftyDesk"): String {
            val trimmed = raw.trim().take(100)
            return if (trimmed.equals("TV-TERMINAL", ignoreCase = true) || trimmed.isBlank()) {
                fallback
            } else {
                trimmed
            }
        }

        fun defaultAlgoIdForExchange(exchange: String): String =
            if (exchange.equals("BSE", ignoreCase = true)) "9999999999999999" else "99999"

        fun mapTimeframeToIntervalAndLookbackMs(timeframe: Timeframe): Pair<String, Long> = when (timeframe) {
            Timeframe.M1 -> "1minute" to TimeUnit.DAYS.toMillis(2)
            Timeframe.M3 -> "3minute" to TimeUnit.DAYS.toMillis(3)
            Timeframe.M5 -> "5minute" to TimeUnit.DAYS.toMillis(5)
            Timeframe.M10 -> "5minute" to TimeUnit.DAYS.toMillis(7)
            Timeframe.M15 -> "15minute" to TimeUnit.DAYS.toMillis(10)
            Timeframe.M30 -> "30minute" to TimeUnit.DAYS.toMillis(14)
            Timeframe.M45 -> "15minute" to TimeUnit.DAYS.toMillis(20)
            Timeframe.H1 -> "60minute" to TimeUnit.DAYS.toMillis(30)
            Timeframe.H2 -> "60minute" to TimeUnit.DAYS.toMillis(45)
            Timeframe.H4 -> "60minute" to TimeUnit.DAYS.toMillis(60)
            Timeframe.DAILY -> "1day" to TimeUnit.DAYS.toMillis(365)
            Timeframe.WEEKLY -> "1week" to TimeUnit.DAYS.toMillis(730)
            Timeframe.MONTHLY -> "1month" to TimeUnit.DAYS.toMillis(1825)
        }
    }

    suspend fun generateToken(
        clientId: String,
        mpin: String,
        totp: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanClientId = clientId.trim()
            val cleanMpin = mpin.trim()
            val cleanTotp = totp.trim()
            require(cleanClientId.isNotBlank()) { "INDstocks Client ID (x-api-key) is required" }
            require(cleanMpin.isNotBlank()) { "MPIN is required" }
            require(cleanTotp.length == 6 && cleanTotp.all { it.isDigit() }) { "Enter a valid 6-digit TOTP" }

            val payload = JSONObject().apply {
                put("mpin", cleanMpin)
                put("totp", cleanTotp)
            }.toString()

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/generate/token")
                .header("x-api-key", cleanClientId)
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = parseJsonObjectSafe(raw)
                if (!response.isSuccessful || isFailureEnvelope(json)) {
                    throw IllegalStateException(extractErrorMessage(response.code, json, raw))
                }
                val token = json?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: json?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Token missing in /generate/token response")
                sanitizeToken(token)
            }
        }
    }

    suspend fun fetchUserProfile(accessToken: String): Result<IndstocksUserProfile> = withContext(Dispatchers.IO) {
        executeStandardGet(accessToken, "/user/profile").map { json ->
            val data = json.optJSONObject("data") ?: json
            val firstName = data.optString("first_name", "").trim()
            val lastName = data.optString("last_name", "").trim()
            val combinedName = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
                .ifBlank { data.optString("name", data.optString("user_name", "INDstocks Trader")) }

            val isNse = data.optBoolean("is_nse_onboarded", false)
            val isBse = data.optBoolean("is_bse_onboarded", false)
            val isNseFno = data.optBoolean("is_nse_fno_onboarded", false)
            val isBseFno = data.optBoolean("is_bse_fno_onboarded", false)

            val explicitSegments = buildList {
                val arr = data.optJSONArray("segments") ?: data.optJSONArray("active_segments")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }
            val derivedSegments = buildList {
                if (isNse) add("NSE")
                if (isBse) add("BSE")
                if (isNseFno) add("NFO")
                if (isBseFno) add("BFO")
            }

            IndstocksUserProfile(
                userId = data.optString("user_id", ""),
                ucc = data.optString("ucc", data.optString("client_id", "")),
                firstName = firstName,
                lastName = lastName,
                name = combinedName,
                email = data.optString("email", ""),
                dematId = data.optString("demat_id", ""),
                phone = data.optString("phone", data.optString("mobile", "")),
                isDdpiActive = data.optBoolean("is_ddpi_active", false),
                isNseOnboarded = isNse,
                isBseOnboarded = isBse,
                isNseFnoOnboarded = isNseFno,
                isBseFnoOnboarded = isBseFno,
                segments = (derivedSegments.ifEmpty { explicitSegments }).distinct()
            )
        }
    }

    suspend fun fetchFunds(accessToken: String): Result<IndstocksFundsSummary> = withContext(Dispatchers.IO) {
        executeStandardGet(accessToken, "/funds").map { json ->
            val data = json.optJSONObject("data") ?: json
            val detailed = data.optJSONObject("detailed_avl_balance")
            val optionBuy = detailed?.optDoubleOr("option_buy", default = 0.0) ?: 0.0
            val optionSell = detailed?.optDoubleOr("option_sell", default = 0.0) ?: 0.0
            val future = detailed?.optDoubleOr("future", default = 0.0) ?: 0.0
            val eqMis = detailed?.optDoubleOr("eq_mis", default = 0.0) ?: 0.0
            val eqCnc = detailed?.optDoubleOr("eq_cnc", default = 0.0) ?: 0.0
            val withdrawal = data.optDoubleOr("withdrawal_balance", default = 0.0)
            val sod = data.optDoubleOr("sod_balance", "opening_balance", default = 0.0)
            val pledgeReceived = data.optDoubleOr("pledge_received", "collateral", "collateral_value", default = 0.0)
            val realized = data.optDoubleOr("realized_pnl", default = 0.0)
            val unrealized = data.optDoubleOr("unrealized_pnl", default = 0.0)
            val brokerage = data.optDoubleOr("brokerage", default = 0.0)
            val eqCharges = data.optDoubleOr("eq_charges", default = 0.0)
            val fnoCharges = data.optDoubleOr("fno_charges", default = 0.0)

            val fallbackAvail = data.optDoubleOr("available_cash", "available_margin", "net_available", default = 0.0)
            val available = when {
                optionBuy > 0.0 -> optionBuy
                future > 0.0 -> future
                optionSell > 0.0 -> optionSell
                withdrawal > 0.0 -> withdrawal
                fallbackAvail > 0.0 -> fallbackAvail
                else -> sod
            }
            val opening = if (sod > 0.0) sod else available
            val used = data.optDoubleOr(
                "utilized_margin", "used_margin",
                default = max(0.0, (opening + pledgeReceived) - max(available, withdrawal))
            )

            IndstocksFundsSummary(
                availableMargin = available,
                optionBuyBalance = optionBuy,
                optionSellBalance = optionSell,
                futureBalance = future,
                eqMisBalance = eqMis,
                eqCncBalance = eqCnc,
                withdrawalBalance = withdrawal,
                sodBalance = sod,
                usedMargin = used,
                collateral = pledgeReceived,
                openingBalance = opening,
                realizedPnl = realized,
                unrealizedPnl = unrealized,
                brokerage = brokerage,
                eqCharges = eqCharges,
                fnoCharges = fnoCharges,
                totalBalance = opening + pledgeReceived
            )
        }
    }

    suspend fun fetchMarketQuotes(
        accessToken: String,
        scripCodes: List<String>
    ): Result<Map<String, IndstocksQuoteItem>> = withContext(Dispatchers.IO) {
        if (scripCodes.isEmpty()) return@withContext Result.success(emptyMap())
        val codesParam = URLEncoder.encode(scripCodes.distinct().joinToString(","), "UTF-8")
        executeStandardGet(accessToken, "/market/quotes/full?scrip-codes=$codesParam").map { json ->
            val data = json.optJSONObject("data") ?: json
            buildMap {
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = data.optJSONObject(key) ?: continue
                    val ltp = obj.optDoubleOr("live_price", "ltp", "last_price", "close", default = 0.0)
                    if (ltp <= 0.0) continue
                    val prevClose = obj.optDoubleOr("prev_close", "previous_close", "close", default = ltp)
                    val open = obj.optDoubleOr("day_open", "open", default = ltp)
                    val high = obj.optDoubleOr("day_high", "high", default = max(ltp, open))
                    val low = obj.optDoubleOr("day_low", "low", default = min(ltp, open))
                    val change = obj.optDoubleOr("day_change", "change", "net_change", default = ltp - prevClose)
                    val changePct = obj.optDoubleOr(
                        "day_change_percentage",
                        "change_percent",
                        "pchange",
                        default = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0
                    )
                    put(
                        key,
                        IndstocksQuoteItem(
                            scripCode = key,
                            symbol = obj.optString("symbol", key),
                            lastPrice = ltp,
                            open = open,
                            high = high,
                            low = low,
                            prevClose = prevClose,
                            change = change,
                            changePct = changePct,
                            volume = obj.optLongOr("volume", "total_traded_volume", default = 0L),
                            upperCircuit = obj.optDoubleOr("upper_circuit", default = 0.0),
                            lowerCircuit = obj.optDoubleOr("lower_circuit", default = 0.0),
                            openInterest = obj.optLongOr("open_interest", default = 0L),
                            openInterestChange = obj.optLongOr("open_interest_change", default = 0L),
                            timestampSec = normalizeToSeconds(obj.optLongOr("ts", default = System.currentTimeMillis() / 1000L))
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchLtpQuotes(
        accessToken: String,
        scripCodes: List<String>
    ): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        if (scripCodes.isEmpty()) return@withContext Result.success(emptyMap())
        val codesParam = URLEncoder.encode(scripCodes.distinct().joinToString(","), "UTF-8")
        executeStandardGet(accessToken, "/market/quotes/ltp?scrip-codes=$codesParam").map { json ->
            val data = json.optJSONObject("data") ?: json
            buildMap {
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = data.optJSONObject(key) ?: continue
                    val ltp = obj.optDoubleOr("live_price", "ltp", "last_price", default = 0.0)
                    if (ltp > 0.0) put(key, ltp)
                }
            }
        }
    }

    suspend fun fetchMarketDepth(
        accessToken: String,
        scripCode: String
    ): Result<IndstocksMarketDepthSnapshot?> = withContext(Dispatchers.IO) {
        val codesParam = URLEncoder.encode(scripCode.trim(), "UTF-8")
        executeStandardGet(accessToken, "/market/quotes/mkt?scrip-codes=$codesParam").map { json ->
            val data = json.optJSONObject("data") ?: return@map null
            val item = data.optJSONObject(scripCode) ?: return@map null
            val md = item.optJSONObject("market_depth") ?: return@map null
            val agg = md.optJSONObject("aggregate")
            val depthArr = md.optJSONArray("depth") ?: JSONArray()
            val levels = buildList {
                for (i in 0 until depthArr.length()) {
                    val row = depthArr.optJSONObject(i) ?: continue
                    val buy = row.optJSONObject("buy")
                    val sell = row.optJSONObject("sell")
                    add(
                        IndstocksMarketDepthLevel(
                            buyQty = parseFormattedNumber(buy?.optString("quantity")),
                            buyPrice = parseFormattedNumber(buy?.optString("price")),
                            sellQty = parseFormattedNumber(sell?.optString("quantity")),
                            sellPrice = parseFormattedNumber(sell?.optString("price"))
                        )
                    )
                }
            }
            IndstocksMarketDepthSnapshot(
                scripCode = scripCode,
                totalBuyQty = agg?.optString("total_buy", "0").orEmpty(),
                totalSellQty = agg?.optString("total_sell", "0").orEmpty(),
                buyPercentage = agg?.optDouble("buy_percentage", 50.0) ?: 50.0,
                sellPercentage = agg?.optDouble("sell_percentage", 50.0) ?: 50.0,
                levels = levels
            )
        }
    }

    suspend fun fetchHistoricalCandles(
        accessToken: String,
        scripCode: String,
        timeframe: Timeframe,
        endTimeMs: Long = System.currentTimeMillis()
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val (interval, lookbackMs) = mapTimeframeToIntervalAndLookbackMs(timeframe)
        val startTimeMs = (endTimeMs - lookbackMs).coerceAtLeast(0L)
        val encodedCode = URLEncoder.encode(scripCode, "UTF-8")
        val path = "/market/historical/$interval?scrip-codes=$encodedCode&start_time=$startTimeMs&end_time=$endTimeMs"

        executeStandardGet(accessToken, path).map { json ->
            val dataObj = json.optJSONObject("data") ?: json
            val candlesArray = dataObj.optJSONObject(scripCode)?.optJSONArray("candles")
                ?: dataObj.optJSONArray("candles")
                ?: run {
                    var found: JSONArray? = null
                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = dataObj.optJSONObject(k)?.optJSONArray("candles") ?: dataObj.optJSONArray(k)
                        if (arr != null) {
                            found = arr
                            break
                        }
                    }
                    found ?: JSONArray()
                }

            val candles = mutableListOf<Candle>()
            var vwapNum = 0.0
            var vwapDen = 0.0
            var fallbackSum = 0.0

            data class RawCandleRow(val ts: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)
            for (i in 0 until candlesArray.length()) {
                val item = candlesArray.opt(i) ?: continue
                val row = when (item) {
                    // Official spec returns objects: {"ts": ..., "o": ..., "h": ..., "l": ..., "c": ..., "v": ...}
                    is JSONObject -> RawCandleRow(
                        ts = item.optLongOr("ts", "timestamp", default = 0L),
                        o = item.optDoubleOr("o", "open", default = Double.NaN),
                        h = item.optDoubleOr("h", "high", default = Double.NaN),
                        l = item.optDoubleOr("l", "low", default = Double.NaN),
                        c = item.optDoubleOr("c", "close", default = Double.NaN),
                        v = item.optDoubleOr("v", "volume", default = 0.0)
                    )
                    is JSONArray -> if (item.length() >= 5) {
                        RawCandleRow(
                            ts = item.optLong(0, 0L),
                            o = item.optDouble(1, Double.NaN),
                            h = item.optDouble(2, Double.NaN),
                            l = item.optDouble(3, Double.NaN),
                            c = item.optDouble(4, Double.NaN),
                            v = if (item.length() > 5) item.optDouble(5, 0.0) else 0.0
                        )
                    } else continue
                    else -> continue
                }
                val (rawTs, o, h, l, c, v) = row
                if (o.isNaN() || h.isNaN() || l.isNaN() || c.isNaN() || c <= 0.0) continue
                val tsMs = if (rawTs in 1L..9_999_999_999L) rawTs * 1000L else rawTs
                val vol = max(0L, v.toLong())
                val tp = (h + l + c) / 3.0
                val vwap = if (vol > 0L) {
                    vwapNum += tp * vol
                    vwapDen += vol
                    vwapNum / vwapDen
                } else {
                    fallbackSum += tp
                    fallbackSum / (candles.size + 1)
                }

                candles.add(
                    Candle(
                        timestamp = tsMs,
                        open = o,
                        high = max(h, max(o, c)),
                        low = min(l, min(o, c)),
                        close = c,
                        volume = vol,
                        vwap = vwap
                    )
                )
            }
            candles.sortedBy { it.timestamp }.takeLast(60)
        }
    }

    /**
     * GET /market/instruments/expiries?underlying=NIFTY&segment=DERIVATIVE
     */
    suspend fun fetchExpiries(
        accessToken: String,
        underlying: String = "NIFTY",
        segment: String = "DERIVATIVE"
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val encUnderlying = URLEncoder.encode(underlying.trim(), "UTF-8")
        val encSegment = URLEncoder.encode(segment.trim().uppercase(), "UTF-8")
        val path = "/market/instruments/expiries?underlying=$encUnderlying&segment=$encSegment"
        executeStandardGet(accessToken, path).map { json ->
            val data = json.opt("data")
            val arr = when (data) {
                is JSONArray -> data
                is JSONObject -> data.optJSONArray("expiries") ?: JSONArray()
                else -> json.optJSONArray("expiries") ?: JSONArray()
            }
            buildList {
                for (i in 0 until arr.length()) {
                    val exp = arr.optString(i).trim()
                    if (exp.isNotEmpty()) add(exp)
                }
            }
        }
    }

    /**
     * GET /market/instruments/search — Discover active option/futures contracts & exact `lot_size` + `security_id`.
     */
    suspend fun searchDerivativeContracts(
        accessToken: String,
        underlying: String = "NIFTY",
        segment: String = "DERIVATIVE",
        instrumentType: String? = null,
        expiry: String? = null,
        strikeFrom: Double? = null,
        strikeTo: Double? = null,
        optionType: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): Result<List<IndstocksInstrumentRow>> = withContext(Dispatchers.IO) {
        val params = buildList {
            add("underlying=${URLEncoder.encode(underlying.trim(), "UTF-8")}")
            add("segment=${URLEncoder.encode(segment.trim().uppercase(), "UTF-8")}")
            instrumentType?.takeIf { it.isNotBlank() }?.let { add("instrument_type=${URLEncoder.encode(it.uppercase(), "UTF-8")}") }
            expiry?.takeIf { it.isNotBlank() }?.let { add("expiry=${URLEncoder.encode(it.trim(), "UTF-8")}") }
            strikeFrom?.let { add("strike_from=${it.roundToInt()}") }
            strikeTo?.let { add("strike_to=${it.roundToInt()}") }
            optionType?.takeIf { it.isNotBlank() }?.let { add("option_type=${URLEncoder.encode(it.uppercase(), "UTF-8")}") }
            add("page=$page")
            add("page_size=${pageSize.coerceIn(1, 100)}")
        }.joinToString("&")

        executeStandardGet(accessToken, "/market/instruments/search?$params").map { json ->
            val data = json.optJSONObject("data") ?: json
            val arr = data.optJSONArray("instruments") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val secId = obj.optString("security_id", "").trim()
                    val tradingSymbol = obj.optString("trading_symbol", "").trim()
                    if (secId.isEmpty() || tradingSymbol.isEmpty()) continue
                    val optType = if (obj.isNull("option_type")) "" else obj.optString("option_type", "").trim()
                    val strike = if (obj.isNull("strike_price")) 0.0 else obj.optDouble("strike_price", 0.0)
                    add(
                        IndstocksInstrumentRow(
                            securityId = secId,
                            symbol = underlying,
                            tradingSymbol = tradingSymbol,
                            segment = "DERIVATIVE",
                            exchange = "NFO",
                            expiry = obj.optString("expiry", ""),
                            strikePrice = strike,
                            optionType = optType,
                            instrumentType = instrumentType.orEmpty().ifBlank { if (optType.isBlank()) "FUTIDX" else "OPTIDX" },
                            lotSize = obj.optInt("lot_size", 75).coerceAtLeast(1)
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchOptionChain(
        accessToken: String,
        spotPrice: Double,
        expiry: String,
        underlyingScrip: String = NIFTY_UNDERLYING_SCRIP,
        exchange: String = "NSE",
        segment: String = "INDEX",
        strikeCount: Int = 10,
        indiaVix: Double = 13.5
    ): Result<OptionsChainSnapshot> =
        fetchOptionChainWithSecurities(
            accessToken = accessToken,
            spotPrice = spotPrice,
            expiry = expiry,
            underlyingScrip = underlyingScrip,
            exchange = exchange,
            segment = segment,
            strikeCount = strikeCount,
            indiaVix = indiaVix
        ).map { it.snapshot }

    /**
     * GET /market/option-chain — returns OptionsChainSnapshot PLUS a lookup map of each strike's
     * CE/PE `security_id` and `trading_symbol` for instant order placement & margin preview.
     */
    suspend fun fetchOptionChainWithSecurities(
        accessToken: String,
        spotPrice: Double,
        expiry: String,
        underlyingScrip: String = NIFTY_UNDERLYING_SCRIP,
        exchange: String = "NSE",
        segment: String = "INDEX",
        strikeCount: Int = 10,
        indiaVix: Double = 13.5,
        lotSizeHint: Int = 75
    ): Result<IndstocksOptionChainResult> = withContext(Dispatchers.IO) {
        val cleanExpiry = expiry.trim()
        require(Regex("""\d{4}-\d{2}-\d{2}""").matches(cleanExpiry)) {
            "Option chain expiry must be YYYY-MM-DD (got '$cleanExpiry')"
        }
        val encodedExpiry = URLEncoder.encode(cleanExpiry, "UTF-8")
        val path = "/market/option-chain?exchange=${exchange.uppercase()}&segment=${segment.uppercase()}&underlying-scrip=$underlyingScrip&expiry=$encodedExpiry&strike_count=$strikeCount"

        executeStandardGet(accessToken, path).map { json ->
            val data = json.optJSONObject("data") ?: json
            val strikesObj = data.optJSONObject("strikes")
                ?: data.optJSONObject("option_chain")
                ?: throw IllegalStateException("Missing strikes map in option chain response")

            val effectiveSpot = data.optDoubleOr("underlying_ltp", "spot_price", default = spotPrice)
                .takeIf { it > 0.0 } ?: spotPrice
            val effectiveExpiry = data.optString("expiry", cleanExpiry).ifBlank { cleanExpiry }
            val atmStrike = ((effectiveSpot / 50.0).roundToInt() * 50.0)

            val parsedStrikes = mutableListOf<OptionStrike>()
            val legSecurities = mutableMapOf<String, IndstocksOptionLegSecurity>()
            val keys = strikesObj.keys()
            while (keys.hasNext()) {
                val strikeKey = keys.next()
                val strikePrice = strikeKey.toDoubleOrNull() ?: continue
                val row = strikesObj.optJSONObject(strikeKey) ?: continue
                val ce = row.optJSONObject("ce") ?: row.optJSONObject("CE") ?: row.optJSONObject("call") ?: JSONObject()
                val pe = row.optJSONObject("pe") ?: row.optJSONObject("PE") ?: row.optJSONObject("put") ?: JSONObject()

                val callGreeksFallback = OptionsGreeksCalculator.calculateCallGreeks(effectiveSpot, strikePrice, indiaVix + 1.0, 4.0)
                val putGreeksFallback = OptionsGreeksCalculator.calculatePutGreeks(effectiveSpot, strikePrice, indiaVix + 0.8, 4.0)

                val ceGreeksObj = ce.optJSONObject("greeks")
                val peGreeksObj = pe.optJSONObject("greeks")

                val callOi = ce.optLongOr("oi", "open_interest", default = 0L)
                val callOiChg = if (ce.has("previous_oi") && !ce.isNull("previous_oi")) {
                    callOi - ce.optLong("previous_oi", callOi)
                } else {
                    ce.optLongOr("oi_change", "chg_oi", "change_in_oi", default = 0L)
                }
                val callLtp = ce.optDoubleOr("last_price", "ltp", default = callGreeksFallback.price)
                val callPrevClose = ce.optDoubleOr("previous_close_price", "prev_close", default = callLtp)
                val callPriceChg = ce.optDoubleOr("change", default = callLtp - callPrevClose)
                val callIv = ce.optDoubleOr("iv", "implied_volatility", default = indiaVix + 1.0)

                val putOi = pe.optLongOr("oi", "open_interest", default = 0L)
                val putOiChg = if (pe.has("previous_oi") && !pe.isNull("previous_oi")) {
                    putOi - pe.optLong("previous_oi", putOi)
                } else {
                    pe.optLongOr("oi_change", "chg_oi", "change_in_oi", default = 0L)
                }
                val putLtp = pe.optDoubleOr("last_price", "ltp", default = putGreeksFallback.price)
                val putPrevClose = pe.optDoubleOr("previous_close_price", "prev_close", default = putLtp)
                val putPriceChg = pe.optDoubleOr("change", default = putLtp - putPrevClose)
                val putIv = pe.optDoubleOr("iv", "implied_volatility", default = indiaVix + 0.8)

                val ceSecId = ce.optString("security_id", "").trim()
                if (ceSecId.isNotEmpty()) {
                    legSecurities["${strikePrice.roundToInt()}_CE"] = IndstocksOptionLegSecurity(
                        strikePrice = strikePrice,
                        optionType = "CE",
                        securityId = ceSecId,
                        tradingSymbol = ce.optString("trading_symbol", "NIFTY ${strikePrice.roundToInt()} CE"),
                        lastPrice = callLtp,
                        lotSize = lotSizeHint
                    )
                }
                val peSecId = pe.optString("security_id", "").trim()
                if (peSecId.isNotEmpty()) {
                    legSecurities["${strikePrice.roundToInt()}_PE"] = IndstocksOptionLegSecurity(
                        strikePrice = strikePrice,
                        optionType = "PE",
                        securityId = peSecId,
                        tradingSymbol = pe.optString("trading_symbol", "NIFTY ${strikePrice.roundToInt()} PE"),
                        lastPrice = putLtp,
                        lotSize = lotSizeHint
                    )
                }

                parsedStrikes.add(
                    OptionStrike(
                        strikePrice = strikePrice,
                        callOi = callOi,
                        callOiChange = callOiChg,
                        callLtp = callLtp,
                        callIv = callIv,
                        callDelta = ceGreeksObj?.optDoubleOr("delta", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: ce.optDoubleOr("delta", default = callGreeksFallback.delta),
                        callGamma = ceGreeksObj?.optDoubleOr("gamma", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: ce.optDoubleOr("gamma", default = callGreeksFallback.gamma),
                        callTheta = ceGreeksObj?.optDoubleOr("theta", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: ce.optDoubleOr("theta", default = callGreeksFallback.theta),
                        callVega = ceGreeksObj?.optDoubleOr("vega", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: ce.optDoubleOr("vega", default = callGreeksFallback.vega),
                        callBuildup = classifyBuildup(callOiChg, callPriceChg),
                        putOi = putOi,
                        putOiChange = putOiChg,
                        putLtp = putLtp,
                        putIv = putIv,
                        putDelta = peGreeksObj?.optDoubleOr("delta", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: pe.optDoubleOr("delta", default = putGreeksFallback.delta),
                        putGamma = peGreeksObj?.optDoubleOr("gamma", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: pe.optDoubleOr("gamma", default = putGreeksFallback.gamma),
                        putTheta = peGreeksObj?.optDoubleOr("theta", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: pe.optDoubleOr("theta", default = putGreeksFallback.theta),
                        putVega = peGreeksObj?.optDoubleOr("vega", default = Double.NaN)?.takeIf { !it.isNaN() }
                            ?: pe.optDoubleOr("vega", default = putGreeksFallback.vega),
                        putBuildup = classifyBuildup(putOiChg, putPriceChg),
                        isAtm = abs(strikePrice - atmStrike) < 1.0
                    )
                )
            }

            val sorted = parsedStrikes.sortedBy { it.strikePrice }
            if (sorted.isEmpty()) throw IllegalStateException("No option strikes returned for $effectiveExpiry")

            val maxCallStrike = sorted.maxByOrNull { it.callOi }?.strikePrice ?: atmStrike
            val maxPutStrike = sorted.maxByOrNull { it.putOi }?.strikePrice ?: atmStrike
            val finalStrikes = sorted.map {
                it.copy(
                    isMaxCallOi = it.strikePrice == maxCallStrike,
                    isMaxPutOi = it.strikePrice == maxPutStrike
                )
            }
            val totalCallOi = finalStrikes.sumOf { it.callOi }.coerceAtLeast(1L)
            val totalPutOi = finalStrikes.sumOf { it.putOi }
            val atmObj = finalStrikes.minByOrNull { abs(it.strikePrice - atmStrike) } ?: finalStrikes.first()

            val snapshot = OptionsChainSnapshot(
                strikes = finalStrikes,
                atmStrike = atmObj.strikePrice,
                totalCallOi = totalCallOi,
                totalPutOi = totalPutOi,
                pcr = totalPutOi.toDouble() / totalCallOi.toDouble(),
                maxCallOiStrike = maxCallStrike,
                maxPutOiStrike = maxPutStrike,
                maxPainStrike = atmObj.strikePrice,
                ivPercentile = if (indiaVix > 15.0) 64.0 else 38.0,
                atmStraddlePremium = atmObj.callLtp + atmObj.putLtp,
                expectedMovePoints = OptionsGreeksCalculator.calculateExpectedMove(effectiveSpot, indiaVix, 4.0),
                expiryDate = effectiveExpiry
            )
            IndstocksOptionChainResult(snapshot = snapshot, legSecurities = legSecurities)
        }
    }

    suspend fun fetchHoldings(accessToken: String): Result<List<IndstocksHolding>> = withContext(Dispatchers.IO) {
        executeStandardGet(accessToken, "/portfolio/holdings").map { json ->
            val arr = extractListArray(json, "holdings")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val qty = obj.optInt("total_qty", obj.optInt("quantity", 0))
                    val avg = obj.optDoubleOr("avg_price", "average_price", default = 0.0)
                    val ltp = obj.optDoubleOr("last_price", "ltp", default = avg)
                    val pnl = obj.optDoubleOr("pnl", "unrealized_pnl", default = (ltp - avg) * qty)
                    val pnlPct = if (avg > 0.0 && ltp > 0.0) ((ltp - avg) / avg) * 100.0 else 0.0
                    add(
                        IndstocksHolding(
                            symbol = obj.optString("symbol", obj.optString("trading_symbol", "EQUITY")),
                            securityId = obj.optString("security_id", ""),
                            isin = obj.optString("isin", ""),
                            exchange = obj.optString("exchange", "NSE"),
                            quantity = qty,
                            usedQuantity = obj.optInt("used_qty", 0),
                            t1Quantity = obj.optInt("t1_qty", obj.optInt("t1_quantity", 0)),
                            dpQuantity = obj.optInt("dp_qty", 0),
                            avgPrice = avg,
                            lastPrice = ltp,
                            pnl = pnl,
                            pnlPct = pnlPct
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchPositions(
        accessToken: String,
        segment: String? = null,
        product: String? = null
    ): Result<List<IndstocksPosition>> = withContext(Dispatchers.IO) {
        // Per INDstocks spec: `product` MUST be paired with `segment` (otherwise 400). If both omitted, returns all open positions.
        val path = if (!segment.isNullOrBlank() && !product.isNullOrBlank()) {
            "/portfolio/positions?segment=${segment.trim().lowercase()}&product=${product.trim().lowercase()}"
        } else if (!segment.isNullOrBlank()) {
            "/portfolio/positions?segment=${segment.trim().lowercase()}"
        } else {
            "/portfolio/positions"
        }

        executeStandardGet(accessToken, path).map { json ->
            val arr = extractListArray(json, "positions")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val netQty = obj.optInt("net_qty", obj.optInt("quantity", 0))
                    val avgPrice = obj.optDoubleOr("avg_price", "buy_avg", "sell_avg", default = 0.0)
                    val buyAvg = obj.optDoubleOr("buy_avg", "buy_avg_price", default = 0.0)
                    val sellAvg = obj.optDoubleOr("sell_avg", "sell_avg_price", default = 0.0)
                    val realized = obj.optDoubleOr("realized_profit", "realized_pnl", "realised_pnl", default = 0.0)
                    val lastPrice = obj.optDoubleOr("last_price", "ltp", default = avgPrice)
                    val unrealized = obj.optDoubleOr(
                        "unrealized_pnl", "unrealised_pnl",
                        default = if (netQty != 0 && lastPrice > 0.0) (lastPrice - avgPrice) * netQty else 0.0
                    )
                    val mtm = obj.optDoubleOr("mtm", "net_pnl", default = realized + unrealized)

                    add(
                        IndstocksPosition(
                            positionId = obj.optString("position_id", ""),
                            symbol = obj.optString("symbol", obj.optString("trading_symbol", "NIFTY")),
                            securityId = obj.optString("security_id", ""),
                            segment = obj.optString("segment", "DERIVATIVE"),
                            product = obj.optString("product", "MARGIN"),
                            exchange = obj.optString("exchange", "NSE").ifBlank { "NSE" },
                            drvInstrument = obj.optString("drv_instrument", ""),
                            drvExpiryDate = obj.optString("drv_expiry_date", ""),
                            drvOptionType = obj.optString("drv_option_type", ""),
                            drvStrikePrice = obj.optDoubleOr("drv_strike_price", default = 0.0),
                            netQty = netQty,
                            avgPrice = avgPrice,
                            buyQty = obj.optInt("buy_qty", 0),
                            buyAvg = buyAvg,
                            sellQty = obj.optInt("sell_qty", 0),
                            sellAvg = sellAvg,
                            lastPrice = lastPrice,
                            realizedPnl = realized,
                            unrealizedPnl = unrealized,
                            mtm = mtm
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchOrderBook(accessToken: String): Result<List<IndstocksOrderRecord>> = withContext(Dispatchers.IO) {
        executeStandardGet(accessToken, "/order-book").map { json ->
            val arr = extractListArray(json, "orders")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(parseOrderRecord(obj))
                }
            }
        }
    }

    suspend fun fetchTradeBook(
        accessToken: String,
        segment: String = "DERIVATIVE"
    ): Result<List<IndstocksTradeRecord>> = withContext(Dispatchers.IO) {
        val segParam = URLEncoder.encode(segment.trim().uppercase(), "UTF-8")
        executeStandardGet(accessToken, "/trade-book?segment=$segParam").map { json ->
            val arr = extractListArray(json, "trades")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val fillId = obj.optString("fill_id", obj.optString("trade_id", "")).trim()
                    val scripCode = obj.optString("scrip_code", obj.optString("security_id", "")).trim()
                    add(
                        IndstocksTradeRecord(
                            tradeId = fillId,
                            exchOrderId = obj.optString("exch_order_id", ""),
                            orderId = obj.optString("order_id", obj.optString("exch_order_id", "")),
                            scripCode = scripCode,
                            symbol = obj.optString("symbol", obj.optString("trading_symbol", scripCode)),
                            txnType = obj.optString("txn_type", ""),
                            quantity = obj.optInt("quantity", obj.optInt("qty", 0)),
                            fillPrice = obj.optDoubleOr("price", "fill_price", "traded_price", default = 0.0),
                            tradeTime = obj.optString("trade_date", obj.optString("trade_time", obj.optString("created_at", ""))),
                            tradeSerialNo = obj.optString("trade_serial_no", ""),
                            remarks = obj.optString("remarks", "")
                        )
                    )
                }
            }
        }
    }

    /**
     * Queries `GET /order` with JSON body `{"order_id": ..., "segment": ...}` per INDstocks spec.
     * Gracefully maps HTTP 512 ("order not found") to `Result.success(null)`.
     */
    suspend fun fetchOrderDetails(
        accessToken: String,
        orderId: String,
        segment: String = "DERIVATIVE"
    ): Result<IndstocksOrderRecord?> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("order_id", orderId)
            put("segment", segment.uppercase())
        }.toString()
        executeGetWithJsonBody(accessToken, "/order", body, treat512AsMissing = true).map { json ->
            if (json.optBoolean("_missing512", false)) {
                null
            } else {
                val data = json.optJSONObject("data") ?: json
                parseOrderRecord(data)
            }
        }
    }

    /**
     * Queries `GET /order/trades` with JSON body `{"order_id": ..., "segment": ...}` per INDstocks spec.
     */
    suspend fun fetchOrderTrades(
        accessToken: String,
        orderId: String,
        segment: String = "DERIVATIVE"
    ): Result<List<IndstocksTradeRecord>> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("order_id", orderId)
            put("segment", segment.uppercase())
        }.toString()
        executeGetWithJsonBody(accessToken, "/order/trades", body).map { json ->
            val arr = extractListArray(json, "trades")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        IndstocksTradeRecord(
                            tradeId = obj.optString("fill_id", obj.optString("trade_id", "")),
                            exchOrderId = obj.optString("exch_order_id", ""),
                            orderId = orderId,
                            quantity = obj.optInt("quantity", obj.optInt("qty", 0)),
                            fillPrice = obj.optDoubleOr("price", "fill_price", default = 0.0),
                            tradeTime = obj.optString("trade_date", "")
                        )
                    )
                }
            }
        }
    }

    /**
     * Queries `GET /margin` with exact camelCase `MarginRequest` JSON body per OpenAPI spec:
     * `segment`, `exchange`, `securityID`, `txnType`, `quantity` (string), `price` (string), `product`.
     */
    suspend fun calculateMargin(
        accessToken: String,
        securityId: String,
        exchange: String = "NSE",
        segment: String = "DERIVATIVE",
        txnType: String = "BUY",
        product: String = "MARGIN",
        qty: Int,
        price: Double
    ): Result<IndstocksMarginPreview> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("segment", segment.uppercase())
            put("exchange", exchange.uppercase())
            put("securityID", securityId.trim())
            put("txnType", txnType.uppercase())
            put("quantity", qty.toString())
            put("price", if (price > 0.0) String.format(java.util.Locale.US, "%.2f", price) else "0")
            put("product", product.uppercase())
        }.toString()

        executeGetWithJsonBody(accessToken, "/margin", body).map { json ->
            val data = json.optJSONObject("data") ?: json
            val chargesObj = data.optJSONObject("charges")
            val breakdown = chargesObj?.let { c ->
                val brk = c.optDouble("brokerage", 10.0)
                val gst = c.optDouble("gst", 1.8)
                val totalChg = c.optDouble("total_charges", brk + gst)
                IndstocksChargesBreakdown(
                    stt = c.optDouble("stt", 0.0),
                    exchangeCharges = c.optDouble("exchange_charges", 0.0),
                    stampDuty = c.optDouble("stamp_duty", 0.0),
                    sebiTurnoverCharges = c.optDouble("sebi_turn_over_charges", 0.0),
                    brokerage = brk,
                    gst = gst,
                    ipftCharges = c.optDouble("IPFTCharges", 0.0),
                    totalCharges = totalChg
                )
            }

            val required = data.optDoubleOr("total_margin", "required_margin", "margin_required", default = qty * price)
            val available = data.optDoubleOr("available_balance", "available_margin", "available_cash", default = 0.0)
            val shortfall = data.optDoubleOr("insufficient_balance", "shortfall", default = 0.0)
            val estCharges = breakdown?.totalCharges
                ?: data.optDoubleOr("brokerage", "estimated_charges", default = 11.8).let { if (it <= 0.0) 11.8 else it }

            IndstocksMarginPreview(
                requiredMargin = required,
                availableMargin = available,
                shortfall = shortfall,
                brokerageEstimate = estCharges,
                spanMargin = data.optDoubleOr("span_margin", default = 0.0),
                exposureMargin = data.optDoubleOr("exposure_margin", default = 0.0),
                deliveryMargin = data.optDoubleOr("delivery_margin", default = 0.0),
                chargesBreakdown = breakdown,
                note = data.optString("message", "INDstocks pre-trade margin (₹10 flat brokerage)")
            )
        }
    }

    suspend fun placeOrder(
        accessToken: String,
        req: IndstocksOrderRequest
    ): Result<IndstocksOrderPlacementResult> = withContext(Dispatchers.IO) {
        val cleanExch = req.exchange.uppercase()
        val cleanOrdType = req.orderType.uppercase()
        val algoId = req.algoId.ifBlank { defaultAlgoIdForExchange(cleanExch) }
        val payload = JSONObject().apply {
            put("txn_type", req.txnType.uppercase())
            put("exchange", cleanExch)
            put("segment", req.segment.uppercase())
            put("product", req.product.uppercase())
            put("order_type", cleanOrdType)
            put("validity", req.validity.uppercase())
            put("security_id", req.securityId.trim())
            put("qty", req.qty)
            put("is_amo", req.isAmo)
            put("algo_id", algoId)
            // Spec: `limit_price` is required ONLY for LIMIT and must be omitted for MARKET orders
            if (cleanOrdType == "LIMIT" && req.price > 0.0) {
                put("limit_price", req.price)
            }
            put("remarks", sanitizeRemarks(req.remarks))
        }.toString()

        executePost(accessToken, "/order", payload).map { json ->
            val data = json.optJSONObject("data") ?: json
            val status = data.optString("order_status", data.optString("status", "INITIATED"))
            val reason = data.optString("reason", "").takeIf { it.isNotBlank() }
            if (status.equals("FAILED", ignoreCase = true) && !reason.isNullOrBlank()) {
                throw IllegalStateException("Order FAILED: $reason")
            }
            IndstocksOrderPlacementResult(
                orderId = data.optString("order_id", data.optString("id", "DRV-${System.currentTimeMillis()}")),
                status = status,
                message = json.optString("message", "Order submitted to INDstocks"),
                isSmartOrder = false,
                reason = reason
            )
        }
    }

    suspend fun modifyOrder(
        accessToken: String,
        req: IndstocksOrderModifyRequest
    ): Result<IndstocksOrderPlacementResult> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("order_id", req.orderId.trim())
            put("segment", req.segment.uppercase())
            put("qty", req.qty)
            put("limit_price", req.limitPrice)
        }.toString()

        executePost(accessToken, "/order/modify", payload).map { json ->
            val data = json.optJSONObject("data") ?: json
            IndstocksOrderPlacementResult(
                orderId = data.optString("order_id", req.orderId),
                status = data.optString("order_status", "MODIFIED"),
                message = json.optString("message", "Order modified (${req.orderId})"),
                isSmartOrder = false
            )
        }
    }

    suspend fun cancelOrder(
        accessToken: String,
        orderId: String,
        segment: String = "DERIVATIVE"
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("order_id", orderId.trim())
            put("segment", segment.uppercase())
        }.toString()
        executePost(accessToken, "/order/cancel", payload).map { json ->
            val data = json.optJSONObject("data")
            val st = data?.optString("order_status", "CANCELLED") ?: "CANCELLED"
            val reason = data?.optString("reason")?.takeIf { it.isNotBlank() }
            reason?.let { "$st: $it" } ?: json.optString("message", "Order $st ($orderId)")
        }
    }

    suspend fun placeSmartOrder(
        accessToken: String,
        req: IndstocksSmartOrderRequest
    ): Result<IndstocksOrderPlacementResult> = withContext(Dispatchers.IO) {
        val cleanExch = req.exchange.uppercase()
        val cleanOrdType = req.orderType.uppercase()
        val algoId = req.algoId.ifBlank { defaultAlgoIdForExchange(cleanExch) }
        val payload = JSONObject().apply {
            put("txn_type", req.txnType.uppercase())
            put("exchange", cleanExch)
            put("segment", req.segment.uppercase())
            put("product", req.product.uppercase())
            put("order_type", cleanOrdType)
            put("validity", req.validity.uppercase())
            put("security_id", req.securityId.trim())
            put("qty", req.qty)
            put("algo_id", algoId)
            when (cleanOrdType) {
                "LIMIT" -> if (req.price > 0.0) put("limit_price", req.price)
                "TRIGGER" -> {
                    if (req.triggerPrice > 0.0) put("trigger_price", req.triggerPrice)
                    if (req.triggerLimitPrice > 0.0) put("trigger_limit_price", req.triggerLimitPrice)
                    // Never send `limit_price` on TRIGGER order
                }
                else -> { /* MARKET omits limit_price */ }
            }
            if (req.stopLossPrice > 0.0) {
                put("sl_trigger_price", req.stopLossPrice)
                val slLim = if (req.stopLossLimitPrice > 0.0) {
                    req.stopLossLimitPrice
                } else if (req.txnType.equals("BUY", true)) {
                    max(0.5, req.stopLossPrice - 1.0)
                } else {
                    req.stopLossPrice + 1.0
                }
                put("sl_limit_price", slLim)
            }
            if (req.targetPrice > 0.0) {
                put("tgt_trigger_price", req.targetPrice)
                val tgtLim = if (req.targetLimitPrice > 0.0) {
                    req.targetLimitPrice
                } else if (req.txnType.equals("BUY", true)) {
                    max(0.5, req.targetPrice - 0.5)
                } else {
                    req.targetPrice + 0.5
                }
                put("tgt_limit_price", tgtLim)
            }
            if (req.sliceOrder && !req.segment.equals("EQUITY", true)) {
                put("slice_order", true)
            }
            put("remarks", sanitizeRemarks(req.remarks, fallback = "NithixNiftyDeskGTT"))
        }.toString()

        executePost(accessToken, "/smart/order", payload).map { json ->
            val data = json.optJSONObject("data") ?: json
            val orderDataArr = data.optJSONArray("order_data")
            val firstEntry = orderDataArr?.optJSONObject(0) ?: data
            val sliceIds = buildList {
                if (orderDataArr != null) {
                    for (i in 0 until orderDataArr.length()) {
                        val oid = orderDataArr.optJSONObject(i)?.optString("order_id", "")?.trim().orEmpty()
                        if (oid.isNotEmpty()) add(oid)
                    }
                }
            }
            val orderId = firstEntry.optString(
                "order_id",
                firstEntry.optString("smart_order_id", "GTT-${System.currentTimeMillis()}")
            )
            val status = firstEntry.optString("order_status", firstEntry.optString("status", "CREATED"))
            val reason = firstEntry.optString("reason", "").takeIf { it.isNotBlank() }
            val childDetails = firstEntry.optJSONObject("child_order_details")
            val childOrderId = childDetails?.optString("order_id")?.takeIf { it.isNotBlank() }
                ?: firstEntry.optString("child_order_id")?.takeIf { it.isNotBlank() }
            val childStatus = childDetails?.optString("order_status")?.takeIf { it.isNotBlank() }

            if (status.equals("FAILED", ignoreCase = true) && !reason.isNullOrBlank()) {
                throw IllegalStateException("Smart order FAILED: $reason")
            }

            IndstocksOrderPlacementResult(
                orderId = orderId,
                status = status,
                message = json.optString("message", "Smart GTT order armed on INDstocks"),
                isSmartOrder = true,
                childOrderId = childOrderId,
                childOrderStatus = childStatus,
                reason = reason,
                sliceOrderIds = sliceIds
            )
        }
    }

    suspend fun modifySmartOrder(
        accessToken: String,
        req: IndstocksSmartOrderModifyRequest
    ): Result<IndstocksOrderPlacementResult> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("order_id", req.orderId.trim())
            put("segment", req.segment.uppercase())
            put("algo_id", req.algoId.ifBlank { "99999" })
            req.qty?.let { put("qty", it) }
            req.limitPrice?.let { put("limit_price", it) }
            req.triggerPrice?.let { put("trigger_price", it) }
            req.triggerLimitPrice?.let { put("trigger_limit_price", it) }
            req.slTriggerPrice?.let { put("sl_trigger_price", it) }
            req.slLimitPrice?.let { put("sl_limit_price", it) }
            req.tgtTriggerPrice?.let { put("tgt_trigger_price", it) }
            req.tgtLimitPrice?.let { put("tgt_limit_price", it) }
        }.toString()

        executePost(accessToken, "/smart/order/modify", payload).map { json ->
            val data = json.optJSONObject("data") ?: json
            IndstocksOrderPlacementResult(
                orderId = data.optString("order_id", req.orderId),
                status = data.optString("order_status", "MODIFIED"),
                message = json.optString("message", "Smart order modified (${req.orderId})"),
                isSmartOrder = true
            )
        }
    }

    suspend fun cancelSmartOrder(
        accessToken: String,
        orderId: String,
        segment: String = "DERIVATIVE"
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("order_id", orderId.trim())
            put("segment", segment.uppercase())
        }.toString()
        executePost(accessToken, "/smart/order/cancel", payload).map { json ->
            val data = json.optJSONObject("data")
            val st = data?.optString("order_status", "CANCELLED") ?: "CANCELLED"
            val child = data?.optJSONObject("child_order_details")?.optString("order_id")
            val suffix = child?.takeIf { it.isNotBlank() }?.let { " + child $it" } ?: ""
            val reason = data?.optString("reason")?.takeIf { it.isNotBlank() }
            reason?.let { "$st$suffix: $it" } ?: json.optString("message", "Smart order $st ($orderId$suffix)")
        }
    }

    suspend fun fetchInstrumentsCsv(
        accessToken: String,
        source: String = "fno"
    ): Result<List<IndstocksInstrumentRow>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = sanitizeToken(accessToken)
            require(cleanToken.isNotBlank()) { "Access token required" }

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/market/instruments?source=$source")
                .header("Authorization", cleanToken)
                .header("Accept", "text/csv,application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(extractErrorMessage(response.code, parseJsonObjectSafe(raw), raw))
                }
                parseInstrumentsCsv(raw, source)
            }
        }
    }

    private fun executeStandardGet(accessToken: String, path: String): Result<JSONObject> {
        return runCatching {
            val cleanToken = sanitizeToken(accessToken)
            require(cleanToken.isNotBlank()) { "INDstocks Access Token is required" }

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
                .header("Authorization", cleanToken) // Raw token — NO Bearer prefix
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = parseJsonObjectSafe(raw)
                if (!response.isSuccessful || isFailureEnvelope(json)) {
                    throw IllegalStateException(extractErrorMessage(response.code, json, raw))
                }
                json ?: JSONObject()
            }
        }
    }

    private fun executePost(accessToken: String, path: String, jsonBody: String): Result<JSONObject> {
        return runCatching {
            val cleanToken = sanitizeToken(accessToken)
            require(cleanToken.isNotBlank()) { "INDstocks Access Token is required" }

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
                .header("Authorization", cleanToken) // Raw token — NO Bearer prefix
                .header("Accept", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = parseJsonObjectSafe(raw)
                if (!response.isSuccessful || isFailureEnvelope(json)) {
                    throw IllegalStateException(extractErrorMessage(response.code, json, raw))
                }
                json ?: JSONObject()
            }
        }
    }

    /**
     * Sends an HTTP/1.1 `GET` request carrying a JSON body (required for `/margin`, `/order`, `/order/trades`)
     * directly over a socket so OkHttp/HttpURLConnection do not block or rewrite `GET` into `POST`.
     */
    internal fun executeGetWithJsonBody(
        accessToken: String,
        path: String,
        jsonBody: String,
        treat512AsMissing: Boolean = false
    ): Result<JSONObject> {
        return runCatching {
            val cleanToken = sanitizeToken(accessToken)
            require(cleanToken.isNotBlank()) { "INDstocks Access Token is required" }

            val fullUrl = "${baseUrl.trimEnd('/')}$path".toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid base URL: $baseUrl")
            val host = fullUrl.host
            val port = fullUrl.port
            val requestPath = buildString {
                append(fullUrl.encodedPath)
                fullUrl.encodedQuery?.let { append('?').append(it) }
            }
            val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
            val hostHeader = if ((fullUrl.isHttps && port == 443) || (!fullUrl.isHttps && port == 80)) host else "$host:$port"

            val socket: Socket = if (fullUrl.isHttps) {
                val sslSocket = SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket
                sslSocket.soTimeout = 10_000
                sslSocket.sslParameters = sslSocket.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                sslSocket.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sslSocket.session)) {
                    sslSocket.close()
                    throw SSLPeerUnverifiedException("TLS hostname verification failed for $host")
                }
                sslSocket
            } else {
                Socket(host, port).apply { soTimeout = 10_000 }
            }

            socket.use { sock ->
                val reqHeader = buildString {
                    append("GET ").append(requestPath).append(" HTTP/1.1\r\n")
                    append("Host: ").append(hostHeader).append("\r\n")
                    append("Authorization: ").append(cleanToken).append("\r\n")
                    append("Accept: application/json\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                    append("Connection: close\r\n\r\n")
                }
                val out = sock.getOutputStream()
                out.write(reqHeader.toByteArray(StandardCharsets.US_ASCII))
                out.write(bodyBytes)
                out.flush()

                val (statusCode, rawBody) = readHttpResponse(sock.getInputStream())
                if (treat512AsMissing && statusCode == 512) {
                    return@runCatching JSONObject().put("_missing512", true)
                }
                val json = parseJsonObjectSafe(rawBody)
                if (statusCode !in 200..299 || isFailureEnvelope(json)) {
                    throw IllegalStateException(extractErrorMessage(statusCode, json, rawBody))
                }
                json ?: JSONObject()
            }
        }
    }

    private fun readHttpResponse(input: InputStream): Pair<Int, String> {
        val rawBytes = input.readBytes()
        val crlfSplit = indexOfBytes(rawBytes, byteArrayOf(13, 10, 13, 10))
        val lfSplit = if (crlfSplit < 0) indexOfBytes(rawBytes, byteArrayOf(10, 10)) else -1
        val splitIdx = if (crlfSplit >= 0) crlfSplit else lfSplit
        val sepLen = if (crlfSplit >= 0) 4 else 2
        if (splitIdx < 0) return 500 to String(rawBytes, StandardCharsets.UTF_8)

        val headerBlock = String(rawBytes, 0, splitIdx, StandardCharsets.US_ASCII)
        val bodyOffset = splitIdx + sepLen
        val lines = headerBlock.split(Regex("\\r?\\n"))
        val statusCode = lines.firstOrNull()
            ?.split(" ")
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 500
        val isChunked = lines.any { it.contains("transfer-encoding", ignoreCase = true) && it.contains("chunked", ignoreCase = true) }
        val decodedBody = if (isChunked) {
            decodeChunkedBytes(rawBytes, bodyOffset)
        } else {
            String(rawBytes, bodyOffset, rawBytes.size - bodyOffset, StandardCharsets.UTF_8)
        }
        return statusCode to decodedBody.trim()
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, startIndex: Int = 0): Int {
        outer@ for (i in startIndex..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun decodeChunkedBytes(rawBytes: ByteArray, startOffset: Int): String {
        val out = ByteArrayOutputStream()
        var cursor = startOffset
        while (cursor < rawBytes.size) {
            val crlfEnd = indexOfBytes(rawBytes, byteArrayOf(13, 10), cursor)
            val lfEnd = if (crlfEnd < 0) indexOfBytes(rawBytes, byteArrayOf(10), cursor) else -1
            val lineEnd = if (crlfEnd >= 0) crlfEnd else lfEnd
            if (lineEnd < 0) break
            val lineSepLen = if (crlfEnd >= 0) 2 else 1

            val sizeLine = String(rawBytes, cursor, lineEnd - cursor, StandardCharsets.US_ASCII)
            val sizeHex = sizeLine.substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: break
            if (chunkSize == 0) break

            val dataStart = lineEnd + lineSepLen
            val dataEnd = min(rawBytes.size, dataStart + chunkSize)
            if (dataEnd > dataStart) {
                out.write(rawBytes, dataStart, dataEnd - dataStart)
            }
            cursor = dataEnd
            if (cursor + 1 < rawBytes.size && rawBytes[cursor] == 13.toByte() && rawBytes[cursor + 1] == 10.toByte()) {
                cursor += 2
            } else if (cursor < rawBytes.size && rawBytes[cursor] == 10.toByte()) {
                cursor += 1
            }
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun parseOrderRecord(obj: JSONObject): IndstocksOrderRecord {
        val orderId = obj.optString("id", obj.optString("order_id", ""))
        val reqPrice = obj.optStringAsDouble("requested_price", "price", "limit_price")
        val trdPrice = obj.optStringAsDouble("traded_price", "avg_price")
        val slTrig = obj.optStringAsDouble("sl_trigger_price")
        val slLim = obj.optStringAsDouble("sl_limit_price")
        val tgtTrig = obj.optStringAsDouble("tgt_trigger_price")
        val tgtLim = obj.optStringAsDouble("tgt_limit_price")
        val childOrderId = obj.optString("child_order_id", "")
        val parentOrderId = obj.optString("parent_order_id", "")
        val ordType = obj.optString("order_type", "LIMIT")

        val smart = orderId.startsWith("GTT-", ignoreCase = true) ||
            ordType.equals("OCO", ignoreCase = true) ||
            slTrig > 0.0 ||
            tgtTrig > 0.0 ||
            childOrderId.isNotBlank() ||
            parentOrderId.isNotBlank() ||
            obj.optBoolean("is_smart_order", false)

        return IndstocksOrderRecord(
            orderId = orderId,
            exchOrderId = obj.optString("exch_order_id", ""),
            symbol = obj.optString("name", obj.optString("symbol", obj.optString("trading_symbol", "NIFTY"))),
            securityId = obj.optString("security_id", ""),
            txnType = obj.optString("txn_type", "BUY"),
            exchange = obj.optString("exchange", "NSE"),
            orderType = ordType,
            product = obj.optString("product", "MARGIN"),
            segment = obj.optString("segment", "DERIVATIVE"),
            validity = obj.optString("validity", "DAY"),
            quantity = obj.optInt("requested_qty", obj.optInt("qty", obj.optInt("quantity", 0))),
            filledQty = obj.optInt("traded_qty", obj.optInt("filled_qty", 0)),
            price = if (reqPrice > 0.0) reqPrice else trdPrice,
            tradedPrice = trdPrice,
            triggerPrice = obj.optStringAsDouble("trigger_price"),
            slTriggerPrice = slTrig,
            slLimitPrice = slLim,
            tgtTriggerPrice = tgtTrig,
            tgtLimitPrice = tgtLim,
            childOrderId = childOrderId,
            parentOrderId = parentOrderId,
            status = obj.optString("status", "UNKNOWN"),
            statusMessage = obj.optString("extra_info", obj.optString("status_message", obj.optString("reason", ""))),
            remarks = obj.optString("remarks", ""),
            isSmartOrder = smart,
            placedAt = obj.optString("created_at", obj.optString("order_timestamp", ""))
        )
    }

    internal fun parseInstrumentsCsv(csv: String, source: String = "fno"): List<IndstocksInstrumentRow> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) return emptyList()

        // Special handling for `source=index`: 3 positional columns (EXCH,SEGMENT,SECURITY_ID where col 1 holds index name!)
        if (source.equals("index", ignoreCase = true)) {
            return lines.drop(1).mapNotNull { line ->
                val cols = line.split(",")
                if (cols.size < 3) return@mapNotNull null
                val exch = cols[0].trim().ifBlank { "NSE" }
                val indexName = cols[1].trim()
                val secId = cols[2].trim()
                if (indexName.isEmpty() || secId.isEmpty()) return@mapNotNull null
                IndstocksInstrumentRow(
                    securityId = secId,
                    symbol = indexName,
                    tradingSymbol = indexName,
                    segment = "INDEX",
                    exchange = exch,
                    instrumentType = "INDEX",
                    lotSize = 1
                )
            }
        }

        val headers = lines.first().split(",").map { it.trim().lowercase() }
        fun idx(vararg names: String): Int = headers.indexOfFirst { h -> names.any { h == it } }

        val idIdx = idx("security_id", "token", "sem_smst_security_id").takeIf { it >= 0 } ?: 2
        val symIdx = idx("trading_symbol", "custom_symbol", "symbol", "symbol_name").takeIf { it >= 0 } ?: 5
        val customSymIdx = idx("custom_symbol", "symbol_name")
        val segIdx = idx("segment", "sem_segment")
        val exchIdx = idx("exch", "exchange", "sem_exm_exch_id")
        val instNameIdx = idx("instrument_name")
        val expIdx = idx("expiry_date", "expiry")
        val strikeIdx = idx("strike_price", "strike")
        val optIdx = idx("option_type", "sem_option_type")
        val lotIdx = idx("lot_units", "lot_size", "sem_lot_units")
        val freezeIdx = idx("freeze_qty")
        val tickIdx = idx("tick_size")

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            val secId = cols.getOrNull(idIdx)?.trim().orEmpty()
            val tradingSym = cols.getOrNull(symIdx)?.trim().orEmpty()
            val customSym = if (customSymIdx >= 0) cols.getOrNull(customSymIdx)?.trim().orEmpty() else tradingSym
            if (secId.isEmpty() || tradingSym.isEmpty()) return@mapNotNull null
            IndstocksInstrumentRow(
                securityId = secId,
                symbol = customSym.ifBlank { tradingSym },
                tradingSymbol = tradingSym,
                segment = cols.getOrNull(segIdx)?.trim()?.ifBlank { "FNO" } ?: "FNO",
                exchange = cols.getOrNull(exchIdx)?.trim()?.ifBlank { "NSE" } ?: "NSE",
                expiry = cols.getOrNull(expIdx)?.trim().orEmpty(),
                strikePrice = cols.getOrNull(strikeIdx)?.trim()?.toDoubleOrNull() ?: 0.0,
                optionType = cols.getOrNull(optIdx)?.trim().orEmpty(),
                instrumentType = cols.getOrNull(instNameIdx)?.trim().orEmpty(),
                lotSize = cols.getOrNull(lotIdx)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 75,
                freezeQty = cols.getOrNull(freezeIdx)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1800,
                tickSize = cols.getOrNull(tickIdx)?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.05
            )
        }
    }

    private fun extractListArray(json: JSONObject, preferredKey: String): JSONArray {
        val data = json.opt("data")
        return when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray(preferredKey)
                ?: data.optJSONArray("items")
                ?: data.optJSONArray("list")
                ?: JSONArray()
            else -> json.optJSONArray(preferredKey) ?: JSONArray()
        }
    }

    private fun parseJsonObjectSafe(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) JSONObject().put("data", JSONArray(trimmed)) else JSONObject(trimmed)
        }.getOrNull()
    }

    private fun isFailureEnvelope(json: JSONObject?): Boolean {
        if (json == null) return false
        if (json.has("success") && !json.optBoolean("success", true)) return true
        val status = json.optString("status", "").lowercase()
        return status == "error" || status == "failed"
    }

    private fun extractErrorMessage(httpCode: Int, json: JSONObject?, rawBody: String): String {
        val debugInfo = json?.optString("debug_info")?.takeIf { it.isNotBlank() }
        val message = json?.optString("message")?.takeIf { it.isNotBlank() }
        val errorType = json?.optString("error_type")?.takeIf { it.isNotBlank() }
        val errorStr = json?.optString("error")?.takeIf { it.isNotBlank() }
        val detail = listOfNotNull(message, debugInfo, errorType, errorStr)
            .distinct()
            .joinToString(" — ")
            .ifBlank { rawBody.take(180).ifBlank { "INDstocks request failed" } }

        return when (httpCode) {
            401, 403 -> "HTTP $httpCode (Token expired or unauthorized): $detail"
            429 -> "HTTP 429 (Rate limit / cooldown active): $detail"
            else -> "HTTP $httpCode: $detail"
        }
    }

    private fun classifyBuildup(oiChange: Long, priceChange: Double): BuildupType = when {
        oiChange > 0L && priceChange >= 0.0 -> BuildupType.LONG_BUILDUP
        oiChange > 0L && priceChange < 0.0 -> BuildupType.SHORT_BUILDUP
        oiChange < 0L && priceChange >= 0.0 -> BuildupType.SHORT_COVERING
        oiChange < 0L && priceChange < 0.0 -> BuildupType.LONG_UNWINDING
        else -> BuildupType.NEUTRAL
    }

    private fun normalizeToSeconds(ts: Long): Long = if (ts > 10_000_000_000L) ts / 1000L else ts

    private fun parseFormattedNumber(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        return raw.replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun JSONObject.optStringAsDouble(vararg keys: String, default: Double = 0.0): Double {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val raw = opt(k)
                when (raw) {
                    is Number -> return raw.toDouble()
                    is String -> raw.replace(",", "").trim().toDoubleOrNull()?.let { return it }
                }
            }
        }
        return default
    }

    private fun JSONObject.optDoubleOr(vararg keys: String, default: Double): Double {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val v = optDouble(k, Double.NaN)
                if (!v.isNaN()) return v
            }
        }
        return default
    }

    private fun JSONObject.optLongOr(vararg keys: String, default: Long): Long {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val v = optLong(k, Long.MIN_VALUE)
                if (v != Long.MIN_VALUE) return v
            }
        }
        return default
    }
}
