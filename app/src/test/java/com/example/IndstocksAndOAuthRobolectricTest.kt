package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.data.DeskState
import com.example.data.NiftyMarketRepository
import com.example.model.ExecutionSafetyMode
import com.example.model.IndstocksBrokerState
import com.example.model.IndstocksConnectionStatus
import com.example.model.IndstocksFundsSummary
import com.example.model.IndstocksHolding
import com.example.model.IndstocksOrderModifyRequest
import com.example.model.IndstocksOrderRecord
import com.example.model.IndstocksOrderRequest
import com.example.model.IndstocksPosition
import com.example.model.IndstocksSmartOrderModifyRequest
import com.example.model.IndstocksSmartOrderRequest
import com.example.model.IndstocksUserProfile
import com.example.model.McpAuthType
import com.example.model.McpConnectionStatus
import com.example.model.McpConnectorConfig
import com.example.model.McpConnectorsUiState
import com.example.model.McpTransportType
import com.example.model.Timeframe
import com.example.network.IndstocksApiClient
import com.example.network.IndstocksWebSocketClient
import com.example.network.LiveMarketDataClient
import com.example.network.McpOAuthClient
import com.example.network.McpPluginClient
import com.example.network.NseMcpClient
import com.example.ui.components.AddCustomConnectorDialog
import com.example.ui.components.IndstocksBrokerSettingsCard
import com.example.ui.screens.ExecutionDeskScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SettingsPage
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IndstocksAndOAuthRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fastHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .build()

    @Test
    fun testMcpOAuth401DiscoveryDcrPkceCodeExchangeAndRefresh() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            val dcrRegisteredClientId = "dcr-client-991"
            var capturedVerifier = ""
            val tokenCalls = mutableListOf<String>()
            val expireFirstToken = AtomicInteger(0)

            server.createContext("/.well-known/oauth-protected-resource") { ex ->
                val json = """
                    {
                      "resource": "http://127.0.0.1:$port/mcp",
                      "authorization_servers": ["http://127.0.0.1:$port"],
                      "scopes_supported": ["mcp:tools", "portfolio:read"]
                    }
                """.trimIndent().toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, json.size.toLong())
                ex.responseBody.use { it.write(json) }
            }

            server.createContext("/.well-known/oauth-authorization-server") { ex ->
                val json = """
                    {
                      "issuer": "http://127.0.0.1:$port",
                      "authorization_endpoint": "http://127.0.0.1:$port/authorize",
                      "token_endpoint": "http://127.0.0.1:$port/token",
                      "registration_endpoint": "http://127.0.0.1:$port/register",
                      "scopes_supported": ["mcp:tools", "portfolio:read"]
                    }
                """.trimIndent().toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, json.size.toLong())
                ex.responseBody.use { it.write(json) }
            }

            server.createContext("/register") { ex ->
                val resp = """{"client_id":"$dcrRegisteredClientId"}""".toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(201, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/token") { ex ->
                val body = ex.requestBody.bufferedReader().readText()
                val params = body.split("&").associate { pair ->
                    val parts = pair.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                }
                val grantType = params["grant_type"].orEmpty()
                tokenCalls.add(grantType)
                if (grantType == "authorization_code") {
                    capturedVerifier = params["code_verifier"].orEmpty()
                    assertEquals("auth-code-indmoney-77", params["code"])
                    assertEquals(dcrRegisteredClientId, params["client_id"])
                    val resp = """
                        {
                          "access_token": "oauth-access-token-v1",
                          "refresh_token": "oauth-refresh-token-v1",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                    """.trimIndent().toByteArray()
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(200, resp.size.toLong())
                    ex.responseBody.use { it.write(resp) }
                } else if (grantType == "refresh_token") {
                    assertEquals("oauth-refresh-token-v1", params["refresh_token"])
                    val resp = """
                        {
                          "access_token": "oauth-access-token-v2",
                          "refresh_token": "oauth-refresh-token-v2",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                    """.trimIndent().toByteArray()
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(200, resp.size.toLong())
                    ex.responseBody.use { it.write(resp) }
                } else {
                    ex.sendResponseHeaders(400, -1)
                }
            }

            server.createContext("/mcp") { ex ->
                val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
                val reqJson = JSONObject(ex.requestBody.bufferedReader().readText())
                val id = reqJson.optString("id", "1")
                val method = reqJson.optString("method")

                val isTokenV1 = auth == "Bearer oauth-access-token-v1" && expireFirstToken.get() == 0
                val isTokenV2 = auth == "Bearer oauth-access-token-v2"
                if (!isTokenV1 && !isTokenV2) {
                    ex.responseHeaders.add(
                        "WWW-Authenticate",
                        """Bearer realm="indmoney-mcp", resource_metadata="http://127.0.0.1:$port/.well-known/oauth-protected-resource", scope="mcp:tools""""
                    )
                    val unauth = """{"error":"invalid_token"}""".toByteArray()
                    ex.sendResponseHeaders(401, unauth.size.toLong())
                    ex.responseBody.use { it.write(unauth) }
                    return@createContext
                }

                val payload = when (method) {
                    "initialize" -> {
                        ex.responseHeaders.add("Mcp-Session-Id", "indmoney-sess-1")
                        """{"jsonrpc":"2.0","id":"$id","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"INDmoney MCP","version":"1.2.0"},"capabilities":{"tools":{}}}}"""
                    }
                    "notifications/initialized" -> {
                        ex.sendResponseHeaders(202, -1)
                        return@createContext
                    }
                    "tools/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"tools":[{"name":"get_portfolio_holdings","description":"Fetch portfolio holdings","inputSchema":{"type":"object","properties":{}}}]}}"""
                    "resources/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"resources":[]}}"""
                    "prompts/list" -> """{"jsonrpc":"2.0","id":"$id","result":{"prompts":[]}}"""
                    else -> """{"jsonrpc":"2.0","id":"$id","result":{}}"""
                }
                val bytes = payload.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()

            try {
                val httpClient = fastHttpClient()
                val oauthClient = McpOAuthClient(httpClient)
                val pluginClient = McpPluginClient(httpClient, oauthClient)
                val repo = NiftyMarketRepository(
                    liveMarketClient = LiveMarketDataClient(httpClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                    nseMcpClient = NseMcpClient(httpClient, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                    mcpOAuthClient = oauthClient,
                    mcpPluginClient = pluginClient
                )

                // 1. Add connector with NONE auth and attempt connect -> triggers 401 -> AUTH_REQUIRED + OAuth discovery
                val added = repo.addCustomConnector(
                    name = "INDmoney MCP",
                    url = "http://127.0.0.1:$port/mcp",
                    transportType = McpTransportType.STREAMABLE_HTTP,
                    authType = McpAuthType.NONE,
                    authHeaderName = "Authorization",
                    authSecret = ""
                )
                repo.connectAndDiscoverConnector(added.id)

                val after401 = repo.connectorsState.value.connectors.first { it.id == added.id }
                assertEquals(McpConnectionStatus.AUTH_REQUIRED, after401.status)
                assertNotNull("OAuth metadata must be discovered on HTTP 401", after401.oauthMetadata)
                assertEquals("http://127.0.0.1:$port/authorize", after401.oauthMetadata!!.authorizationEndpoint)

                // 2. Prepare OAuth 2.1 PKCE + DCR authorization URL
                val redirectUri = "http://127.0.0.1:49152/callback"
                val pending = repo.prepareOAuthAuthorization(
                    connectorId = added.id,
                    explicitClientId = "",
                    redirectUri = redirectUri
                )
                assertNotNull("Pending OAuth flow must be created via DCR", pending)
                assertTrue(pending!!.authorizationUrl.contains("code_challenge_method=S256"))
                assertTrue(pending.authorizationUrl.contains("client_id=$dcrRegisteredClientId"))
                val capturedChallenge = android.net.Uri.parse(pending.authorizationUrl).getQueryParameter("code_challenge").orEmpty()
                assertTrue(capturedChallenge.isNotBlank())

                // 3. Complete OAuth with callback URL carrying code + matching state
                val callbackUrl = "$redirectUri?code=auth-code-indmoney-77&state=${pending.state}"
                val exchanged = repo.completeOAuthWithCallbackOrCode(callbackUrl)
                assertTrue("OAuth PKCE code exchange & reconnect must succeed", exchanged)
                assertTrue("PKCE verifier must have been sent", capturedVerifier.length >= 43)

                val connected = repo.connectorsState.value.connectors.first { it.id == added.id }
                assertEquals(McpConnectionStatus.CONNECTED, connected.status)
                assertEquals(McpAuthType.OAUTH_PKCE, connected.authType)
                assertEquals("oauth-access-token-v1", connected.authSecret)
                assertEquals("oauth-refresh-token-v1", connected.oauthRefreshToken)
                assertEquals("get_portfolio_holdings", connected.tools.first().name)

                // 4. Simulate v1 token expiry -> McpPluginClient automatically refreshes using v1 refresh_token
                expireFirstToken.set(1)
                repo.connectAndDiscoverConnector(added.id)
                val refreshed = repo.connectorsState.value.connectors.first { it.id == added.id }
                assertEquals(McpConnectionStatus.CONNECTED, refreshed.status)
                assertEquals("oauth-access-token-v2", refreshed.authSecret)
                assertEquals(listOf("authorization_code", "refresh_token"), tokenCalls)
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testSettingsConnectorsOAuthSignInButtonOpensAuthorizeDialog() {
        var discoveredId: String? = null
        val authRequiredConnector = McpConnectorConfig(
            id = "indmoney-oauth-test",
            name = "indmoney",
            url = "https://mcp.indmoney.com/mcp",
            status = McpConnectionStatus.AUTH_REQUIRED,
            authType = McpAuthType.OAUTH_PKCE,
            lastError = "Sign in required (OAuth 2.1 + PKCE)"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    SettingsScreen(
                        settingsPage = SettingsPage.CONNECTORS,
                        deskState = DeskState.createOfflinePlaceholderForUiTest(),
                        connectorsState = McpConnectorsUiState(connectors = listOf(authRequiredConnector)),
                        onNavigatePage = {},
                        onBack = {},
                        onSelectTradingStyle = {},
                        onSelectWorkflow = {},
                        onRefreshLive = {},
                        validateConnectorUrl = { null },
                        onAddCustomConnector = { _, _, _, _, _, _, _, _ -> },
                        onUpdateCustomConnector = { _, _, _, _, _, _, _, _, _ -> },
                        onDeleteCustomConnector = {},
                        onToggleConnectorEnabled = {},
                        onConnectConnector = {},
                        onExecuteConnectorTool = { _, _, _ -> },
                        onDiscoverOAuth = { id -> discoveredId = id }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connector_authorize_indmoney-oauth-test")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mcp_oauth_dialog").assertIsDisplayed()
        assertEquals("indmoney-oauth-test", discoveredId)
    }

    @Test
    fun testAddCustomConnectorCompact2x2AuthGridAndOAuthClientId() {
        var confirmedAuthType: McpAuthType? = null
        var confirmedClientId = ""

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    AddCustomConnectorDialog(
                        validateUrl = { null },
                        onConfirm = { _, _, _, auth, _, _, _, _, oauthCid ->
                            confirmedAuthType = auth
                            confirmedClientId = oauthCid
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connector_auth_NONE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("connector_auth_OAUTH_PKCE").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("connector_auth_BEARER_TOKEN").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("connector_auth_API_KEY_HEADER").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("connector_name_input").performScrollTo().performTextInput("INDmoney MCP")
        composeTestRule.onNodeWithTag("connector_url_input").performScrollTo().performTextInput("https://mcp.indmoney.com/mcp")
        composeTestRule.onNodeWithTag("connector_oauth_client_id_input").performScrollTo().performTextInput("custom-cid-1")
        composeTestRule.onNodeWithTag("confirm_add_connector_button").performScrollTo().performClick()

        assertEquals(McpAuthType.OAUTH_PKCE, confirmedAuthType)
        assertEquals("custom-cid-1", confirmedClientId)
    }

    @Test
    fun testIndstocksRawAuthQuotesCandlesOptionChainPortfolioAndGetWithBody() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            val rawAuthHeaders = mutableListOf<String>()
            var marginRequestMethod = ""
            var marginRequestBody = ""
            var orderDetailsMethod = ""

            fun assertRawAuth(headers: com.sun.net.httpserver.Headers) {
                val auth = headers.getFirst("Authorization").orEmpty()
                rawAuthHeaders.add(auth)
                assertFalse("INDstocks Authorization header must NOT include 'Bearer ' prefix: $auth", auth.startsWith("Bearer ", ignoreCase = true))
                assertEquals("raw-indstocks-token-001", auth)
            }

            server.createContext("/generate/token") { ex ->
                assertEquals("api-key-ind-99", ex.requestHeaders.getFirst("x-api-key"))
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                assertEquals("1234", req.optString("mpin"))
                assertEquals("654321", req.optString("totp"))
                val resp = """{"status":"success","token":"generated-24h-token"}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/user/profile") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "user_id": "USR101",
                        "ucc": "UCC9876",
                        "first_name": "Arjun",
                        "last_name": "Mehta",
                        "demat_id": "1208160012345678",
                        "is_ddpi_active": true,
                        "is_nse_onboarded": true,
                        "is_nse_fno_onboarded": true
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/funds") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "detailed_avl_balance": {"option_buy": 450000.0, "option_sell": 440000.0, "future": 445000.0, "eq_mis": 450000.0, "eq_cnc": 450000.0},
                        "sod_balance": 500000.0,
                        "withdrawal_balance": 450000.0,
                        "pledge_received": 25000.0,
                        "realized_pnl": 4200.0,
                        "unrealized_pnl": 1850.0,
                        "brokerage": 20.0
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/market/quotes/full") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "NSE_40000001": {"symbol":"NIFTY 50","live_price":25285.50,"prev_close":25144.30,"day_open":25180.0,"day_high":25310.0,"day_low":25150.0,"day_change":141.20,"day_change_percentage":0.56,"ts":1763500000},
                        "NSE_26017": {"symbol":"INDIA VIX","live_price":12.85,"prev_close":13.45,"day_change_percentage":-4.46}
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/market/historical/15minute") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "candles": [
                          {"ts": 1763500000, "o": 25150.0, "h": 25190.0, "l": 25140.0, "c": 25180.0, "v": 120000},
                          {"ts": 1763500900, "o": 25180.0, "h": 25220.0, "l": 25175.0, "c": 25210.0, "v": 150000},
                          {"ts": 1763501800, "o": 25210.0, "h": 25250.0, "l": 25205.0, "c": 25240.0, "v": 180000},
                          {"ts": 1763502700, "o": 25240.0, "h": 25270.0, "l": 25230.0, "c": 25260.0, "v": 140000},
                          {"ts": 1763503600, "o": 25260.0, "h": 25295.0, "l": 25255.0, "c": 25280.0, "v": 165000},
                          {"ts": 1763504500, "o": 25280.0, "h": 25310.0, "l": 25270.0, "c": 25285.5, "v": 190000}
                        ]
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/market/instruments/expiries") { ex ->
                assertRawAuth(ex.requestHeaders)
                val query = ex.requestURI.rawQuery.orEmpty()
                assertTrue("Expected underlying=NIFTY in expiries query: $query", query.contains("underlying=NIFTY"))
                assertTrue("Expected segment=DERIVATIVE in expiries query: $query", query.contains("segment=DERIVATIVE"))
                val resp = """{"status":"success","data":{"expiries":["2026-11-26","2026-12-03"]}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/market/option-chain") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "underlying_ltp": 25285.50,
                        "expiry": "2026-11-26",
                        "strikes": {
                          "25250": {
                            "ce":{"security_id":"54301","trading_symbol":"NIFTY26NOV25250CE","last_price":155.0,"previous_close_price":140.0,"oi":2200000,"previous_oi":2020000,"iv":12.9,"greeks":{"delta":0.61,"gamma":0.0012,"theta":-14.2,"vega":11.5}},
                            "pe":{"security_id":"54302","trading_symbol":"NIFTY26NOV25250PE","last_price":110.0,"previous_close_price":122.0,"oi":3400000,"previous_oi":2950000,"iv":13.1,"greeks":{"delta":-0.39,"gamma":0.0012,"theta":-13.8,"vega":11.5}}
                          },
                          "25300": {
                            "ce":{"security_id":"54321","trading_symbol":"NIFTY26NOV25300CE","last_price":122.5,"previous_close_price":115.0,"oi":2900000,"previous_oi":2690000,"iv":12.7,"greeks":{"delta":0.50,"gamma":0.0014,"theta":-15.1,"vega":12.0}},
                            "pe":{"security_id":"54322","trading_symbol":"NIFTY26NOV25300PE","last_price":135.0,"previous_close_price":140.0,"oi":4100000,"previous_oi":3480000,"iv":13.0,"greeks":{"delta":-0.50,"gamma":0.0014,"theta":-14.9,"vega":12.0}}
                          },
                          "25350": {
                            "ce":{"security_id":"54341","trading_symbol":"NIFTY26NOV25350CE","last_price":94.0,"previous_close_price":90.0,"oi":3600000,"previous_oi":3290000,"iv":12.8,"greeks":{"delta":0.38,"gamma":0.0011,"theta":-13.0,"vega":11.0}},
                            "pe":{"security_id":"54342","trading_symbol":"NIFTY26NOV25350PE","last_price":164.0,"previous_close_price":170.0,"oi":2100000,"previous_oi":1960000,"iv":13.2,"greeks":{"delta":-0.62,"gamma":0.0011,"theta":-12.8,"vega":11.0}}
                          }
                        }
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/portfolio/holdings") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """{"status":"success","data":[{"symbol":"RELIANCE","security_id":"2885","isin":"INE002A01018","exchange":"NSE","total_qty":10,"used_qty":0,"t1_qty":2,"dp_qty":8,"avg_price":2850.0}]}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/portfolio/positions") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """{"status":"success","data":[{"position_id":"POS-1","symbol":"NIFTY26NOV25300CE","security_id":"54321","segment":"DERIVATIVE","product":"MARGIN","net_qty":75,"avg_price":122.5,"buy_qty":75,"buy_avg":122.5,"sell_qty":0,"realized_profit":0.0}]}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/order-book") { ex ->
                assertRawAuth(ex.requestHeaders)
                val resp = """{"status":"success","data":[{"id":"ORD-101","exch_order_id":"EX-999","name":"NIFTY26NOV25300CE","security_id":"54321","txn_type":"BUY","segment":"DERIVATIVE","product":"MARGIN","requested_qty":75,"traded_qty":0,"requested_price":"122.50","status":"PENDING"}]}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/trade-book") { ex ->
                assertRawAuth(ex.requestHeaders)
                assertTrue("Expected segment query param in /trade-book", ex.requestURI.rawQuery.orEmpty().contains("segment=DERIVATIVE"))
                val resp = """{"status":"success","data":[{"fill_id":"TRD-1","exch_order_id":"EX-998","order_id":"ORD-100","scrip_code":"NFO_54302","symbol":"NIFTY26NOV25250PE","txn_type":"BUY","quantity":75,"price":110.0,"trade_date":"2026-11-19 10:15:00"}]}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/margin") { ex ->
                assertRawAuth(ex.requestHeaders)
                marginRequestMethod = ex.requestMethod
                marginRequestBody = ex.requestBody.bufferedReader().readText()
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "total_margin": 9187.5,
                        "span_margin": 6000.0,
                        "exposure_margin": 3187.5,
                        "available_balance": 450000.0,
                        "insufficient_balance": 0.0,
                        "charges": {
                          "stt": 4.2,
                          "exchange_charges": 1.5,
                          "stamp_duty": 0.5,
                          "sebi_turn_over_charges": 0.1,
                          "brokerage": 10.0,
                          "gst": 1.8,
                          "IPFTCharges": 0.2,
                          "total_charges": 18.3
                        }
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/order") { ex ->
                assertRawAuth(ex.requestHeaders)
                orderDetailsMethod = ex.requestMethod
                val bodyJson = JSONObject(ex.requestBody.bufferedReader().readText())
                assertEquals("DERIVATIVE", bodyJson.optString("segment"))
                if (bodyJson.optString("order_id") == "MISSING-512") {
                    val err = """{"status":"failed","message":"Order not found"}""".toByteArray()
                    ex.sendResponseHeaders(512, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                } else {
                    val resp = """{"status":"success","data":{"id":"ORD-101","name":"NIFTY26NOV25300CE","txn_type":"BUY","requested_qty":75,"requested_price":"122.50","status":"PENDING"}}""".toByteArray()
                    ex.sendResponseHeaders(200, resp.size.toLong())
                    ex.responseBody.use { it.write(resp) }
                }
            }
            server.start()

            try {
                val client = IndstocksApiClient(fastHttpClient(), baseUrl = "http://127.0.0.1:$port")
                val messyToken = "Bearer raw-indstocks-token-001"

                val totpTok = client.generateToken("api-key-ind-99", "1234", "654321").getOrThrow()
                assertEquals("generated-24h-token", totpTok)

                val profile = client.fetchUserProfile(messyToken).getOrThrow()
                assertEquals("UCC9876", profile.ucc)
                assertEquals("Arjun Mehta", profile.name)
                assertTrue(profile.isDdpiActive)
                assertTrue(profile.isNseFnoOnboarded)
                assertTrue(profile.segments.contains("NFO"))

                val funds = client.fetchFunds(messyToken).getOrThrow()
                assertEquals(450000.0, funds.availableMargin, 0.01)
                assertEquals(500000.0, funds.sodBalance, 0.01)
                assertEquals(25000.0, funds.collateral, 0.01)

                val quotes = client.fetchMarketQuotes(messyToken, listOf(IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE)).getOrThrow()
                assertEquals(25285.50, quotes[IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE]!!.lastPrice, 0.01)

                val candles = client.fetchHistoricalCandles(messyToken, IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE, Timeframe.M15).getOrThrow()
                assertEquals(6, candles.size)
                assertEquals(1763500000L * 1000L, candles.first().timestamp)
                assertEquals(25285.5, candles.last().close, 0.01)

                val chainRes = client.fetchOptionChainWithSecurities(messyToken, 25285.50, "2026-11-26").getOrThrow()
                assertEquals(25300.0, chainRes.snapshot.atmStrike, 0.01)
                assertEquals(3, chainRes.snapshot.strikes.size)
                // OI change = oi - previous_oi (2900000 - 2690000 = 210000), nested greeks delta = 0.50
                val atm = chainRes.snapshot.strikes.first { it.strikePrice == 25300.0 }
                assertEquals(210000L, atm.callOiChange)
                assertEquals(0.50, atm.callDelta, 0.001)
                assertEquals("54321", chainRes.legSecurities["25300_CE"]?.securityId)
                assertEquals("NIFTY26NOV25300CE", chainRes.legSecurities["25300_CE"]?.tradingSymbol)

                val holding = client.fetchHoldings(messyToken).getOrThrow().first()
                assertEquals("RELIANCE", holding.symbol)
                assertEquals(10, holding.quantity)
                assertEquals("INE002A01018", holding.isin)

                val position = client.fetchPositions(messyToken).getOrThrow().first()
                assertEquals(75, position.netQty)
                assertEquals("POS-1", position.positionId)

                assertEquals("ORD-101", client.fetchOrderBook(messyToken).getOrThrow().first().orderId)
                assertEquals("TRD-1", client.fetchTradeBook(messyToken, "DERIVATIVE").getOrThrow().first().tradeId)

                val margin = client.calculateMargin(messyToken, securityId = "54321", qty = 75, price = 122.5).getOrThrow()
                assertEquals("GET", marginRequestMethod)
                val marginReqJson = JSONObject(marginRequestBody)
                assertEquals("54321", marginReqJson.optString("securityID"))
                assertEquals("BUY", marginReqJson.optString("txnType"))
                assertEquals("75", marginReqJson.optString("quantity"))
                assertEquals("122.50", marginReqJson.optString("price"))
                assertEquals("NSE", marginReqJson.optString("exchange"))
                assertEquals("MARGIN", marginReqJson.optString("product"))
                assertEquals(9187.5, margin.requiredMargin, 0.01)
                assertEquals(10.0, margin.chargesBreakdown!!.brokerage, 0.01)
                assertEquals(18.3, margin.brokerageEstimate, 0.01)

                val missing = client.fetchOrderDetails(messyToken, "MISSING-512").getOrThrow()
                assertEquals("GET", orderDetailsMethod)
                assertNull("HTTP 512 on /order must return null cleanly", missing)
                assertTrue(rawAuthHeaders.all { it == "raw-indstocks-token-001" })
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testIndstocksTotpCooldownAndPrimaryFeedWithYahooFallback() {
        runBlocking {
            val indServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val indPort = indServer.address.port
            val yahooServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val yahooPort = yahooServer.address.port
            val failIndWith401 = AtomicInteger(0)

            indServer.createContext("/generate/token") { ex ->
                val resp = """{"token":"totp-token-888"}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/user/profile") { ex ->
                val resp = """{"data":{"ucc":"IND555","first_name":"Priya","last_name":"Nair","is_ddpi_active":true,"is_nse_fno_onboarded":true}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/funds") { ex ->
                val resp = """{"data":{"detailed_avl_balance":{"option_buy":320000.0},"sod_balance":320000.0}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/quotes/full") { ex ->
                if (failIndWith401.get() == 1) {
                    val err = """{"message":"Token expired"}""".toByteArray()
                    ex.sendResponseHeaders(401, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                    return@createContext
                }
                val resp = """{"data":{"NSE_40000001":{"symbol":"NIFTY 50","live_price":25410.25,"prev_close":25250.0,"day_open":25280.0,"day_high":25430.0,"day_low":25260.0}}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/quotes/ltp") { ex ->
                val resp = """{"status":"success","data":{}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/historical/15minute") { ex ->
                if (failIndWith401.get() == 1) {
                    ex.sendResponseHeaders(401, -1)
                    return@createContext
                }
                val resp = """
                    {
                      "data": {
                        "candles": [
                          {"ts":1763500000,"o":25300,"h":25340,"l":25290,"c":25330,"v":1000},
                          {"ts":1763500900,"o":25330,"h":25360,"l":25320,"c":25350,"v":1200},
                          {"ts":1763501800,"o":25350,"h":25380,"l":25340,"c":25370,"v":1300},
                          {"ts":1763502700,"o":25370,"h":25400,"l":25360,"c":25390,"v":1400},
                          {"ts":1763503600,"o":25390,"h":25420,"l":25385,"c":25410.25,"v":1500}
                        ]
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/instruments/expiries") { ex ->
                val resp = """{"data":{"expiries":["2026-11-26"]}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/instruments/search") { ex ->
                if (failIndWith401.get() == 1) {
                    val err = """{"message":"Token expired"}""".toByteArray()
                    ex.sendResponseHeaders(401, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                    return@createContext
                }
                val resp = """{"status":"success","data":{"instruments":[{"security_id":"51011","trading_symbol":"NIFTY26NOVFUT","expiry":"2026-11-26","lot_size":75,"strike_price":null,"option_type":null}]}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/market/option-chain") { ex ->
                val resp = """{"data":{"underlying_ltp":25410.25,"strikes":{"25400":{"ce":{"security_id":"61001","trading_symbol":"NIFTY26NOV25400CE","last_price":142.0,"oi":2500000,"previous_oi":2400000},"pe":{"security_id":"61002","trading_symbol":"NIFTY26NOV25400PE","last_price":128.0,"oi":3800000,"previous_oi":3550000}}}}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            indServer.createContext("/portfolio/holdings") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            indServer.createContext("/portfolio/positions") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            indServer.createContext("/order-book") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            indServer.createContext("/trade-book") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }

            yahooServer.createContext("/chart/^NSEI") { ex ->
                val resp = """
                    {"chart":{"result":[{"meta":{"regularMarketPrice":25188.40,"chartPreviousClose":25144.30,"regularMarketDayHigh":25210.0,"regularMarketDayLow":25120.0,"regularMarketOpen":25150.0},"timestamp":[1763500000,1763500900,1763501800,1763502700,1763503600],"indicators":{"quote":[{"open":[25150,25160,25170,25180,25185],"high":[25165,25175,25185,25195,25210],"low":[25140,25155,25165,25175,25180],"close":[25160,25170,25180,25185,25188.4],"volume":[1000,1100,1200,1300,1400]}]}}],"error":null}}
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            yahooServer.createContext("/chart/^INDIAVIX") { ex ->
                val resp = """{"chart":{"result":[{"meta":{"regularMarketPrice":13.20,"chartPreviousClose":13.50}}],"error":null}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            yahooServer.createContext("/nse-index") { ex ->
                val resp = """{"data":[{"symbol":"NIFTY 50","lastPrice":25188.40}],"advance":{"advances":"30","declines":"20","unchanged":"0"}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            indServer.start()
            yahooServer.start()

            try {
                val http = fastHttpClient()
                val wsClient = IndstocksWebSocketClient(
                    client = http,
                    pricesWsUrl = "ws://127.0.0.1:1/ws/prices",
                    orderUpdatesWsUrl = "ws://127.0.0.1:1/ws/trades"
                )
                val repo = NiftyMarketRepository(
                    liveMarketClient = LiveMarketDataClient(
                        client = http,
                        yahooChartBaseUrl = "http://127.0.0.1:$yahooPort/chart",
                        nseIndexApiUrl = "http://127.0.0.1:$yahooPort/nse-index"
                    ),
                    nseMcpClient = NseMcpClient(http, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                    indstocksClient = IndstocksApiClient(http, "http://127.0.0.1:$indPort"),
                    indstocksWsClient = wsClient
                )

                // 1. Connect via TOTP -> connects and sets INDstocks as Primary Feed
                repo.generateIndstocksTokenViaTotp("cid-test", "4321", "112233")
                assertEquals(IndstocksConnectionStatus.CONNECTED, repo.indstocksState.value.status)
                assertEquals("INDstocks Live API", repo.deskState.value.dataSourceLabel)
                assertEquals(25410.25, repo.deskState.value.spot.spotPrice, 0.01)
                assertEquals("2026-11-26", repo.deskState.value.optionsChain.expiryDate)
                assertEquals(320000.0, repo.deskState.value.availableCapital, 0.01)
                assertEquals(75, repo.indstocksState.value.activeContractLotSize)

                // 2. Immediate 2nd TOTP attempt must be blocked by 60s cooldown
                repo.generateIndstocksTokenViaTotp("cid-test", "4321", "998877")
                assertTrue(repo.indstocksState.value.lastError.orEmpty().contains("wait", ignoreCase = true))

                // 3. Token expiry (HTTP 401) -> marks TOKEN_EXPIRED and seamlessly falls back to Yahoo/NSE feed
                failIndWith401.set(1)
                repo.refreshLiveMarketData(Timeframe.M15)
                assertEquals(IndstocksConnectionStatus.TOKEN_EXPIRED, repo.indstocksState.value.status)
                assertEquals(25188.40, repo.deskState.value.spot.spotPrice, 0.01)
                assertTrue(repo.deskState.value.isLiveMarketData)
                assertTrue(repo.deskState.value.dataSourceLabel.contains("Fallback"))
            } finally {
                indServer.stop(0)
                yahooServer.stop(0)
            }
        }
    }

    @Test
    fun testIndstocksInstrumentSearchMarketDepthAndCsvParsing() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port

            server.createContext("/market/instruments/search") { ex ->
                val q = ex.requestURI.rawQuery.orEmpty()
                assertTrue(q.contains("underlying=NIFTY"))
                assertTrue(q.contains("instrument_type=OPTIDX"))
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "instruments": [
                          {"security_id":"54321","trading_symbol":"NIFTY26NOV25300CE","expiry":"2026-11-26","lot_size":75,"strike_price":25300,"option_type":"CE"}
                        ]
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/market/quotes/mkt") { ex ->
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "NSE_40000001": {
                          "market_depth": {
                            "aggregate": {"total_buy": "1,25,000", "total_sell": "98,500", "buy_percentage": 55.9, "sell_percentage": 44.1},
                            "depth": [
                              {"buy": {"quantity": "750", "price": "122.50"}, "sell": {"quantity": "600", "price": "122.75"}},
                              {"buy": {"quantity": "1500", "price": "122.25"}, "sell": {"quantity": "900", "price": "123.00"}}
                            ]
                          }
                        }
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            server.start()

            try {
                val client = IndstocksApiClient(fastHttpClient(), "http://127.0.0.1:$port")
                val rows = client.searchDerivativeContracts(
                    accessToken = "tok-1",
                    underlying = "NIFTY",
                    instrumentType = "OPTIDX",
                    strikeFrom = 25300.0,
                    strikeTo = 25300.0,
                    optionType = "CE"
                ).getOrThrow()
                assertEquals(1, rows.size)
                assertEquals("54321", rows.first().securityId)
                assertEquals(75, rows.first().lotSize)
                assertEquals("NFO_54321", rows.first().scripCode)

                val depth = client.fetchMarketDepth("tok-1", "NSE_40000001").getOrThrow()
                assertNotNull(depth)
                assertEquals(55.9, depth!!.buyPercentage, 0.01)
                assertEquals(2, depth.levels.size)
                assertEquals(122.50, depth.levels.first().buyPrice, 0.01)

                // Verify CSV parsing for both index (3 positional columns) & fno (LOT_UNITS + FREEZE_QTY)
                val indexCsv = "EXCH,SEGMENT,SECURITY_ID\nNSE,NIFTY 50,40000001\nNSE,INDIA VIX,26017"
                val indexParsed = client.parseInstrumentsCsv(indexCsv, "index")
                assertEquals(2, indexParsed.size)
                assertEquals("NIFTY 50", indexParsed.first().symbol)
                assertEquals("40000001", indexParsed.first().securityId)

                val fnoCsv = "EXCH,SEGMENT,SECURITY_ID,INSTRUMENT_NAME,EXPIRY_FLAG,TRADING_SYMBOL,CUSTOM_SYMBOL,LOT_UNITS,FREEZE_QTY,TICK_SIZE,EXPIRY_DATE,STRIKE_PRICE,OPTION_TYPE\nNSE,FNO,51011,FUTIDX,M,NIFTY26NOVFUT,NIFTY,75,1800,0.05,2026-11-26,,"
                val fnoParsed = client.parseInstrumentsCsv(fnoCsv, "fno")
                assertEquals(1, fnoParsed.size)
                assertEquals(75, fnoParsed.first().lotSize)
                assertEquals(1800, fnoParsed.first().freezeQty)
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testIndstocksOrdersModifyCancelSmartOrderGttAndOrderTrades() {
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            var lastOrderPayload = JSONObject()
            var lastSmartPayload = JSONObject()
            var cancelSegment = ""
            var smartCancelId = ""

            server.createContext("/order/modify") { ex ->
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                assertEquals("ORD-501", req.optString("order_id"))
                assertEquals("DERIVATIVE", req.optString("segment"))
                assertEquals(150, req.optInt("qty"))
                assertEquals(128.5, req.optDouble("limit_price"), 0.01)
                val resp = """{"status":"success","data":{"order_id":"ORD-501","order_status":"MODIFIED"}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/order/cancel") { ex ->
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                cancelSegment = req.optString("segment")
                val resp = """{"status":"success","data":{"order_id":"ORD-501","order_status":"CANCELLED","reason":"User cancelled"}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/order/trades") { ex ->
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                assertEquals("ORD-501", req.optString("order_id"))
                val resp = """{"status":"success","data":[{"fill_id":"FILL-9","exch_order_id":"EX-9","quantity":75,"price":122.0,"trade_date":"2026-11-19"}]}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/order") { ex ->
                lastOrderPayload = JSONObject(ex.requestBody.bufferedReader().readText())
                val resp = """{"status":"success","data":{"order_id":"ORD-501","order_status":"CREATED"}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/smart/order/modify") { ex ->
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                assertEquals("GTT-901", req.optString("order_id"))
                assertEquals(110.0, req.optDouble("sl_trigger_price"), 0.01)
                assertEquals(175.0, req.optDouble("tgt_trigger_price"), 0.01)
                val resp = """{"status":"success","data":{"order_id":"GTT-901","order_status":"MODIFIED"}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/smart/order/cancel") { ex ->
                val req = JSONObject(ex.requestBody.bufferedReader().readText())
                smartCancelId = req.optString("order_id")
                val resp = """{"status":"success","data":{"order_id":"GTT-901","order_status":"CANCELLED","child_order_details":{"order_id":"CHL-9","order_status":"CANCELLED"}}}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }

            server.createContext("/smart/order") { ex ->
                lastSmartPayload = JSONObject(ex.requestBody.bufferedReader().readText())
                val resp = """
                    {
                      "status": "success",
                      "data": {
                        "order_data": [
                          {
                            "order_id": "GTT-901",
                            "order_status": "O-PENDING",
                            "child_order_details": {"order_id": "CHL-9", "order_status": "OCO-PENDING"}
                          }
                        ]
                      }
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
            server.start()

            try {
                val client = IndstocksApiClient(fastHttpClient(), "http://127.0.0.1:$port")

                // MARKET order must omit limit_price & sanitize reserved remark TV-TERMINAL
                val mktRes = client.placeOrder(
                    "tok-1",
                    IndstocksOrderRequest(
                        txnType = "BUY",
                        exchange = "NSE",
                        segment = "DERIVATIVE",
                        product = "MARGIN",
                        orderType = "MARKET",
                        securityId = "54321",
                        qty = 75,
                        price = 122.5,
                        remarks = "TV-TERMINAL"
                    )
                ).getOrThrow()
                assertEquals("ORD-501", mktRes.orderId)
                assertFalse("MARKET order must omit limit_price", lastOrderPayload.has("limit_price"))
                assertEquals("NSE", lastOrderPayload.optString("exchange"))
                assertEquals("MARGIN", lastOrderPayload.optString("product"))
                assertFalse("Must never send TV-TERMINAL", lastOrderPayload.optString("remarks").equals("TV-TERMINAL", true))

                val modRes = client.modifyOrder(
                    "tok-1",
                    IndstocksOrderModifyRequest(orderId = "ORD-501", segment = "DERIVATIVE", qty = 150, limitPrice = 128.5)
                ).getOrThrow()
                assertEquals("MODIFIED", modRes.status)

                val ordTrades = client.fetchOrderTrades("tok-1", "ORD-501", "DERIVATIVE").getOrThrow()
                assertEquals(1, ordTrades.size)
                assertEquals("FILL-9", ordTrades.first().tradeId)

                client.cancelOrder("tok-1", "ORD-501", "DERIVATIVE").getOrThrow()
                assertEquals("DERIVATIVE", cancelSegment)

                val smartRes = client.placeSmartOrder(
                    "tok-1",
                    IndstocksSmartOrderRequest(
                        txnType = "BUY",
                        exchange = "NSE",
                        segment = "DERIVATIVE",
                        product = "MARGIN",
                        orderType = "LIMIT",
                        securityId = "54321",
                        qty = 1950,
                        price = 125.0,
                        targetPrice = 165.0,
                        stopLossPrice = 105.0,
                        sliceOrder = true
                    )
                ).getOrThrow()
                assertEquals("GTT-901", smartRes.orderId)
                assertEquals("CHL-9", smartRes.childOrderId)
                assertEquals(105.0, lastSmartPayload.optDouble("sl_trigger_price"), 0.01)
                assertEquals(165.0, lastSmartPayload.optDouble("tgt_trigger_price"), 0.01)
                assertTrue(lastSmartPayload.optBoolean("slice_order"))

                val smartMod = client.modifySmartOrder(
                    "tok-1",
                    IndstocksSmartOrderModifyRequest(
                        orderId = "GTT-901",
                        segment = "DERIVATIVE",
                        qty = 150,
                        slTriggerPrice = 110.0,
                        tgtTriggerPrice = 175.0
                    )
                ).getOrThrow()
                assertEquals("MODIFIED", smartMod.status)

                val cancelMsg = client.cancelSmartOrder("tok-1", "GTT-901", "DERIVATIVE").getOrThrow()
                assertEquals("GTT-901", smartCancelId)
                assertTrue(cancelMsg.contains("CHL-9"))
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testIndstocksHoldingsPositionsLtpEnrichmentAndWebSocketFrameParsing() {
        runBlocking {
            // 1. WebSocket frame parsing (regular price + double-encoded order_update string)
            val tick = IndstocksWebSocketClient.parsePriceFrame(
                """{"mode":"ltp","instrument":"NIDX:26000","data":{"ltp":25342.75},"timestamp":1763501234000}"""
            )
            assertNotNull(tick)
            assertEquals("NIDX:26000", tick!!.instrument)
            assertEquals(25342.75, tick.ltp, 0.01)

            val doubleEncodedOrderUpdate = "\"{\\\"mode\\\":\\\"order_update\\\",\\\"data\\\":{\\\"order_id\\\":\\\"ORD-77\\\",\\\"entity_name\\\":\\\"NIFTY 25300 CE\\\",\\\"order_type\\\":\\\"BUY\\\",\\\"order_status\\\":\\\"S\\\",\\\"executed_price\\\":132.45,\\\"req_quantity\\\":75,\\\"requested_lot\\\":1,\\\"lot\\\":1}}\""
            val ordEvt = IndstocksWebSocketClient.parseOrderUpdateFrame(doubleEncodedOrderUpdate)
            assertNotNull(ordEvt)
            assertEquals("ORD-77", ordEvt!!.orderId)
            assertEquals("SUCCESS", ordEvt.statusLabel)
            assertEquals(132.45, ordEvt.executedPrice!!, 0.01)
            assertEquals("PARTIALLY FILLED - CANCELLED", IndstocksWebSocketClient.mapOrderStatusCode("PFC"))

            // 2. Holdings & Positions LTP enrichment inside NiftyMarketRepository.syncIndstocksPortfolioAndOrders
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port

            server.createContext("/user/profile") { ex ->
                val b = """{"data":{"ucc":"U1","first_name":"Test","last_name":"User","is_nse_fno_onboarded":true}}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/funds") { ex ->
                val b = """{"data":{"detailed_avl_balance":{"option_buy":150000.0}}}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/portfolio/holdings") { ex ->
                val b = """{"data":[{"symbol":"INFY","security_id":"1594","exchange":"NSE","total_qty":10,"avg_price":1800.0}]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/portfolio/positions") { ex ->
                val b = """{"data":[{"symbol":"NIFTY26NOV25300CE","security_id":"54321","segment":"DERIVATIVE","product":"MARGIN","net_qty":75,"avg_price":120.0,"realized_profit":0.0}]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/market/quotes/ltp") { ex ->
                val b = """{"status":"success","data":{"NSE_1594":{"live_price":1850.0},"NFO_54321":{"live_price":140.0}}}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/order-book") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/trade-book") { ex ->
                val b = """{"data":[]}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/market/instruments/expiries") { ex ->
                val b = """{"data":{"expiries":["2026-11-26"]}}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.createContext("/market/instruments/search") { ex ->
                val b = """{"data":{"instruments":[{"security_id":"51011","trading_symbol":"NIFTY26NOVFUT","lot_size":75}]}}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            server.start()

            try {
                val http = fastHttpClient()
                val ws = IndstocksWebSocketClient(http, "ws://127.0.0.1:1/ws/prices", "ws://127.0.0.1:1/ws/trades")
                val repo = NiftyMarketRepository(
                    liveMarketClient = LiveMarketDataClient(http, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                    nseMcpClient = NseMcpClient(http, "http://127.0.0.1:1", "http://127.0.0.1:1"),
                    indstocksClient = IndstocksApiClient(http, "http://127.0.0.1:$port"),
                    indstocksWsClient = ws
                )
                repo.saveAndConnectIndstocksToken("raw-tok-ltp")

                val state = repo.indstocksState.value
                assertEquals(1850.0, state.holdings.first().lastPrice, 0.01)
                assertEquals(500.0, state.holdings.first().pnl, 0.01)
                assertEquals(140.0, state.positions.first().lastPrice, 0.01)
                assertEquals(1500.0, state.positions.first().mtm, 0.01)
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testIndstocksBrokerSettingsCardDailyTokenAndTotpTabs() {
        var tokenSubmitted = ""
        var totpSubmitted = ""

        composeTestRule.setContent {
            MyApplicationTheme {
                IndstocksBrokerSettingsCard(
                    indstocksState = IndstocksBrokerState(),
                    onConnectWithToken = { tok, _ -> tokenSubmitted = tok },
                    onGenerateTokenViaTotp = { _, _, totp -> totpSubmitted = totp },
                    onSyncPortfolio = {},
                    onDisconnect = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("indstocks_token_input").performTextInput("daily-tok-abc")
        composeTestRule.onNodeWithTag("indstocks_connect_token_button").performClick()
        assertEquals("daily-tok-abc", tokenSubmitted)

        composeTestRule.onNodeWithTag("indstocks_auth_tab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("indstocks_client_id_input").performTextInput("cli-01")
        composeTestRule.onNodeWithTag("indstocks_mpin_input").performTextInput("1234")
        composeTestRule.onNodeWithTag("indstocks_totp_input").performTextInput("789012")
        composeTestRule.onNodeWithTag("indstocks_generate_totp_button").performClick()
        assertEquals("789012", totpSubmitted)
    }

    @Test
    fun testExecutionDeskThreeModeSwitcherAndPortfolioCard() {
        var selectedMode = ExecutionSafetyMode.PAPER_SIMULATION
        var acked = false
        val connectedBrokerState = IndstocksBrokerState(
            accessToken = "live-tok",
            status = IndstocksConnectionStatus.CONNECTED,
            profile = IndstocksUserProfile(ucc = "IND777", name = "Desk Trader", isDdpiActive = true),
            funds = IndstocksFundsSummary(availableMargin = 250000.0, usedMargin = 15000.0, collateral = 10000.0),
            holdings = listOf(IndstocksHolding(symbol = "INFY", exchange = "NSE", quantity = 5, avgPrice = 1800.0, lastPrice = 1850.0, pnl = 250.0)),
            positions = listOf(IndstocksPosition(symbol = "NIFTY26NOV25300CE", netQty = 25, product = "INTRADAY", mtm = 1200.0)),
            orderBook = listOf(IndstocksOrderRecord(orderId = "IND-9001", symbol = "NIFTY26NOV25300CE", txnType = "BUY", quantity = 25, price = 140.0, status = "OPEN")),
            executionMode = selectedMode,
            liveTradingRiskAcknowledged = acked
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ExecutionDeskScreen(
                    state = DeskState.createOfflinePlaceholderForUiTest(),
                    indstocksState = connectedBrokerState,
                    onSelectExecutionMode = { mode, riskAck ->
                        selectedMode = mode
                        acked = riskAck
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("execution_safety_mode_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execution_mode_LIVE_EXECUTION").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Enable Live INDstocks Orders?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_live_risk_button").performClick()

        assertEquals(ExecutionSafetyMode.LIVE_EXECUTION, selectedMode)
        assertTrue(acked)
        composeTestRule.onNodeWithTag("indstocks_portfolio_card").performScrollTo().assertIsDisplayed()
    }
}

private fun DeskState.Companion.createOfflinePlaceholderForUiTest(): DeskState {
    val fast = OkHttpClient.Builder().connectTimeout(50, TimeUnit.MILLISECONDS).build()
    return NiftyMarketRepository(
        liveMarketClient = LiveMarketDataClient(fast, "http://127.0.0.1:1", "http://127.0.0.1:1"),
        nseMcpClient = NseMcpClient(fast, "http://127.0.0.1:1", "http://127.0.0.1:1")
    ).deskState.value
}
