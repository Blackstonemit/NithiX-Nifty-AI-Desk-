package com.example.qa

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.MarketChartDataRepository
import com.example.data.local.InMemoryChartSettingsStore
import com.example.engine.chart.SignalRiskAndBacktestEngine
import com.example.model.Candle
import com.example.model.ChartAnchorPoint
import com.example.model.ChartDrawing
import com.example.model.DrawingToolType
import com.example.model.MarketSymbol
import com.example.model.McpPendingOAuthFlow
import com.example.model.NseWatchlistCatalog
import com.example.network.IndstocksApiClient
import com.example.network.LiveMarketDataClient
import com.example.network.McpOAuthClient
import com.example.network.McpPluginClient
import com.example.niftychart.data.local.NiftyOHLCVDao
import com.example.niftychart.data.local.NiftyOHLCVEntity
import com.example.niftychart.data.remote.MarketDataProvider
import com.example.niftychart.data.repository.NiftyMarketRepositoryImpl
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import com.example.ui.viewmodel.ChartWorkstationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * STAGE 9 & 11 — Security, Privacy, Edge-to-Edge Fuzzing, and Concurrency Race Audit
 *
 * Exercises boundary inputs, malformed URLs, OAuth CSRF state validation, backup exclusion rules,
 * HTTPS endpoint verification, UTF-8 chunked HTTP decoding, locked drawing deletion guards,
 * async symbol-switch race conditions, and real-time stream reconnection recovery.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QaSecurityAndEdgeToEdgeTest {

    @Test
    fun `QA-SEC-01 Backup exclusion rules protect SQLite chart_workstation_db alongside SharedPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allowBackup = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        if (allowBackup) {
            val backupXml = File("src/main/res/xml/backup_rules.xml").takeIf { it.exists() }?.readText().orEmpty()
            val dataExtractionXml = File("src/main/res/xml/data_extraction_rules.xml").takeIf { it.exists() }?.readText().orEmpty()
            assertTrue(
                "SEC-01: allowBackup=true requires backup_rules.xml to exclude chart_workstation.db storing plaintext LLM API keys",
                backupXml.contains("domain=\"database\"") && backupXml.contains("chart_workstation.db")
            )
            assertTrue(
                "SEC-01: allowBackup=true requires data_extraction_rules.xml to exclude chart_workstation.db",
                dataExtractionXml.contains("domain=\"database\"") && dataExtractionXml.contains("chart_workstation.db")
            )
        }
    }

    @Test
    fun `QA-SEC-02 OAuth exchangeCodeForToken rejects callback missing CSRF state parameter`() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"access_token":"hijacked_token","token_type":"Bearer"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val oauthClient = McpOAuthClient(mockClient)
        val pendingFlow = McpPendingOAuthFlow(
            connectorId = "conn_1",
            connectorName = "Evil Test",
            serverUrl = "https://mcp.example.com",
            authorizationUrl = "https://auth.example.com/authorize",
            tokenEndpoint = "https://auth.example.com/token",
            clientId = "client_1",
            redirectUri = "niftydesk://oauth/callback",
            codeVerifier = "verifier_abc",
            state = "expected_csrf_state_123",
            resourceUrl = "https://mcp.example.com"
        )

        // Attack 1: attacker strips 'state' query parameter from deep-link callback
        val resultMissingState = oauthClient.exchangeCodeForToken(
            flow = pendingFlow,
            codeOrCallbackUrl = "niftydesk://oauth/callback?code=attacker_auth_code"
        )
        assertFalse(
            "SEC-02: OAuth callback missing 'state' MUST be rejected to prevent CSRF code-injection",
            resultMissingState.isSuccess
        )
    }

    @Test
    fun `QA-SEC-03 Raw SSLSocket transport enforces HTTPS hostname verification`() {
        // Verify source does not call raw SSLSocketFactory.createSocket without endpoint identification
        val source = File("src/main/java/com/example/network/IndstocksApiClient.kt")
            .takeIf { it.exists() }?.readText().orEmpty()
        assertTrue("Expected IndstocksApiClient.kt source to exist for static TLS inspection", source.isNotBlank())
        val usesRawSocket = source.contains("SSLSocketFactory.getDefault().createSocket")
        val hasHostnameVerification = source.contains("ENDPOINT_IDENTIFICATION") ||
            source.contains("setEndpointIdentificationAlgorithm") ||
            source.contains("HostnameVerifier")
        assertTrue(
            "SEC-03: IndstocksApiClient raw SSLSocket lacks HTTPS hostname / endpoint identification verification",
            !usesRawSocket || hasHostnameVerification
        )
    }

    @Test
    fun `QA-SEC-04 Cleartext HTTP URLs to non-loopback hosts should be rejected for Bearer-authenticated MCP connectors`() {
        val pluginClient = McpPluginClient()
        // Blank / malformed URLs are already caught
        assertNotNull(pluginClient.validateUrl(""))
        assertNotNull(pluginClient.validateUrl("   "))
        assertNotNull(pluginClient.validateUrl("ftp://mcp.example.com"))
        // Remote unencrypted http:// exposes Bearer / OAuth secrets on untrusted networks
        assertNotNull(
            "SEC-05: Remote cleartext http:// URL should be rejected for Bearer-authenticated MCP endpoints",
            pluginClient.validateUrl("http://remote-broker.example.com/mcp")
        )
    }

    @Test
    fun `QA-E2E-01 IndstocksApiClient chunked HTTP decoder handles multi-byte UTF-8 Rupee symbols accurately`() {
        val chunk1 = """{"status":"success","message":"Spot ₹25,800.60","""
        val chunk2 = """"data":{"order_id":"ORD1"}}"""
        val c1Bytes = chunk1.toByteArray(StandardCharsets.UTF_8)
        val c2Bytes = chunk2.toByteArray(StandardCharsets.UTF_8)

        val serverSocket = ServerSocket(0)
        val serverThread = Thread {
            serverSocket.use { ss ->
                ss.accept().use { client ->
                    val input = client.getInputStream()
                    val buf = ByteArray(2048)
                    input.read(buf)
                    val out = client.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
                    )
                    out.write("${Integer.toHexString(c1Bytes.size)}\r\n".toByteArray(StandardCharsets.US_ASCII))
                    out.write(c1Bytes)
                    out.write("\r\n${Integer.toHexString(c2Bytes.size)}\r\n".toByteArray(StandardCharsets.US_ASCII))
                    out.write(c2Bytes)
                    out.write("\r\n0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    out.flush()
                }
            }
        }
        serverThread.start()

        val client = IndstocksApiClient(baseUrl = "http://127.0.0.1:${serverSocket.localPort}")
        val result = client.executeGetWithJsonBody(
            accessToken = "dummy_token",
            path = "/order",
            jsonBody = """{"order_id":"ORD1"}"""
        )
        serverThread.join(2000)

        assertTrue(
            "BUG-FUN-07: Chunked HTTP decoding corrupted multi-byte UTF-8 JSON (${result.exceptionOrNull()?.message})",
            result.isSuccess
        )
        assertEquals("ORD1", result.getOrNull()?.optJSONObject("data")?.optString("order_id"))
    }

    @Test
    fun `QA-E2E-02 Edge-to-Edge numeric & fuzz inputs do not crash SMA, RSI, Risk Engine, or Symbol Normalizer`() {
        val sma = CalculateSMAUseCase()
        val rsi = CalculateRSIUseCase()

        assertTrue(sma.calculateFromCloses(emptyList(), 20).all { it == null })
        assertTrue(sma.calculateFromCloses(listOf(Double.NaN, -1.0, 0.0, 1e12), 20).size == 4)
        assertEquals(0, rsi.calculateFromCloses(emptyList(), 14).values.size)
        assertNull(rsi.calculateFromCloses(listOf(100.0, 100.0, 100.0), 14).latestValue)
        assertEquals(50.0, rsi.calculateFromCloses(List(20) { 100.0 }, 14).latestValue!!, 1e-6)

        // Zero / negative / extreme inputs in Risk Calculator
        val riskZero = SignalRiskAndBacktestEngine.calculateRiskPosition(
            capital = -500.0,
            riskPct = 0.0,
            entryPrice = 0.0,
            customStopLoss = null,
            atr14 = -10.0,
            isLong = true
        )
        assertTrue(riskZero.recommendedShares >= 1)
        assertTrue(riskZero.recommendedNiftyLots >= 1)
        assertTrue(riskZero.capital >= 1000.0)

        // Backtest on < 30 candles and empty list
        val emptyBt = SignalRiskAndBacktestEngine.runBacktest(emptyList(), com.example.model.BacktestConfig())
        assertEquals(0, emptyBt.trades.size)

        // Symbol normalizer fuzz inputs (SQLi, emoji, whitespace, trailing slashes)
        val fuzzy = NseWatchlistCatalog.normalizeCustomNseSymbol("  tcs.NS ")
        assertEquals("TCS", fuzzy.symbol)
        val badChars = NseWatchlistCatalog.normalizeCustomNseSymbol("BAD/TICKER?foo=1")
        assertFalse(
            "Invalid URL delimiter '?' or '/' should be sanitized from Yahoo ticker: ${badChars.yahooTicker}",
            badChars.yahooTicker.contains("?") || badChars.yahooTicker.contains("/")
        )
    }

    @Test
    fun `QA-E2E-03 ChartWorkstationCoordinator preserves locked drawings during delete, undo, and clearAll`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = InMemoryChartSettingsStore()
            val coordinator = ChartWorkstationCoordinator(scope = scope, settingsStore = store)

            coordinator.selectDrawingTool(DrawingToolType.HORIZONTAL_LINE)
            val anchor = ChartAnchorPoint(1_740_000_000_000L, 25500.0)
            coordinator.handleChartCanvasTapForDrawing(anchor, anchor)
            delay(50)

            val created = coordinator.state.value.drawings.firstOrNull()
            assertNotNull("Expected horizontal line drawing to be created", created)
            coordinator.toggleLockDrawing(created!!)
            delay(50)

            val lockedDrawing = coordinator.state.value.drawings.first()
            assertTrue("Expected drawing to be locked", lockedDrawing.isLocked)

            // Attempt delete, undo, and clearAll on locked drawing
            coordinator.deleteDrawing(lockedDrawing.id)
            coordinator.undoLastDrawing()
            coordinator.clearAllDrawings()
            delay(50)

            assertEquals(
                "BUG-FUN-02: Locked drawing must NOT be removed by delete/undo/clearAll",
                1,
                coordinator.state.value.drawings.size
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `QA-E2E-04 ChartWorkstationCoordinator cancels stale symbol fetch on rapid symbol switching`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    val bodyJson = if (url.contains("%5ENSEI")) {
                        // Slow slow response for NIFTY 50
                        Thread.sleep(300)
                        yahooChartJson(25000.0)
                    } else {
                        // Fast response for BANKNIFTY
                        yahooChartJson(52000.0)
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bodyJson.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()

            val repo = MarketChartDataRepository(
                liveMarketClient = LiveMarketDataClient(
                    client = client,
                    yahooChartBaseUrl = "https://mock.local/chart",
                    nseIndexApiUrl = "https://mock.local/nse"
                )
            )
            val coordinator = ChartWorkstationCoordinator(
                scope = scope,
                settingsStore = InMemoryChartSettingsStore(),
                marketDataRepo = repo
            )

            // Fire slow NIFTY 50 fetch, then rapidly switch to BANKNIFTY
            coordinator.fetchActiveSymbolCandles()
            delay(30)
            coordinator.selectSymbol(NseWatchlistCatalog.defaultSymbols[1]) // BANKNIFTY

            // Wait until both slow & fast responses have settled
            delay(500)

            assertEquals("BANKNIFTY", coordinator.state.value.activeSymbol.symbol)
            val finalPrice = coordinator.state.value.candles.lastOrNull()?.close ?: 0.0
            assertTrue(
                "BUG-FUN-01: Stale slow NIFTY 50 fetch (25000) overwrote active BANKNIFTY chart! Actual price=$finalPrice",
                finalPrice > 40000.0
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `QA-E2E-05 NiftyMarketRepositoryImpl reconnects to live stream after transient failure instead of trapping in simulator`() = runBlocking {
        val fakeDao = InMemoryNiftyDao()
        val callCount = AtomicInteger(0)
        val liveProvider = object : MarketDataProvider {
            override val providerName: String = "MockLiveProvider"
            override val isSimulated: Boolean = false
            override suspend fun fetchHistoricalCandles(
                symbol: String,
                interval: ChartInterval,
                fromTimestampMs: Long?,
                toTimestampMs: Long
            ): Result<List<OHLCV>> = Result.success(emptyList())

            override fun streamMarketTicks(symbol: String): Flow<MarketTick> = flow {
                if (callCount.incrementAndGet() == 1) {
                    emit(MarketTick(symbol, 25500.0, 100L, 1_740_000_000_000L))
                    throw IOException("Simulated transient socket drop")
                } else {
                    emit(MarketTick(symbol, 26500.0, 200L, 1_740_000_060_000L))
                }
            }
        }
        val demoProvider = object : MarketDataProvider {
            override val providerName: String = "InfiniteDemo"
            override val isSimulated: Boolean = true
            override suspend fun fetchHistoricalCandles(
                symbol: String,
                interval: ChartInterval,
                fromTimestampMs: Long?,
                toTimestampMs: Long
            ) = Result.success(emptyList<OHLCV>())

            override fun streamMarketTicks(symbol: String): Flow<MarketTick> = flow {
                while (true) {
                    emit(MarketTick(symbol, 24000.0, 10L, System.currentTimeMillis()))
                    delay(100)
                }
            }
        }

        val repo = NiftyMarketRepositoryImpl(dao = fakeDao, remoteProvider = liveProvider, demoProvider = demoProvider)
        val observed = withTimeoutOrNull(4_000L) {
            repo.streamRealTimeCandles("NIFTY 50", ChartInterval.M1)
                .take(8)
                .toList()
        }.orEmpty()

        assertTrue(
            "BUG-FUN-08: After transient error, streamRealTimeCandles trapped infinitely in demoProvider and never reconnected to liveProvider (calls=${callCount.get()})",
            observed.any { (it?.close ?: 0.0) == 26500.0 }
        )
    }

    private fun yahooChartJson(basePrice: Double): String {
        val tsList = (0 until 30).joinToString(",") { (1_740_000_000L + it * 900L).toString() }
        val opens = (0 until 30).joinToString(",") { (basePrice + it).toString() }
        val highs = (0 until 30).joinToString(",") { (basePrice + it + 15.0).toString() }
        val lows = (0 until 30).joinToString(",") { (basePrice + it - 12.0).toString() }
        val closes = (0 until 30).joinToString(",") { (basePrice + it + 5.0).toString() }
        val vols = (0 until 30).joinToString(",") { "120000" }
        return """
            {
              "chart": {
                "result": [
                  {
                    "meta": { "regularMarketPrice": ${basePrice + 34.0}, "chartPreviousClose": $basePrice },
                    "timestamp": [$tsList],
                    "indicators": {
                      "quote": [{
                        "open": [$opens],
                        "high": [$highs],
                        "low": [$lows],
                        "close": [$closes],
                        "volume": [$vols]
                      }]
                    }
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private class InMemoryNiftyDao : NiftyOHLCVDao {
        private val rows = mutableListOf<NiftyOHLCVEntity>()
        private var nextId = 1L

        override suspend fun insertCandle(candle: NiftyOHLCVEntity): Long {
            val dup = rows.any { it.symbol == candle.symbol && it.interval == candle.interval && it.timestamp == candle.timestamp }
            if (dup) return -1L
            val saved = candle.copy(id = nextId++)
            rows.add(saved)
            return saved.id
        }

        override suspend fun insertCandlesBatch(candles: List<NiftyOHLCVEntity>): List<Long> =
            candles.map { insertCandle(it) }

        override suspend fun updateCandle(candle: NiftyOHLCVEntity): Int {
            val idx = rows.indexOfFirst { it.id == candle.id }
            if (idx < 0) return 0
            rows[idx] = candle
            return 1
        }

        override suspend fun candleExists(symbol: String, interval: String, timestamp: Long): Boolean =
            rows.any { it.symbol == symbol && it.interval == interval && it.timestamp == timestamp }

        override suspend fun getCandleAt(symbol: String, interval: String, timestamp: Long): NiftyOHLCVEntity? =
            rows.firstOrNull { it.symbol == symbol && it.interval == interval && it.timestamp == timestamp }

        override suspend fun getLatestPrice(symbol: String): NiftyOHLCVEntity? =
            rows.filter { it.symbol == symbol }.maxByOrNull { it.timestamp }

        override fun observeLatestPrice(symbol: String): Flow<NiftyOHLCVEntity?> = flowOf(rows.lastOrNull())
        override fun observeCandlesInRange(symbol: String, interval: String, startTimestamp: Long, endTimestamp: Long): Flow<List<NiftyOHLCVEntity>> = flowOf(emptyList())
        override suspend fun getCandlesInRange(symbol: String, interval: String, startTimestamp: Long, endTimestamp: Long): List<NiftyOHLCVEntity> = emptyList()
        override fun observeCandlesByInterval(symbol: String, interval: String): Flow<List<NiftyOHLCVEntity>> = flowOf(emptyList())
        override fun observeLatestNCandles(symbol: String, interval: String, limit: Int): Flow<List<NiftyOHLCVEntity>> = flowOf(emptyList())
        override suspend fun getLatestNCandles(symbol: String, interval: String, limit: Int): List<NiftyOHLCVEntity> =
            rows.filter { it.symbol == symbol && it.interval == interval }.sortedBy { it.timestamp }.takeLast(limit)

        override suspend fun getLatestTimestamp(symbol: String, interval: String): Long? =
            rows.filter { it.symbol == symbol && it.interval == interval }.maxOfOrNull { it.timestamp }

        override suspend fun getCandleCount(symbol: String, interval: String): Int =
            rows.count { it.symbol == symbol && it.interval == interval }

        override suspend fun deleteOutdatedRecords(cutoffTimestamp: Long): Int = 0
        override suspend fun deleteOutdatedRecordsForInterval(symbol: String, interval: String, cutoffTimestamp: Long): Int = 0
    }
}
