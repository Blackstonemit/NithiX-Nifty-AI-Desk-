package com.example.network

import com.example.model.McpRpcLog
import com.example.model.McpToolDefinition
import com.example.model.NseBhavcopyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NseMcpQueryResult(
    val record: NseBhavcopyRecord,
    val logs: List<McpRpcLog>,
    val isLiveNetworkSuccess: Boolean,
    val statusMessage: String,
    val latencyMs: Long
)

/**
 * Official Model Context Protocol (MCP) client for National Stock Exchange of India (NSE).
 * Documentation: https://www.nseindia.com/nse-mcp
 * Endpoints:
 *  - nse-bhavcopy: https://mcp.nseindia.in/bhavcopy/cm/mcp
 *  - cm-market:    https://mcp.nseindia.in/cmmkt/mcp
 */
class NseMcpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
    val bhavcopyEndpoint: String = "https://mcp.nseindia.in/bhavcopy/cm/mcp",
    val cmMarketEndpoint: String = "https://mcp.nseindia.in/cmmkt/mcp"
) {

    val defaultTools = listOf(
        McpToolDefinition(
            name = "get_bhavcopy_by_symbol",
            description = "Fetches official end-of-day Capital Market Bhavcopy settlement for a given symbol or index.",
            parametersDescription = "symbol (string, e.g. 'NIFTY 50' or 'RELIANCE'), series (optional, default 'EQ')"
        ),
        McpToolDefinition(
            name = "get_daily_bhavcopy",
            description = "Retrieves complete market-wide Bhavcopy summary for specified trade date.",
            parametersDescription = "date (string, format YYYY-MM-DD)"
        ),
        McpToolDefinition(
            name = "get_historical_prices",
            description = "Provides up to 5 years of historical OHLCV data from official NSE archives.",
            parametersDescription = "symbol (string), from_date (string), to_date (string)"
        ),
        McpToolDefinition(
            name = "get_index_constituents",
            description = "Returns real-time weightage and closing prices of all 50 NIFTY 50 constituent stocks.",
            parametersDescription = "index_name (string, e.g. 'NIFTY 50')"
        ),
        McpToolDefinition(
            name = "get_market_breadth",
            description = "Returns advance/decline ratio, 52-week highs/lows, and upper/lower circuit counts.",
            parametersDescription = "segment (string, e.g. 'CM')"
        )
    )

    suspend fun queryBhavcopy(symbol: String = "NIFTY 50"): NseMcpQueryResult = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString().take(8)
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<McpRpcLog>()

        val requestJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "get_bhavcopy_by_symbol")
                put("arguments", JSONObject().apply {
                    put("symbol", symbol)
                })
            })
        }.toString(2)

        logs.add(
            McpRpcLog(
                id = "REQ-$requestId",
                timestamp = startTime,
                direction = "OUTGOING_REQUEST",
                method = "tools/call (get_bhavcopy_by_symbol)",
                payloadJson = requestJson,
                isSuccess = true
            )
        )

        var parsedRecord: NseBhavcopyRecord? = null
        var isLiveSuccess = false
        var statusMsg: String
        var totalLatency = 0L

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(bhavcopyEndpoint)
                .addHeader("User-Agent", "NithixNiftyDesk-MCP-Client/1.0 (Android)")
                .addHeader("Accept", "application/json, text/event-stream")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string().orEmpty()
                totalLatency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

                if (response.isSuccessful && responseBodyStr.isNotBlank()) {
                    parsedRecord = parseJsonRpcBhavcopy(responseBodyStr, symbol)
                    isLiveSuccess = parsedRecord != null
                    statusMsg = if (isLiveSuccess) {
                        "Live NSE MCP sync successful (${totalLatency}ms)"
                    } else {
                        "NSE MCP returned unparseable payload; using cached reference"
                    }
                    logs.add(
                        McpRpcLog(
                            id = "RES-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "tools/call:response (HTTP ${response.code}, ${totalLatency}ms)",
                            payloadJson = responseBodyStr,
                            isSuccess = isLiveSuccess
                        )
                    )
                } else {
                    statusMsg = "NSE MCP HTTP ${response.code}; using offline reference"
                    logs.add(
                        McpRpcLog(
                            id = "ERR-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "tools/call:error (HTTP ${response.code}, ${totalLatency}ms)",
                            payloadJson = responseBodyStr.ifBlank { "{\"error\": \"HTTP ${response.code}\"}" },
                            isSuccess = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            totalLatency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            statusMsg = "Offline / Timeout (${e.javaClass.simpleName}): using cached reference"
            val errorJson = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("error", JSONObject().apply {
                    put("code", -32000)
                    put("message", e.message ?: "Network error connecting to NSE MCP")
                    put("fallbackApplied", true)
                })
            }.toString(2)

            logs.add(
                McpRpcLog(
                    id = "ERR-$requestId",
                    timestamp = System.currentTimeMillis(),
                    direction = "INCOMING_RESPONSE",
                    method = "tools/call:offline (${e.javaClass.simpleName} - ${totalLatency}ms)",
                    payloadJson = errorJson,
                    isSuccess = false
                )
            )
        }

        val finalRecord = parsedRecord ?: createCachedFallbackRecord(symbol)
        NseMcpQueryResult(
            record = finalRecord,
            logs = logs,
            isLiveNetworkSuccess = isLiveSuccess,
            statusMessage = statusMsg,
            latencyMs = totalLatency
        )
    }

    internal fun parseJsonRpcBhavcopy(responseBodyStr: String, fallbackSymbol: String): NseBhavcopyRecord? {
        return try {
            val cleaned = if (responseBodyStr.contains("data:")) {
                responseBodyStr.lines()
                    .firstOrNull { it.trimStart().startsWith("data:") }
                    ?.substringAfter("data:")
                    ?.trim() ?: responseBodyStr
            } else {
                responseBodyStr
            }

            val json = JSONObject(cleaned)
            val result = json.optJSONObject("result") ?: return null
            val close = result.optDouble("close", Double.NaN)
            if (close.isNaN() || close <= 0.0) return null

            NseBhavcopyRecord(
                symbol = result.optString("symbol", fallbackSymbol),
                series = result.optString("series", "INDEX"),
                open = result.optDouble("open", close),
                high = result.optDouble("high", close),
                low = result.optDouble("low", close),
                close = close,
                last = result.optDouble("last", close),
                prevClose = result.optDouble("prevClose", close),
                totalTradedQty = result.optLong("totalTradedQty", 0L),
                totalTradedVal = result.optDouble("totalTradedVal", 0.0),
                tradeDate = result.optString("tradeDate", getTodayIstDate()),
                deliveryQty = result.optLong("deliveryQty", 0L),
                deliveryPct = result.optDouble("deliveryPct", 0.0)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun createCachedFallbackRecord(symbol: String): NseBhavcopyRecord = NseBhavcopyRecord(
        symbol = symbol,
        series = "INDEX",
        open = 25139.25,
        high = 25242.00,
        low = 25068.50,
        close = 25144.30,
        last = 25144.30,
        prevClose = 25139.25,
        totalTradedQty = 318400000L,
        totalTradedVal = 35420000000.0,
        tradeDate = "Cached Session Reference",
        deliveryQty = 214500000L,
        deliveryPct = 67.37
    )

    private fun getTodayIstDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(Date())
    }
}
