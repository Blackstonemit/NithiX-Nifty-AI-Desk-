package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.DeskState
import com.example.data.NiftyMarketRepository
import com.example.data.SharedPreferencesMcpConnectorStore
import com.example.model.MarketBias
import com.example.model.McpAuthType
import com.example.model.McpConnectionStatus
import com.example.model.McpConnectorConfig
import com.example.model.McpTransportType
import com.example.model.SignalType
import com.example.model.Timeframe
import com.example.network.LiveMarketDataClient
import com.example.network.McpPluginClient
import com.example.network.NseMcpClient
import com.example.ui.components.CandlestickChart
import com.example.ui.components.HeaderTicker
import com.example.ui.components.NseMcpDeskDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExecutionDeskScreen
import com.example.ui.screens.FuturesDeskScreen
import com.example.ui.screens.ReportAndAuditScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NiftyDeskViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fastOfflineClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(250, TimeUnit.MILLISECONDS)
        .readTimeout(250, TimeUnit.MILLISECONDS)
        .build()

    private fun createOfflineRepository(): NiftyMarketRepository {
        val fastClient = fastOfflineClient()
        return NiftyMarketRepository(
            liveMarketClient = LiveMarketDataClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
            nseMcpClient = NseMcpClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1")
        )
    }

    @Test
    fun testRealTimeBaselineAndTickSimulation() {
        val repository = createOfflineRepository()
        val initialSpot = repository.deskState.value.spot.spotPrice
        assertEquals(25144.30, initialSpot, 0.01)

        repeat(5) {
            repository.simulateMarketTick()
        }

        val finalRec = repository.deskState.value.finalRecommendation
        assertNotNull(finalRec)
        assertTrue("Latency should be sub-50ms", finalRec.latencyMs <= 50L)
    }

    @Test
    fun testScenarioTransitionsAndAgentConsensus() {
        val repository = createOfflineRepository()

        repository.switchScenario("Bearish Breakdown (PE Rally)")
        var state = repository.deskState.value
        assertEquals(MarketBias.BEARISH, state.technicals.trendDirection)
        assertTrue(
            state.finalRecommendation.signal == SignalType.BUY_PE ||
                state.finalRecommendation.signal == SignalType.BEARISH_FUTURES
        )
        assertTrue("PCR should be bearish (< 0.8)", state.optionsChain.pcr < 0.8)

        repository.switchScenario("Conflicting Signals (No-Trade Trap)")
        state = repository.deskState.value
        assertEquals(SignalType.NO_TRADE, state.finalRecommendation.signal)
        assertTrue(state.finalRecommendation.confidenceScore < 60)

        repository.switchScenario("Bullish Breakout (CE Expansion)")
        state = repository.deskState.value
        assertTrue(
            state.finalRecommendation.signal == SignalType.BUY_CE ||
                state.finalRecommendation.signal == SignalType.BULLISH_FUTURES
        )
        assertTrue(state.finalRecommendation.confidenceScore >= 70)
    }

    @Test
    fun testCandlestickAndSynchronizedVolumeBarChartHorizontalDragShowsOhlcPopup() {
        val repository = createOfflineRepository()
        val state = repository.deskState.value
        var selectedTf = state.selectedTimeframe

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    CandlestickChart(
                        candles = state.candles,
                        vwapValue = state.technicals.vwap,
                        emaValue = state.technicals.ema20,
                        selectedTimeframe = selectedTf,
                        onSelectTimeframe = { selectedTf = it },
                        isStreaming = false,
                        isMarketOpen = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("candlestick_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("candlestick_price_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("volume_bar_chart").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ohlc_popup_overlay").assertDoesNotExist()

        // Horizontal drag across the candlestick price canvas reveals OHLC popup overlay
        composeTestRule.onNodeWithTag("candlestick_price_canvas").performScrollTo().performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveBy(Offset(width * 0.35f, 0f))
            up()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ohlc_popup_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open").assertIsDisplayed()
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
        composeTestRule.onNodeWithText("Low").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()

        // Horizontal drag on the synchronized secondary Volume Bar Chart also scrubs candles (back left < 50%)
        composeTestRule.onNodeWithTag("volume_bar_chart").performScrollTo().performTouchInput {
            down(Offset(width * 0.65f, height * 0.5f))
            moveBy(Offset(-width * 0.45f, 0f))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ohlc_popup_overlay").performScrollTo().assertIsDisplayed()

        // Timeframe selection updates callback
        composeTestRule.onNodeWithTag("chart_tf_1D").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(Timeframe.DAILY, selectedTf)
    }

    @Test
    fun testHeaderTickerControlsFitWithoutClippingOnCompactPhone() {
        val repository = createOfflineRepository()
        val state = repository.deskState.value
        var refreshed = false
        var mcpOpened = false

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                    HeaderTicker(
                        spot = state.spot,
                        futures = state.futures,
                        isStreaming = false,
                        selectedScenario = DeskState.SCENARIO_LIVE_FEED,
                        sessionStatus = state.sessionStatus,
                        onToggleStreaming = {},
                        onSelectScenario = {},
                        latencyMs = 18L,
                        onOpenMcpDesk = { mcpOpened = true },
                        isLiveMarketData = false,
                        isRefreshing = false,
                        lastSyncLabel = "Offline fallback",
                        onRefreshLive = { refreshed = true }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_mcp_badge_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("refresh_live_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("toggle_stream_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scenario_picker_button").assertIsDisplayed()

        assertTrue(mcpOpened)
        assertTrue(refreshed)
    }

    @Test
    fun testExecutionDeskScreenRendersLiveOrdersAndCloseAction() {
        val repository = createOfflineRepository()
        val rec = repository.deskState.value.finalRecommendation
        repository.executeAuthorizedTrade(rec, lots = 3)
        val liveOrder = repository.deskState.value.activeOrders.first()

        composeTestRule.setContent {
            MyApplicationTheme {
                ExecutionDeskScreen(
                    state = repository.deskState.value,
                    onCloseOrder = { repository.closeOrder(it) }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("execution_desk_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execution_position_${liveOrder.id}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_order_${liveOrder.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        val closedOrder = repository.deskState.value.activeOrders.first { it.id == liveOrder.id }
        assertEquals("CLOSED", closedOrder.status)
    }

    @Test
    fun testReportAndAuditScreenMatchesSummedOptionOiChanges() {
        val repository = createOfflineRepository()
        val state = repository.deskState.value
        val expectedCallSum = state.optionsChain.strikes.sumOf { it.callOiChange }
        val expectedPutSum = state.optionsChain.strikes.sumOf { it.putOiChange }
        val callFormatted = String.format(Locale.US, "%,d", expectedCallSum)
        val putFormatted = String.format(Locale.US, "%,d", expectedPutSum)

        composeTestRule.setContent {
            MyApplicationTheme {
                ReportAndAuditScreen(state = state)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("report_and_audit_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText(callFormatted, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(putFormatted, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun testNseMcpDialogShowsOfflineStatusAndDynamicTradedValue() {
        runBlocking {
            val repository = createOfflineRepository()
            repository.syncNseBhavcopy("NIFTY 50")

            composeTestRule.setContent {
                MyApplicationTheme {
                    NseMcpDeskDialog(
                        mcpState = repository.nseMcpState.value,
                        onSyncNow = {},
                        onDismiss = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("nse_mcp_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("CACHED / OFFLINE", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("₹3,542 Cr", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun testFuturesDeskScreenDisplaysCurrentContractsAndInstitutionalFlows() {
        val repository = createOfflineRepository()
        val state = repository.deskState.value

        composeTestRule.setContent {
            MyApplicationTheme {
                FuturesDeskScreen(
                    futures = state.futures,
                    spotPrice = state.spot.spotPrice
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("futures_desk_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("NIFTY FUTURES CONTRACTS (NSE)").assertIsDisplayed()
    }

    @Test
    fun testLiveMarketDataClientWithLocalHttpServer() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port

            val yahooNiftyJson = """
                {
                  "chart": {
                    "result": [{
                      "meta": {
                        "regularMarketPrice": 25144.30,
                        "chartPreviousClose": 25139.25,
                        "regularMarketDayHigh": 25242.0,
                        "regularMarketDayLow": 25068.5,
                        "regularMarketTime": 1763100000
                      },
                      "timestamp": [1763098200, 1763099100, 1763100000],
                      "indicators": {
                        "quote": [{
                          "open": [25090.0, 25115.0, 25132.0],
                          "high": [25128.0, 25150.0, 25165.0],
                          "low": [25080.0, 25105.0, 25120.0],
                          "close": [25115.0, 25132.0, 25144.30],
                          "volume": [45000, 52000, 61000]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
            """.trimIndent()

            val yahooVixJson = """
                {
                  "chart": {
                    "result": [{
                      "meta": {
                        "regularMarketPrice": 13.45,
                        "chartPreviousClose": 13.80
                      }
                    }],
                    "error": null
                  }
                }
            """.trimIndent()

            val nseIndexJson = """
                {
                  "data": [
                    {"symbol": "NIFTY 50", "lastPrice": 25144.30, "change": 5.05, "pChange": 0.02, "yearHigh": 26277.35, "yearLow": 21281.45},
                    {"symbol": "HDFCBANK", "lastPrice": 1742.30, "change": 12.4, "pChange": 0.72, "meta": {"companyName": "HDFC Bank Ltd"}}
                  ],
                  "advance": {"advances": "29", "declines": "21", "unchanged": "0"},
                  "metadata": {"pe": 21.65, "pb": 3.72, "divYield": 1.24}
                }
            """.trimIndent()

            // URI.getPath() in HttpServer decodes %5E -> ^
            server.createContext("/chart/^NSEI") { ex ->
                val bytes = yahooNiftyJson.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.createContext("/chart/^INDIAVIX") { ex ->
                val bytes = yahooVixJson.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.createContext("/nse-index") { ex ->
                val bytes = nseIndexJson.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()

            try {
                val client = LiveMarketDataClient(
                    client = fastOfflineClient(),
                    yahooChartBaseUrl = "http://127.0.0.1:$port/chart",
                    nseIndexApiUrl = "http://127.0.0.1:$port/nse-index"
                )
                val result = client.fetchLiveMarketSnapshot(Timeframe.M15)
                assertTrue("Live HTTP snapshot should succeed: ${result.statusMessage}", result.isLive)
                assertEquals(25144.30, result.indexQuote!!.spotPrice, 0.01)
                assertEquals(3, result.indexQuote!!.candles.size)
                assertEquals(13.45, result.vixQuote!!.vixValue, 0.01)
                assertEquals(29, result.indexTracker!!.advances)

                val failClient = LiveMarketDataClient(
                    client = fastOfflineClient(),
                    yahooChartBaseUrl = "http://127.0.0.1:$port/missing",
                    nseIndexApiUrl = "http://127.0.0.1:$port/missing"
                )
                val failResult = failClient.fetchLiveMarketSnapshot(Timeframe.M15)
                assertFalse("Failed endpoints must mark isLive=false", failResult.isLive)
                assertTrue(failResult.statusMessage.contains("Offline/Cached"))
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testNseMcpClientJsonRpcParsingAndErrorHandling() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port

            val ssePayload = """
                event: message
                data: {"jsonrpc":"2.0","id":"1","result":{"symbol":"NIFTY 50","series":"INDEX","open":25100.0,"high":25210.0,"low":25050.0,"close":25144.30,"last":25144.30,"prevClose":25139.25,"totalTradedQty":320000000,"totalTradedVal":35120000000.0,"tradeDate":"2026-11-14","deliveryQty":210000000,"deliveryPct":65.62}}
            """.trimIndent()

            server.createContext("/mcp-ok") { ex ->
                val bytes = ssePayload.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.createContext("/mcp-err") { ex ->
                val bytes = """{"error":"Service Unavailable"}""".toByteArray()
                ex.sendResponseHeaders(503, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()

            try {
                val okClient = NseMcpClient(
                    client = fastOfflineClient(),
                    bhavcopyEndpoint = "http://127.0.0.1:$port/mcp-ok",
                    cmMarketEndpoint = "http://127.0.0.1:$port/mcp-ok"
                )
                val okRes = okClient.queryBhavcopy("NIFTY 50")
                assertTrue("Live SSE JSON-RPC response must report isLiveNetworkSuccess=true", okRes.isLiveNetworkSuccess)
                assertEquals(25144.30, okRes.record.close, 0.01)
                assertTrue(okRes.logs.all { it.isSuccess })

                val errClient = NseMcpClient(
                    client = fastOfflineClient(),
                    bhavcopyEndpoint = "http://127.0.0.1:$port/mcp-err",
                    cmMarketEndpoint = "http://127.0.0.1:$port/mcp-err"
                )
                val errRes = errClient.queryBhavcopy("NIFTY 50")
                assertFalse("HTTP 503 must NOT mask failure as success", errRes.isLiveNetworkSuccess)
                assertTrue("HTTP 503 log must mark incoming response as failed", errRes.logs.any { !it.isSuccess })
                assertEquals(25144.30, errRes.record.close, 0.01)
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testMcpPluginClientHandshakeDiscoveryAuthAndToolCallWithLocalHttpServer() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            val capturedMethods = mutableListOf<String>()
            val capturedAuthHeaders = mutableListOf<String>()
            val capturedSessionHeaders = mutableListOf<String>()

            server.createContext("/mcp") { ex ->
                val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
                capturedAuthHeaders.add(auth)
                ex.requestHeaders.getFirst("Mcp-Session-Id")?.let { capturedSessionHeaders.add(it) }

                val reqBody = ex.requestBody.bufferedReader().readText()
                val reqJson = JSONObject(reqBody)
                val id = reqJson.optString("id", "1")
                val method = reqJson.optString("method")
                capturedMethods.add(method)

                if (auth != "Bearer secret-token-123") {
                    val unauth = """{"error":"Unauthorized"}""".toByteArray()
                    ex.sendResponseHeaders(401, unauth.size.toLong())
                    ex.responseBody.use { it.write(unauth) }
                    return@createContext
                }

                val responseBody = when (method) {
                    "initialize" -> {
                        ex.responseHeaders.add("Mcp-Session-Id", "sess-nifty-01")
                        """{"jsonrpc":"2.0","id":"$id","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"NiftyMockMcp","version":"2.1.0"},"capabilities":{"tools":{}}}}"""
                    }
                    "notifications/initialized" -> {
                        ex.sendResponseHeaders(202, -1)
                        return@createContext
                    }
                    "tools/list" -> {
                        // Return via SSE framing to validate Streamable HTTP + SSE unwrapping
                        """
                        event: message
                        data: {"jsonrpc":"2.0","id":"$id","result":{"tools":[{"name":"get_index_quote","description":"Fetch index spot quote","inputSchema":{"type":"object","required":["symbol"],"properties":{"symbol":{"type":"string","description":"NSE symbol"}}}}]}}
                        """.trimIndent()
                    }
                    "resources/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"resources":[{"uri":"nse://nifty50","name":"NIFTY 50 Spot","mimeType":"application/json"}]}}"""
                    "prompts/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"prompts":[{"name":"morning_brief","description":"Pre-open NIFTY desk summary"}]}}"""
                    "tools/call" -> {
                        val toolArgs = reqJson.optJSONObject("params")?.optJSONObject("arguments")
                        val sym = toolArgs?.optString("symbol", "NIFTY") ?: "NIFTY"
                        """{"jsonrpc":"2.0","id":"$id","result":{"content":[{"type":"text","text":"$sym Spot: 25144.30 (+0.02%)"}]}}"""
                    }
                    else -> """{"jsonrpc":"2.0","id":"$id","error":{"code":-32601,"message":"Method not found"}}"""
                }

                val bytes = responseBody.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()

            try {
                val mcpClient = McpPluginClient(fastOfflineClient())
                assertNotNull(mcpClient.validateUrl("not-a-url"))
                assertEquals(null, mcpClient.validateUrl("http://127.0.0.1:$port/mcp"))

                val connector = McpConnectorConfig(
                    id = "custom-test-1",
                    name = "Nifty Custom MCP",
                    url = "http://127.0.0.1:$port/mcp",
                    transportType = McpTransportType.STREAMABLE_HTTP,
                    authType = McpAuthType.BEARER_TOKEN,
                    authSecret = "secret-token-123"
                )

                val handshake = mcpClient.connectAndDiscover(connector)
                assertTrue("MCP handshake should succeed: ${handshake.errorMessage}", handshake.isSuccess)
                assertEquals("NiftyMockMcp", handshake.serverInfoName)
                assertEquals("2.1.0", handshake.serverInfoVersion)
                assertEquals("sess-nifty-01", handshake.sessionId)
                assertEquals(1, handshake.tools.size)
                assertEquals("get_index_quote", handshake.tools.first().name)
                assertEquals(listOf("symbol"), handshake.tools.first().requiredParams)
                assertEquals(1, handshake.resources.size)
                assertEquals(1, handshake.prompts.size)
                assertTrue(capturedMethods.containsAll(listOf("initialize", "notifications/initialized", "tools/list", "resources/list", "prompts/list")))
                assertTrue("Subsequent calls must send Mcp-Session-Id", capturedSessionHeaders.contains("sess-nifty-01"))
                assertTrue("Requests must include Bearer token", capturedAuthHeaders.all { it == "Bearer secret-token-123" })

                val connectedConnector = connector.copy(sessionId = handshake.sessionId)
                val toolOutcome = mcpClient.callTool(connectedConnector, "get_index_quote", """{"symbol":"NIFTY 50"}""")
                assertTrue(toolOutcome.executionResult.isSuccess)
                assertTrue(toolOutcome.executionResult.outputText.contains("NIFTY 50 Spot: 25144.30"))

                val badAuthOutcome = mcpClient.connectAndDiscover(connector.copy(authSecret = "wrong-token"))
                assertFalse("HTTP 401 should fail cleanly", badAuthOutcome.isSuccess)
                assertTrue(badAuthOutcome.errorMessage!!.contains("401"))
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testSharedPreferencesMcpConnectorStoreRoundTripPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesMcpConnectorStore(context)

        val savedConnector = McpConnectorConfig(
            id = "custom-persist-1",
            name = "NSE Analytics Connector",
            url = "https://mcp.example.com/sse",
            transportType = McpTransportType.SSE,
            authType = McpAuthType.API_KEY_HEADER,
            authHeaderName = "X-NSE-Key",
            authSecret = "api-key-999",
            customHeaders = mapOf("X-Desk" to "NIFTY"),
            isEnabled = true
        )
        store.saveCustomConnectors(listOf(savedConnector))
        store.saveBuiltInEnabledState("builtin-nse-bhavcopy", false)

        // Recreate store instance from context to assert real SharedPreferences persistence
        val reloadedStore = SharedPreferencesMcpConnectorStore(context)
        val loadedList = reloadedStore.loadCustomConnectors()
        assertEquals(1, loadedList.size)
        val loaded = loadedList.first()
        assertEquals("custom-persist-1", loaded.id)
        assertEquals("NSE Analytics Connector", loaded.name)
        assertEquals(McpTransportType.SSE, loaded.transportType)
        assertEquals(McpAuthType.API_KEY_HEADER, loaded.authType)
        assertEquals("X-NSE-Key", loaded.authHeaderName)
        assertEquals("api-key-999", loaded.authSecret)
        assertEquals("NIFTY", loaded.customHeaders["X-Desk"])
        assertEquals(false, reloadedStore.loadBuiltInEnabledStates()["builtin-nse-bhavcopy"])
    }

    @Test
    fun testSettingsToConnectorsAddCustomConnectorAndExecuteToolFlow() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/mcp") { ex ->
            val reqJson = JSONObject(ex.requestBody.bufferedReader().readText())
            val id = reqJson.optString("id", "1")
            val method = reqJson.optString("method")

            val payload = when (method) {
                "initialize" -> {
                    ex.responseHeaders.add("Mcp-Session-Id", "ui-sess-42")
                    """{"jsonrpc":"2.0","id":"$id","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"CustomUiMcp","version":"1.0"},"capabilities":{"tools":{}}}}"""
                }
                "notifications/initialized" -> {
                    ex.sendResponseHeaders(202, -1)
                    return@createContext
                }
                "tools/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"tools":[{"name":"inspect_oi","description":"Inspect NIFTY open interest","inputSchema":{"type":"object","required":["symbol"],"properties":{"symbol":{"type":"string","description":"Index symbol"}}}}]}}"""
                "resources/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"resources":[]}}"""
                "prompts/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"prompts":[]}}"""
                "tools/call" -> {
                    val sym = reqJson.optJSONObject("params")?.optJSONObject("arguments")?.optString("symbol", "NIFTY 50") ?: "NIFTY 50"
                    """{"jsonrpc":"2.0","id":"$id","result":{"content":[{"type":"text","text":"$sym OI Pulse: Bullish PCR 1.18"}]}}"""
                }
                else -> """{"jsonrpc":"2.0","id":"$id","result":{}}"""
            }
            val bytes = payload.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val fastClient = fastOfflineClient()
            val repo = NiftyMarketRepository(
                liveMarketClient = LiveMarketDataClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                nseMcpClient = NseMcpClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                mcpPluginClient = McpPluginClient(fastClient)
            )
            val viewModel = NiftyDeskViewModel(repository = repo)
            viewModel.openSettings()

            composeTestRule.setContent {
                val deskState by viewModel.deskState.collectAsState()
                val settingsPage by viewModel.settingsPage.collectAsState()
                val connectorsState by viewModel.connectorsState.collectAsState()

                MyApplicationTheme {
                    SettingsScreen(
                        settingsPage = settingsPage,
                        deskState = deskState,
                        connectorsState = connectorsState,
                        onNavigatePage = { viewModel.openSettingsPage(it) },
                        onBack = { viewModel.navigateSettingsBack() },
                        onSelectTradingStyle = { viewModel.switchTradingStyle(it) },
                        onSelectWorkflow = { viewModel.switchWorkflow(it) },
                        onRefreshLive = { viewModel.refreshLiveMarketData() },
                        validateConnectorUrl = { viewModel.validateConnectorUrl(it) },
                        onAddCustomConnector = { name, url, transport, auth, headerName, secret, headers, connectNow ->
                            viewModel.addCustomConnectorAndConnect(
                                name = name,
                                url = url,
                                transportType = transport,
                                authType = auth,
                                authHeaderName = headerName,
                                authSecret = secret,
                                customHeaders = headers,
                                connectImmediately = connectNow
                            )
                        },
                        onUpdateCustomConnector = { id, name, url, transport, auth, headerName, secret, headers, connectNow ->
                            viewModel.updateCustomConnectorAndConnect(
                                connectorId = id,
                                name = name,
                                url = url,
                                transportType = transport,
                                authType = auth,
                                authHeaderName = headerName,
                                authSecret = secret,
                                customHeaders = headers,
                                connectImmediately = connectNow
                            )
                        },
                        onDeleteCustomConnector = { viewModel.removeCustomConnector(it) },
                        onToggleConnectorEnabled = { viewModel.toggleConnectorEnabled(it) },
                        onConnectConnector = { viewModel.connectConnector(it) },
                        onExecuteConnectorTool = { id, toolName, args ->
                            viewModel.executeConnectorTool(id, toolName, args)
                        }
                    )
                }
            }

            // 1. Settings Root -> Connectors
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("settings_connectors_item").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()

            // 2. Connectors -> Add custom connector
            composeTestRule.onNodeWithTag("connectors_screen").assertIsDisplayed()
            composeTestRule.onNodeWithText("Settings → Connectors").assertIsDisplayed()
            composeTestRule.onNodeWithTag("add_custom_connector_button").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()

            // 3. Fill custom MCP server details and submit
            composeTestRule.onNodeWithTag("add_custom_connector_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("connector_name_input").performScrollTo().performTextInput("NIFTY Dev MCP")
            composeTestRule.onNodeWithTag("connector_url_input").performScrollTo().performTextInput("http://127.0.0.1:$port/mcp")
            composeTestRule.onNodeWithTag("connector_auth_BEARER_TOKEN").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("connector_auth_secret_input").performScrollTo().performTextInput("tok-99")
            composeTestRule.onNodeWithTag("confirm_add_connector_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("add_custom_connector_dialog").assertDoesNotExist()

            // 4. Wait for live handshake & tools/list discovery to complete
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                val custom = viewModel.connectorsState.value.connectors.firstOrNull { !it.isBuiltIn }
                if (custom != null && custom.status == McpConnectionStatus.CONNECTED && custom.tools.isNotEmpty()) {
                    break
                }
                Thread.sleep(50)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val addedConnector = viewModel.connectorsState.value.connectors.firstOrNull { !it.isBuiltIn }
            assertNotNull("Custom connector should have been added", addedConnector)
            assertEquals(
                "Expected CONNECTED, error=${addedConnector!!.lastError}",
                McpConnectionStatus.CONNECTED,
                addedConnector.status
            )
            assertEquals("NIFTY Dev MCP", addedConnector.name)
            assertTrue("Expected tools discovered", addedConnector.tools.isNotEmpty())
            assertEquals("inspect_oi", addedConnector.tools.first().name)

            composeTestRule.onNodeWithTag("connector_card_${addedConnector.id}")
                .performScrollTo()
                .assertIsDisplayed()

            // 5. Open Tools Inspector and execute discovered MCP tool
            composeTestRule.onNodeWithTag("connector_inspect_${addedConnector.id}")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("connector_inspector_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("tool_param_symbol").performScrollTo().performTextInput("NIFTY 50")
            composeTestRule.onNodeWithTag("execute_mcp_tool_button").performScrollTo().performClick()

            val toolDeadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < toolDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                if (composeTestRule.onAllNodesWithTag("mcp_tool_output_box").fetchSemanticsNodes().isNotEmpty()) {
                    break
                }
                Thread.sleep(50)
            }
            composeTestRule.onNodeWithTag("mcp_tool_output_box").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("NIFTY 50 OI Pulse: Bullish PCR 1.18", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testUnifiedDashboardHasCompactHeaderAndNoChartTerminalSwitcher() {
        val fastClient = fastOfflineClient()
        val repo = NiftyMarketRepository(
            liveMarketClient = LiveMarketDataClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
            nseMcpClient = NseMcpClient(fastClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
            mcpPluginClient = McpPluginClient(fastClient)
        )
        val viewModel = NiftyDeskViewModel(repository = repo)
        var settingsOpened = false
        var executionOpened = false

        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    DashboardHeaderBar(onOpenSettings = { settingsOpened = true })
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToExecution = { executionOpened = true }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_header_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("NITHIX NIFTY DESK").assertIsDisplayed()
        composeTestRule.onNodeWithText("DASHBOARD").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("chart_mode_tab").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("dashboard_mode_tab").fetchSemanticsNodes().size)

        composeTestRule.onNodeWithTag("open_settings_button").assertIsDisplayed().performClick()
        assertTrue("Settings callback should fire", settingsOpened)

        composeTestRule.onNodeWithTag("dashboard_execution_link").performScrollTo().performClick()
        assertTrue("Execution shortcut should fire", executionOpened)
    }
}
