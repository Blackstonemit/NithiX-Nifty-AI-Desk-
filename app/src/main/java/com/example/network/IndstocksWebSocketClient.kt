package com.example.network

import com.example.model.IndstocksOrderUpdateEvent
import com.example.model.IndstocksPriceTick
import com.example.model.IndstocksWebSocketStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket streaming client for INDstocks Price Feed & Order Updates Feed.
 *
 * Official WebSocket endpoints:
 * - Price Feed: `wss://ws-prices.indstocks.com/api/v1/ws/prices`
 * - Order Updates: `wss://ws-order-updates.indstocks.com/api/v1/ws/trades`
 *
 * Protocol guarantees:
 * - Auth header uses RAW token (`Authorization: <access_token>`), no "Bearer " prefix.
 * - Price instruments use colon-separated format `SEGMENT:TOKEN` (e.g. `NIDX:26000`, `NFO:51011`).
 * - Order update frames arrive as double-encoded JSON strings (`"{\"mode\":\"order_update\",...}"`) or JSON objects.
 */
class IndstocksWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build(),
    private val pricesWsUrl: String = DEFAULT_PRICES_WS_URL,
    private val orderUpdatesWsUrl: String = DEFAULT_ORDERS_WS_URL
) {

    companion object {
        const val DEFAULT_PRICES_WS_URL = "wss://ws-prices.indstocks.com/api/v1/ws/prices"
        const val DEFAULT_ORDERS_WS_URL = "wss://ws-order-updates.indstocks.com/api/v1/ws/trades"

        fun mapOrderStatusCode(code: String): String = when (code.trim().uppercase()) {
            "R" -> "RECEIVED"
            "P" -> "PENDING"
            "S" -> "SUCCESS"
            "F" -> "FAILED"
            "C" -> "CANCELLED"
            "RJ" -> "REJECTED"
            "PF" -> "PARTIALLY FILLED"
            "PFC" -> "PARTIALLY FILLED - CANCELLED"
            else -> code.trim().uppercase().ifBlank { "UPDATED" }
        }

        internal fun unwrapJsonPayload(rawText: String): JSONObject? {
            val trimmed = rawText.trim()
            if (trimmed.isBlank()) return null
            return runCatching {
                when (val first = JSONTokener(trimmed).nextValue()) {
                    is JSONObject -> first
                    is String -> {
                        val inner = first.trim()
                        if (inner.startsWith("{")) JSONObject(inner) else null
                    }
                    else -> null
                }
            }.getOrNull()
        }

        internal fun parsePriceFrame(rawText: String): IndstocksPriceTick? {
            val obj = unwrapJsonPayload(rawText) ?: return null
            val mode = obj.optString("mode", "").lowercase()
            if (mode.isNotBlank() && mode != "ltp" && mode != "quote") return null

            val data = obj.optJSONObject("data") ?: obj
            val ltp = when {
                data.has("ltp") && !data.isNull("ltp") -> data.optDouble("ltp", Double.NaN)
                data.has("live_price") && !data.isNull("live_price") -> data.optDouble("live_price", Double.NaN)
                data.has("last_price") && !data.isNull("last_price") -> data.optDouble("last_price", Double.NaN)
                else -> Double.NaN
            }
            if (ltp.isNaN() || ltp <= 0.0) return null

            return IndstocksPriceTick(
                mode = mode.ifBlank { "ltp" },
                instrument = obj.optString("instrument", data.optString("instrument", "")),
                ltp = ltp,
                timestampMs = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }

        internal fun parseOrderUpdateFrame(rawText: String): IndstocksOrderUpdateEvent? {
            val obj = unwrapJsonPayload(rawText) ?: return null
            val mode = obj.optString("mode", "").lowercase()
            if (mode != "order_update") return null

            val data = obj.optJSONObject("data") ?: return null
            val orderId = data.optString("order_id", "").trim()
            if (orderId.isBlank()) return null

            val code = data.optString("order_status", "")
            val execPrice = if (data.has("executed_price") && !data.isNull("executed_price")) {
                data.optDouble("executed_price", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
            } else null
            val filledLot = if (data.has("lot") && !data.isNull("lot")) data.optInt("lot") else null

            return IndstocksOrderUpdateEvent(
                orderId = orderId,
                entityName = data.optString("entity_name", ""),
                orderType = data.optString("order_type", ""),
                orderStatusCode = code,
                statusLabel = mapOrderStatusCode(code),
                executedPrice = execPrice,
                reqQuantity = data.optInt("req_quantity", 0),
                requestedLot = data.optInt("requested_lot", 0),
                filledLot = filledLot,
                errorMessage = data.optString("error_message", "").trim(),
                timestampMs = obj.optLong("timestamp", data.optLong("timestamp", System.currentTimeMillis()))
            )
        }
    }

    private val _priceStatus = MutableStateFlow(IndstocksWebSocketStatus.DISCONNECTED)
    val priceStatus: StateFlow<IndstocksWebSocketStatus> = _priceStatus.asStateFlow()

    private val _orderStatus = MutableStateFlow(IndstocksWebSocketStatus.DISCONNECTED)
    val orderStatus: StateFlow<IndstocksWebSocketStatus> = _orderStatus.asStateFlow()

    private val _priceTicks = MutableSharedFlow<IndstocksPriceTick>(extraBufferCapacity = 64)
    val priceTicks: SharedFlow<IndstocksPriceTick> = _priceTicks.asSharedFlow()

    private val _orderUpdates = MutableSharedFlow<IndstocksOrderUpdateEvent>(extraBufferCapacity = 32)
    val orderUpdates: SharedFlow<IndstocksOrderUpdateEvent> = _orderUpdates.asSharedFlow()

    private val wsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manuallyDisconnected = AtomicBoolean(true)
    private val priceRetryAttempt = AtomicInteger(0)
    private val orderRetryAttempt = AtomicInteger(0)
    private var priceReconnectJob: Job? = null
    private var orderReconnectJob: Job? = null
    private var activeToken: String = ""
    private var priceSocket: WebSocket? = null
    private var orderSocket: WebSocket? = null
    private var subscribedInstruments: List<String> = listOf("NIDX:26000")

    fun connect(
        accessToken: String,
        instruments: List<String> = listOf("NIDX:26000")
    ) {
        val cleanToken = IndstocksApiClient.sanitizeToken(accessToken)
        if (cleanToken.isBlank()) return
        manuallyDisconnected.set(false)
        activeToken = cleanToken
        priceRetryAttempt.set(0)
        orderRetryAttempt.set(0)
        priceReconnectJob?.cancel()
        orderReconnectJob?.cancel()
        subscribedInstruments = instruments.ifEmpty { listOf("NIDX:26000") }

        connectPriceFeed(cleanToken, subscribedInstruments)
        connectOrderUpdatesFeed(cleanToken)
    }

    fun updatePriceSubscriptions(instruments: List<String>, mode: String = "quote") {
        val clean = instruments.distinct().take(3000)
        if (clean.isEmpty()) return
        subscribedInstruments = clean
        val msg = JSONObject().apply {
            put("action", "subscribe")
            put("mode", mode)
            put("instruments", JSONArray(clean))
        }.toString()
        priceSocket?.send(msg)
    }

    private fun connectPriceFeed(cleanToken: String, instruments: List<String>) {
        priceSocket?.close(1000, "Reconnecting")
        priceSocket = null
        _priceStatus.value = IndstocksWebSocketStatus.CONNECTING

        val req = Request.Builder()
            .url(pricesWsUrl)
            .header("Authorization", cleanToken)
            .build()

        priceSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                priceRetryAttempt.set(0)
                _priceStatus.value = IndstocksWebSocketStatus.STREAMING
                val sub = JSONObject().apply {
                    put("action", "subscribe")
                    put("mode", "quote")
                    put("instruments", JSONArray(instruments))
                }.toString()
                webSocket.send(sub)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parsePriceFrame(text)?.let { tick ->
                    _priceTicks.tryEmit(tick)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _priceStatus.value = IndstocksWebSocketStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _priceStatus.value = IndstocksWebSocketStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _priceStatus.value = IndstocksWebSocketStatus.ERROR
                schedulePriceReconnect()
            }
        })
    }

    private fun schedulePriceReconnect() {
        if (manuallyDisconnected.get() || activeToken.isBlank()) return
        val attempt = priceRetryAttempt.incrementAndGet()
        if (attempt > 5) return
        val backoffMs = (1_500L shl (attempt - 1).coerceAtMost(4)).coerceAtMost(24_000L)
        priceReconnectJob?.cancel()
        priceReconnectJob = wsScope.launch {
            delay(backoffMs)
            if (!manuallyDisconnected.get() && activeToken.isNotBlank()) {
                connectPriceFeed(activeToken, subscribedInstruments)
            }
        }
    }

    private fun connectOrderUpdatesFeed(cleanToken: String) {
        orderSocket?.close(1000, "Reconnecting")
        orderSocket = null
        _orderStatus.value = IndstocksWebSocketStatus.CONNECTING

        val req = Request.Builder()
            .url(orderUpdatesWsUrl)
            .header("Authorization", cleanToken)
            .build()

        orderSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                orderRetryAttempt.set(0)
                _orderStatus.value = IndstocksWebSocketStatus.STREAMING
                val sub = JSONObject().apply {
                    put("action", "subscribe")
                    put("mode", "order_update")
                }.toString()
                webSocket.send(sub)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseOrderUpdateFrame(text)?.let { evt ->
                    _orderUpdates.tryEmit(evt)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _orderStatus.value = IndstocksWebSocketStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _orderStatus.value = IndstocksWebSocketStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _orderStatus.value = IndstocksWebSocketStatus.ERROR
                scheduleOrderReconnect()
            }
        })
    }

    private fun scheduleOrderReconnect() {
        if (manuallyDisconnected.get() || activeToken.isBlank()) return
        val attempt = orderRetryAttempt.incrementAndGet()
        if (attempt > 5) return
        val backoffMs = (1_500L shl (attempt - 1).coerceAtMost(4)).coerceAtMost(24_000L)
        orderReconnectJob?.cancel()
        orderReconnectJob = wsScope.launch {
            delay(backoffMs)
            if (!manuallyDisconnected.get() && activeToken.isNotBlank()) {
                connectOrderUpdatesFeed(activeToken)
            }
        }
    }

    fun disconnect() {
        manuallyDisconnected.set(true)
        activeToken = ""
        priceReconnectJob?.cancel()
        orderReconnectJob?.cancel()
        runCatching { priceSocket?.close(1000, "Disconnected by user") }
        runCatching { orderSocket?.close(1000, "Disconnected by user") }
        priceSocket = null
        orderSocket = null
        _priceStatus.value = IndstocksWebSocketStatus.DISCONNECTED
        _orderStatus.value = IndstocksWebSocketStatus.DISCONNECTED
    }
}
