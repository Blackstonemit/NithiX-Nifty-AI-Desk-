package com.example.data

import com.example.engine.OptionsGreeksCalculator
import com.example.engine.TechnicalCalculator
import com.example.engine.agents.NiftyMultiAgentSystem
import com.example.model.AgentReport
import com.example.model.BreakoutSnapshot
import com.example.model.BuildupType
import com.example.model.Candle
import com.example.model.ExecutedOrder
import com.example.model.ExecutionSafetyMode
import com.example.model.FinalTradeRecommendation
import com.example.model.FuturesData
import com.example.model.IndstocksBrokerState
import com.example.model.IndstocksConnectionStatus
import com.example.model.IndstocksInstrumentRow
import com.example.model.IndstocksOptionLegSecurity
import com.example.model.IndstocksOrderModifyRequest
import com.example.model.IndstocksOrderRequest
import com.example.model.IndstocksSmartOrderModifyRequest
import com.example.model.IndstocksSmartOrderRequest
import com.example.model.IndstocksWebSocketStatus
import com.example.model.MarketBias
import com.example.model.McpAuthType
import com.example.model.McpConnectionStatus
import com.example.model.McpConnectorConfig
import com.example.model.McpConnectorsUiState
import com.example.model.McpDiscoveredTool
import com.example.model.McpRpcLog
import com.example.model.McpTransportType
import com.example.model.NiftySpotData
import com.example.model.NseBhavcopyRecord
import com.example.model.NseMcpState
import com.example.model.OptionStrike
import com.example.model.OptionsChainSnapshot
import com.example.model.OrderBlock
import com.example.model.PriceZone
import com.example.model.SignalType
import com.example.model.SmartMoneySnapshot
import com.example.model.TechnicalAnalysisSnapshot
import com.example.model.Timeframe
import com.example.model.TradingStyle
import com.example.model.TradingWorkflow
import com.example.network.IndstocksApiClient
import com.example.network.IndstocksWebSocketClient
import com.example.network.LiveIndexQuote
import com.example.network.LiveMarketDataClient
import com.example.network.LiveVixQuote
import com.example.network.McpOAuthClient
import com.example.network.McpPluginClient
import com.example.network.NseMcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class DeskState(
    val spot: NiftySpotData,
    val futures: FuturesData,
    val optionsChain: OptionsChainSnapshot,
    val technicals: TechnicalAnalysisSnapshot,
    val smartMoney: SmartMoneySnapshot,
    val breakout: BreakoutSnapshot,
    val agentReports: List<AgentReport>,
    val finalRecommendation: FinalTradeRecommendation,
    val candles: List<Candle>,
    val selectedTimeframe: Timeframe = Timeframe.M15,
    val selectedWorkflow: TradingWorkflow = TradingWorkflow.MARKET_HOURS,
    val selectedTradingStyle: TradingStyle = TradingStyle.INTRADAY_MOMENTUM,
    val selectedScenarioName: String = SCENARIO_LIVE_FEED,
    val isLiveStreaming: Boolean = false,
    val activeOrders: List<ExecutedOrder> = emptyList(),
    val historicalAuditLogs: List<FinalTradeRecommendation> = emptyList(),
    val availableCapital: Double = 200000.0,
    val totalRealizedPnl: Double = 8450.0,
    val sessionStatus: MarketSessionStatus = MarketHoursEngine.getMarketSessionStatus(false),
    val isLiveMarketData: Boolean = false,
    val dataSourceLabel: String = "Cached NSE Reference",
    val lastSyncLabel: String = "Pending live sync",
    val isRefreshing: Boolean = false,
    val liveNetworkError: String? = null
) {
    companion object {
        const val SCENARIO_LIVE_FEED = "Live Market Feed"
    }
}

class NiftyMarketRepository(
    private val liveMarketClient: LiveMarketDataClient = LiveMarketDataClient(),
    private val nseMcpClient: NseMcpClient = NseMcpClient(),
    private val mcpOAuthClient: McpOAuthClient = McpOAuthClient(),
    private val mcpPluginClient: McpPluginClient = McpPluginClient(oauthClient = mcpOAuthClient),
    private val connectorStore: McpConnectorPersistence = InMemoryMcpConnectorStore(),
    private val indstocksClient: IndstocksApiClient = IndstocksApiClient(),
    private val indstocksStore: IndstocksCredentialPersistence = InMemoryIndstocksCredentialStore(),
    private val indstocksWsClient: IndstocksWebSocketClient = IndstocksWebSocketClient()
) {

    private val multiAgentSystem = NiftyMultiAgentSystem()

    private val _deskState = MutableStateFlow(createInitialDeskState(DeskState.SCENARIO_LIVE_FEED))
    val deskState: StateFlow<DeskState> = _deskState.asStateFlow()

    private val _nseMcpState = MutableStateFlow(
        NseMcpState(
            activeTools = nseMcpClient.defaultTools,
            isConnected = false,
            isFallbackData = true,
            latestBhavcopy = NseBhavcopyRecord(
                symbol = "NIFTY 50",
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
        )
    )
    val nseMcpState: StateFlow<NseMcpState> = _nseMcpState.asStateFlow()

    private val _connectorsState = MutableStateFlow(
        McpConnectorsUiState(connectors = buildInitialConnectorsList())
    )
    val connectorsState: StateFlow<McpConnectorsUiState> = _connectorsState.asStateFlow()

    private val _indstocksState = MutableStateFlow(
        IndstocksBrokerState(
            accessToken = indstocksStore.loadAccessToken(),
            clientId = indstocksStore.loadClientId(),
            status = IndstocksConnectionStatus.DISCONNECTED,
            executionMode = indstocksStore.loadExecutionMode(),
            liveTradingRiskAcknowledged = indstocksStore.loadLiveRiskAcknowledged()
        )
    )
    val indstocksState: StateFlow<IndstocksBrokerState> = _indstocksState.asStateFlow()

    private var cachedFnoInstruments: List<IndstocksInstrumentRow> = emptyList()
    private var cachedNiftyFutures: List<IndstocksInstrumentRow> = emptyList()
    private var cachedOptionLegSecurities: Map<String, IndstocksOptionLegSecurity> = emptyMap()
    private var wsCollectorJob: Job? = null
    private var tickCounter = 0

    fun startIndstocksStreams(scope: CoroutineScope) {
        wsCollectorJob?.cancel()
        wsCollectorJob = scope.launch {
            launch {
                indstocksWsClient.priceStatus.collect { wsStatus ->
                    _indstocksState.value = _indstocksState.value.copy(wsPriceStatus = wsStatus)
                }
            }
            launch {
                indstocksWsClient.orderStatus.collect { wsStatus ->
                    _indstocksState.value = _indstocksState.value.copy(wsOrderStatus = wsStatus)
                }
            }
            launch {
                indstocksWsClient.priceTicks.collect { tick ->
                    if (tick.ltp <= 0.0) return@collect
                    val futSecId = cachedNiftyFutures.firstOrNull()?.securityId
                    val isSpotOrTracked = tick.instrument.isBlank() ||
                        tick.instrument.contains("26000") ||
                        tick.instrument.contains("40000001") ||
                        (futSecId != null && tick.instrument.endsWith(":$futSecId"))
                    if (isSpotOrTracked && _deskState.value.selectedScenarioName == DeskState.SCENARIO_LIVE_FEED) {
                        val current = _deskState.value
                        val prevClose = current.spot.prevClose.takeIf { it > 0.0 } ?: tick.ltp
                        val change = tick.ltp - prevClose
                        val changePct = (change / prevClose) * 100.0
                        val updatedSpot = current.spot.copy(
                            spotPrice = tick.ltp,
                            change = change,
                            changePct = changePct,
                            dayHigh = max(current.spot.dayHigh, tick.ltp),
                            dayLow = min(current.spot.dayLow, tick.ltp)
                        )
                        val updatedCandles = current.candles.toMutableList()
                        if (updatedCandles.isNotEmpty()) {
                            val last = updatedCandles.last()
                            updatedCandles[updatedCandles.size - 1] = last.copy(
                                close = tick.ltp,
                                high = max(last.high, tick.ltp),
                                low = min(last.low, tick.ltp)
                            )
                        }
                        _deskState.value = current.copy(
                            spot = updatedSpot,
                            candles = updatedCandles,
                            isLiveMarketData = true,
                            lastSyncLabel = "WS Live ${formatCurrentIstShort()}"
                        )
                    }
                }
            }
            launch {
                indstocksWsClient.orderUpdates.collect { evt ->
                    val latest = _indstocksState.value
                    val updatedOrders = latest.orderBook.map { rec ->
                        if (rec.orderId == evt.orderId) {
                            rec.copy(
                                status = evt.statusLabel,
                                tradedPrice = evt.executedPrice ?: rec.tradedPrice,
                                statusMessage = evt.errorMessage.ifBlank { rec.statusMessage }
                            )
                        } else rec
                    }
                    _indstocksState.value = latest.copy(
                        orderBook = updatedOrders,
                        lastOrderUpdateEvent = evt,
                        statusNotice = "WS Order #${evt.orderId}: ${evt.statusLabel}" +
                            (evt.executedPrice?.let { " @ ₹${String.format(Locale.US, "%.2f", it)}" } ?: "")
                    )
                    launch { syncIndstocksPortfolioAndOrders() }
                }
            }
        }
    }

    fun stopIndstocksStreams() {
        wsCollectorJob?.cancel()
        wsCollectorJob = null
        indstocksWsClient.disconnect()
    }

    private fun buildInitialConnectorsList(): List<McpConnectorConfig> {
        val builtInStates = connectorStore.loadBuiltInEnabledStates()
        val nseTools = nseMcpClient.defaultTools.map {
            McpDiscoveredTool(
                name = it.name,
                description = it.description,
                parameterProperties = mapOf("symbol" to "string — ${it.parametersDescription}")
            )
        }
        val builtIns = listOf(
            McpConnectorConfig(
                id = BUILTIN_NSE_BHAVCOPY_ID,
                name = "NSE Bhavcopy MCP",
                url = nseMcpClient.bhavcopyEndpoint,
                transportType = McpTransportType.STREAMABLE_HTTP,
                authType = McpAuthType.NONE,
                isBuiltIn = true,
                isEnabled = builtInStates[BUILTIN_NSE_BHAVCOPY_ID] ?: true,
                serverInfoName = "NSE India Bhavcopy Server",
                tools = nseTools
            ),
            McpConnectorConfig(
                id = BUILTIN_NSE_CM_ID,
                name = "NSE CM Market MCP",
                url = nseMcpClient.cmMarketEndpoint,
                transportType = McpTransportType.STREAMABLE_HTTP,
                authType = McpAuthType.NONE,
                isBuiltIn = true,
                isEnabled = builtInStates[BUILTIN_NSE_CM_ID] ?: true,
                serverInfoName = "NSE India Capital Market Server",
                tools = nseTools.takeLast(2)
            )
        )
        val customs = connectorStore.loadCustomConnectors()
        return builtIns + customs
    }

    fun validateConnectorUrl(rawUrl: String): String? = mcpPluginClient.validateUrl(rawUrl)

    fun addCustomConnector(
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String> = emptyMap(),
        oauthClientId: String = ""
    ): McpConnectorConfig {
        val newConnector = McpConnectorConfig(
            id = "mcp-${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            url = url.trim(),
            transportType = transportType,
            authType = authType,
            authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
            authSecret = authSecret,
            oauthClientId = oauthClientId.trim(),
            customHeaders = customHeaders,
            isBuiltIn = false,
            isEnabled = true,
            status = McpConnectionStatus.DISCONNECTED
        )
        val updatedList = _connectorsState.value.connectors + newConnector
        _connectorsState.value = _connectorsState.value.copy(connectors = updatedList)
        connectorStore.saveCustomConnectors(updatedList)
        return newConnector
    }

    fun updateCustomConnector(
        connectorId: String,
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String> = emptyMap(),
        oauthClientId: String = ""
    ): McpConnectorConfig? {
        var updatedItem: McpConnectorConfig? = null
        val updatedList = _connectorsState.value.connectors.map { item ->
            if (item.id == connectorId && !item.isBuiltIn) {
                val edited = item.copy(
                    name = name.trim(),
                    url = url.trim(),
                    transportType = transportType,
                    authType = authType,
                    authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
                    authSecret = authSecret,
                    oauthClientId = oauthClientId.trim(),
                    customHeaders = customHeaders,
                    status = McpConnectionStatus.DISCONNECTED,
                    sessionId = null,
                    lastError = null
                )
                updatedItem = edited
                edited
            } else {
                item
            }
        }
        _connectorsState.value = _connectorsState.value.copy(connectors = updatedList)
        connectorStore.saveCustomConnectors(updatedList)
        return updatedItem
    }

    fun removeCustomConnector(connectorId: String) {
        val updatedList = _connectorsState.value.connectors.filterNot { it.id == connectorId && !it.isBuiltIn }
        val updatedLogs = _connectorsState.value.rpcLogsByConnector - connectorId
        val updatedResults = _connectorsState.value.lastToolResultsByConnector - connectorId
        _connectorsState.value = _connectorsState.value.copy(
            connectors = updatedList,
            rpcLogsByConnector = updatedLogs,
            lastToolResultsByConnector = updatedResults
        )
        connectorStore.saveCustomConnectors(updatedList)
    }

    fun toggleConnectorEnabled(connectorId: String) {
        var toggledBuiltIn: Boolean? = null
        val updatedList = _connectorsState.value.connectors.map { item ->
            if (item.id == connectorId) {
                val nextEnabled = !item.isEnabled
                if (item.isBuiltIn) toggledBuiltIn = nextEnabled
                item.copy(
                    isEnabled = nextEnabled,
                    status = if (!nextEnabled) McpConnectionStatus.DISCONNECTED else item.status
                )
            } else {
                item
            }
        }
        _connectorsState.value = _connectorsState.value.copy(connectors = updatedList)
        if (toggledBuiltIn != null) {
            connectorStore.saveBuiltInEnabledState(connectorId, toggledBuiltIn!!)
        } else {
            connectorStore.saveCustomConnectors(updatedList)
        }
    }

    suspend fun connectAndDiscoverConnector(connectorId: String) {
        val target = _connectorsState.value.connectors.firstOrNull { it.id == connectorId } ?: return
        _connectorsState.value = _connectorsState.value.copy(
            connectors = _connectorsState.value.connectors.map {
                if (it.id == connectorId) {
                    it.copy(
                        isEnabled = true,
                        status = McpConnectionStatus.CONNECTING,
                        lastError = null
                    )
                } else it
            }
        )

        val result = mcpPluginClient.connectAndDiscover(target)
        val currentState = _connectorsState.value
        val existingLogs = currentState.rpcLogsByConnector[connectorId].orEmpty()
        val combinedLogs = (result.logs + existingLogs).take(40)

        var shouldPersistCustom = false
        val updatedList = currentState.connectors.map { item ->
            if (item.id == connectorId) {
                if (result.isSuccess) {
                    if (result.refreshedAccessToken != null) shouldPersistCustom = true
                    item.copy(
                        isEnabled = true,
                        status = McpConnectionStatus.CONNECTED,
                        authType = if (result.refreshedAccessToken != null) McpAuthType.OAUTH_PKCE else item.authType,
                        authSecret = result.refreshedAccessToken ?: item.authSecret,
                        oauthRefreshToken = result.refreshedRefreshToken ?: item.oauthRefreshToken,
                        oauthMetadata = result.oauthMetadata ?: item.oauthMetadata,
                        serverInfoName = result.serverInfoName,
                        serverInfoVersion = result.serverInfoVersion,
                        protocolVersion = result.protocolVersion,
                        sessionId = result.sessionId,
                        lastLatencyMs = result.latencyMs,
                        lastError = null,
                        lastConnectedTimestamp = System.currentTimeMillis(),
                        tools = result.tools.ifEmpty { item.tools },
                        resources = result.resources,
                        prompts = result.prompts
                    )
                } else {
                    item.copy(
                        status = if (result.authRequired) McpConnectionStatus.AUTH_REQUIRED else McpConnectionStatus.ERROR,
                        oauthMetadata = result.oauthMetadata ?: item.oauthMetadata,
                        lastLatencyMs = result.latencyMs,
                        lastError = result.errorMessage ?: "Connection failed"
                    )
                }
            } else {
                item
            }
        }
        _connectorsState.value = currentState.copy(
            connectors = updatedList,
            rpcLogsByConnector = currentState.rpcLogsByConnector + (connectorId to combinedLogs)
        )
        if (shouldPersistCustom) {
            connectorStore.saveCustomConnectors(updatedList)
        }
    }

    suspend fun discoverOAuthForConnector(connectorId: String) {
        val target = _connectorsState.value.connectors.firstOrNull { it.id == connectorId } ?: return
        _connectorsState.value = _connectorsState.value.copy(
            oauthBusyConnectorId = connectorId,
            oauthStatusMessage = "Discovering OAuth 2.1 endpoints..."
        )
        val meta = mcpOAuthClient.discoverOAuthMetadata(target.url)
        val updatedList = _connectorsState.value.connectors.map {
            if (it.id == connectorId) it.copy(oauthMetadata = meta ?: it.oauthMetadata) else it
        }
        _connectorsState.value = _connectorsState.value.copy(
            connectors = updatedList,
            oauthBusyConnectorId = null,
            oauthStatusMessage = if (meta != null) {
                "OAuth endpoints ready (${meta.issuer ?: meta.authorizationEndpoint})"
            } else {
                "OAuth metadata not found on server; you can still paste a Bearer token or enter Client ID."
            }
        )
    }

    suspend fun prepareOAuthAuthorization(
        connectorId: String,
        explicitClientId: String,
        redirectUri: String
    ): com.example.model.McpPendingOAuthFlow? {
        val target = _connectorsState.value.connectors.firstOrNull { it.id == connectorId } ?: return null
        _connectorsState.value = _connectorsState.value.copy(
            oauthBusyConnectorId = connectorId,
            oauthStatusMessage = "Preparing OAuth 2.1 PKCE session..."
        )
        val prep = mcpOAuthClient.prepareOAuthFlow(
            connectorId = target.id,
            connectorName = target.name,
            serverUrl = target.url,
            existingMetadata = target.oauthMetadata,
            explicitClientId = explicitClientId.ifBlank { target.oauthClientId },
            redirectUri = redirectUri
        )
        val updatedList = _connectorsState.value.connectors.map {
            if (it.id == connectorId) {
                it.copy(
                    oauthMetadata = prep.updatedMetadata ?: it.oauthMetadata,
                    oauthClientId = prep.pendingFlow?.clientId ?: explicitClientId.trim().ifBlank { it.oauthClientId }
                )
            } else it
        }
        _connectorsState.value = _connectorsState.value.copy(
            connectors = updatedList,
            activeOAuthFlow = prep.pendingFlow,
            oauthBusyConnectorId = null,
            oauthStatusMessage = if (prep.isSuccess) {
                "Opening browser sign-in for ${target.name}..."
            } else {
                prep.errorMessage
            }
        )
        connectorStore.saveCustomConnectors(updatedList)
        return prep.pendingFlow
    }

    suspend fun completeOAuthWithCallbackOrCode(codeOrCallbackUrl: String): Boolean {
        val flow = _connectorsState.value.activeOAuthFlow ?: return false
        _connectorsState.value = _connectorsState.value.copy(
            oauthBusyConnectorId = flow.connectorId,
            oauthStatusMessage = "Exchanging authorization code for OAuth token..."
        )
        val tokenRes = mcpOAuthClient.exchangeCodeForToken(flow, codeOrCallbackUrl)
        if (!tokenRes.isSuccess || tokenRes.accessToken.isNullOrBlank()) {
            _connectorsState.value = _connectorsState.value.copy(
                oauthBusyConnectorId = null,
                oauthStatusMessage = tokenRes.errorMessage ?: "OAuth token exchange failed"
            )
            return false
        }

        val updatedList = _connectorsState.value.connectors.map {
            if (it.id == flow.connectorId) {
                it.copy(
                    authType = McpAuthType.OAUTH_PKCE,
                    authSecret = tokenRes.accessToken,
                    oauthClientId = flow.clientId,
                    oauthRefreshToken = tokenRes.refreshToken ?: it.oauthRefreshToken,
                    lastError = null
                )
            } else it
        }
        _connectorsState.value = _connectorsState.value.copy(
            connectors = updatedList,
            activeOAuthFlow = null,
            oauthBusyConnectorId = null,
            oauthStatusMessage = "OAuth token saved! Connecting to ${flow.connectorName}..."
        )
        connectorStore.saveCustomConnectors(updatedList)
        connectAndDiscoverConnector(flow.connectorId)
        return true
    }

    suspend fun applyManualBearerToken(connectorId: String, token: String) {
        val cleanToken = token.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        if (cleanToken.isBlank()) return
        val updatedList = _connectorsState.value.connectors.map {
            if (it.id == connectorId) {
                it.copy(
                    authType = McpAuthType.BEARER_TOKEN,
                    authSecret = cleanToken,
                    lastError = null
                )
            } else it
        }
        _connectorsState.value = _connectorsState.value.copy(
            connectors = updatedList,
            activeOAuthFlow = null,
            oauthBusyConnectorId = null,
            oauthStatusMessage = null
        )
        connectorStore.saveCustomConnectors(updatedList)
        connectAndDiscoverConnector(connectorId)
    }

    fun clearActiveOAuthFlow() {
        _connectorsState.value = _connectorsState.value.copy(
            activeOAuthFlow = null,
            oauthBusyConnectorId = null,
            oauthStatusMessage = null
        )
    }

    suspend fun executeConnectorTool(
        connectorId: String,
        toolName: String,
        argumentsJson: String
    ) {
        val target = _connectorsState.value.connectors.firstOrNull { it.id == connectorId } ?: return
        _connectorsState.value = _connectorsState.value.copy(executingToolConnectorId = connectorId)
        val outcome = mcpPluginClient.callTool(target, toolName, argumentsJson)
        val currentState = _connectorsState.value
        val existingLogs = currentState.rpcLogsByConnector[connectorId].orEmpty()
        val combinedLogs = (outcome.logs + existingLogs).take(40)

        val updatedConnectors = currentState.connectors.map { item ->
            if (item.id == connectorId) {
                item.copy(
                    status = if (outcome.executionResult.isSuccess) McpConnectionStatus.CONNECTED else item.status,
                    lastLatencyMs = outcome.executionResult.latencyMs
                )
            } else item
        }

        _connectorsState.value = currentState.copy(
            connectors = updatedConnectors,
            rpcLogsByConnector = currentState.rpcLogsByConnector + (connectorId to combinedLogs),
            lastToolResultsByConnector = currentState.lastToolResultsByConnector + (connectorId to outcome.executionResult),
            executingToolConnectorId = null
        )
    }

    companion object {
        const val BUILTIN_NSE_BHAVCOPY_ID = "builtin-nse-bhavcopy"
        const val BUILTIN_NSE_CM_ID = "builtin-nse-cm-market"
    }

    suspend fun generateIndstocksTokenViaTotp(
        clientId: String,
        mpin: String,
        totp: String
    ) {
        val cleanClientId = clientId.trim()
        val now = System.currentTimeMillis()
        val cooldownUntil = _indstocksState.value.totpCooldownUntilMs
        if (cooldownUntil > now) {
            val waitSec = ((cooldownUntil - now) / 1000L).coerceAtLeast(1L)
            _indstocksState.value = _indstocksState.value.copy(
                lastError = "Rate-limit protection: wait ${waitSec}s before generating another TOTP token."
            )
            return
        }

        indstocksStore.saveClientId(cleanClientId)
        _indstocksState.value = _indstocksState.value.copy(
            clientId = cleanClientId,
            isBusy = true,
            status = IndstocksConnectionStatus.AUTHENTICATING,
            lastError = null,
            statusNotice = "Generating 24h access token via INDstocks TOTP..."
        )

        val tokenResult = indstocksClient.generateToken(cleanClientId, mpin, totp)
        val nextCooldown = System.currentTimeMillis() + 60_000L
        if (tokenResult.isSuccess) {
            val newToken = tokenResult.getOrThrow()
            _indstocksState.value = _indstocksState.value.copy(totpCooldownUntilMs = nextCooldown)
            saveAndConnectIndstocksToken(newToken, cleanClientId)
        } else {
            _indstocksState.value = _indstocksState.value.copy(
                isBusy = false,
                status = IndstocksConnectionStatus.ERROR,
                totpCooldownUntilMs = nextCooldown,
                statusNotice = null,
                lastError = tokenResult.exceptionOrNull()?.message ?: "TOTP token generation failed"
            )
        }
    }

    suspend fun saveAndConnectIndstocksToken(
        rawToken: String,
        optionalClientId: String? = null
    ) {
        val cleanToken = IndstocksApiClient.sanitizeToken(rawToken)
        if (cleanToken.isBlank()) {
            _indstocksState.value = _indstocksState.value.copy(
                lastError = "Paste a valid INDstocks daily access token."
            )
            return
        }

        optionalClientId?.trim()?.let { cid ->
            if (cid.isNotBlank()) indstocksStore.saveClientId(cid)
        }
        val effectiveClientId = optionalClientId?.trim()?.takeIf { it.isNotBlank() }
            ?: _indstocksState.value.clientId

        val startMs = System.currentTimeMillis()
        _indstocksState.value = _indstocksState.value.copy(
            accessToken = cleanToken,
            clientId = effectiveClientId,
            isBusy = true,
            status = IndstocksConnectionStatus.AUTHENTICATING,
            lastError = null,
            statusNotice = "Verifying INDstocks profile & portfolio..."
        )

        val profileRes = indstocksClient.fetchUserProfile(cleanToken)
        if (profileRes.isFailure) {
            val errMsg = profileRes.exceptionOrNull()?.message ?: "Profile verification failed"
            val expired = errMsg.contains("401") || errMsg.contains("403")
            _indstocksState.value = _indstocksState.value.copy(
                isBusy = false,
                status = if (expired) IndstocksConnectionStatus.TOKEN_EXPIRED else IndstocksConnectionStatus.ERROR,
                statusNotice = null,
                lastError = errMsg
            )
            return
        }

        indstocksStore.saveAccessToken(cleanToken)
        val profile = profileRes.getOrThrow()
        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        _indstocksState.value = _indstocksState.value.copy(
            accessToken = cleanToken,
            clientId = effectiveClientId,
            status = IndstocksConnectionStatus.CONNECTED,
            profile = profile,
            isBusy = false,
            lastError = null,
            lastLatencyMs = latency,
            lastSyncedAtLabel = formatCurrentIstShort(),
            statusNotice = "INDstocks connected (${profile.ucc.ifBlank { profile.name }}) — syncing live feed & portfolio"
        )

        syncIndstocksPortfolioAndOrders()
        refreshLiveMarketData(_deskState.value.selectedTimeframe)

        // Start INDstocks WebSocket feeds if UI/ViewModel collector is active or user connected manually
        val wsInstruments = buildList {
            add("NIDX:26000")
            cachedNiftyFutures.firstOrNull()?.securityId?.let { add("NFO:$it") }
        }
        indstocksWsClient.connect(cleanToken, wsInstruments)
    }

    suspend fun syncIndstocksPortfolioAndOrders() = coroutineScope {
        val current = _indstocksState.value
        val token = current.accessToken
        if (token.isBlank()) return@coroutineScope

        val startMs = System.currentTimeMillis()
        _indstocksState.value = current.copy(isBusy = true)

        val fundsDeferred = async { indstocksClient.fetchFunds(token) }
        val holdingsDeferred = async { indstocksClient.fetchHoldings(token) }
        val positionsDeferred = async { indstocksClient.fetchPositions(token) }
        val ordersDeferred = async { indstocksClient.fetchOrderBook(token) }
        val drvTradesDeferred = async { indstocksClient.fetchTradeBook(token, "DERIVATIVE") }
        val eqTradesDeferred = async { indstocksClient.fetchTradeBook(token, "EQUITY") }
        val expiriesDeferred = async { indstocksClient.fetchExpiries(token, "NIFTY", "DERIVATIVE") }
        val futContractsDeferred = async {
            indstocksClient.searchDerivativeContracts(
                accessToken = token,
                underlying = "NIFTY",
                segment = "DERIVATIVE",
                instrumentType = "FUTIDX",
                pageSize = 10
            )
        }

        val funds = fundsDeferred.await().getOrNull()
        val rawHoldings = holdingsDeferred.await().getOrNull()
        val rawPositions = positionsDeferred.await().getOrNull()
        val orders = ordersDeferred.await().getOrNull()
        val drvTrades = drvTradesDeferred.await().getOrNull().orEmpty()
        val eqTrades = eqTradesDeferred.await().getOrNull().orEmpty()
        val combinedTrades = (drvTrades + eqTrades).distinctBy { "${it.tradeId}_${it.orderId}_${it.tradeSerialNo}" }
        val expiries = expiriesDeferred.await().getOrNull().orEmpty()
        val futContracts = futContractsDeferred.await().getOrNull().orEmpty()
        if (futContracts.isNotEmpty()) {
            cachedNiftyFutures = futContracts
        }

        // Enrich Holdings & Open Positions with live LTP via /market/quotes/ltp (best-effort)
        val scripCodesToPrice = buildSet {
            rawHoldings.orEmpty().forEach { h ->
                if (h.securityId.isNotBlank()) {
                    add("${h.exchange.uppercase()}_${h.securityId}")
                }
            }
            rawPositions.orEmpty().forEach { p ->
                if (p.securityId.isNotBlank() && p.netQty != 0) {
                    val prefix = if (p.segment.equals("DERIVATIVE", true) || p.segment.equals("FNO", true)) "NFO" else p.exchange.uppercase()
                    add("${prefix}_${p.securityId}")
                }
            }
        }.toList()

        val ltpMap = if (scripCodesToPrice.isNotEmpty()) {
            indstocksClient.fetchLtpQuotes(token, scripCodesToPrice).getOrNull().orEmpty()
        } else emptyMap()

        val enrichedHoldings = rawHoldings?.map { h ->
            val ltp = ltpMap["${h.exchange.uppercase()}_${h.securityId}"]
            if (ltp != null && ltp > 0.0) {
                val pnl = (ltp - h.avgPrice) * h.quantity
                val pnlPct = if (h.avgPrice > 0.0) ((ltp - h.avgPrice) / h.avgPrice) * 100.0 else 0.0
                h.copy(lastPrice = ltp, pnl = pnl, pnlPct = pnlPct)
            } else h
        }

        val enrichedPositions = rawPositions?.map { p ->
            val prefix = if (p.segment.equals("DERIVATIVE", true) || p.segment.equals("FNO", true)) "NFO" else p.exchange.uppercase()
            val ltp = ltpMap["${prefix}_${p.securityId}"]
            if (ltp != null && ltp > 0.0 && p.netQty != 0) {
                val unrealized = (ltp - p.avgPrice) * p.netQty
                p.copy(lastPrice = ltp, unrealizedPnl = unrealized, mtm = p.realizedPnl + unrealized)
            } else p
        }

        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val latest = _indstocksState.value
        val activeLot = futContracts.firstOrNull()?.lotSize ?: latest.activeContractLotSize
        val activeFreeze = futContracts.firstOrNull()?.freezeQty ?: latest.activeContractFreezeQty

        _indstocksState.value = latest.copy(
            funds = funds ?: latest.funds,
            holdings = enrichedHoldings ?: latest.holdings,
            positions = enrichedPositions ?: latest.positions,
            orderBook = orders ?: latest.orderBook,
            tradeBook = if (drvTradesDeferred.await().isSuccess || eqTradesDeferred.await().isSuccess) combinedTrades else latest.tradeBook,
            availableExpiries = expiries.ifEmpty { latest.availableExpiries },
            selectedExpiry = latest.selectedExpiry ?: expiries.firstOrNull(),
            activeContractLotSize = activeLot,
            activeContractFreezeQty = activeFreeze,
            isBusy = false,
            lastLatencyMs = latency,
            lastSyncedAtLabel = formatCurrentIstShort()
        )

        if (funds != null && funds.availableMargin > 0.0) {
            _deskState.value = _deskState.value.copy(availableCapital = funds.availableMargin)
        }
    }

    fun disconnectIndstocks() {
        indstocksStore.clearCredentials()
        indstocksWsClient.disconnect()
        cachedFnoInstruments = emptyList()
        cachedNiftyFutures = emptyList()
        cachedOptionLegSecurities = emptyMap()
        _indstocksState.value = IndstocksBrokerState(
            clientId = indstocksStore.loadClientId(),
            status = IndstocksConnectionStatus.DISCONNECTED,
            wsPriceStatus = IndstocksWebSocketStatus.DISCONNECTED,
            wsOrderStatus = IndstocksWebSocketStatus.DISCONNECTED,
            executionMode = ExecutionSafetyMode.PAPER_SIMULATION,
            liveTradingRiskAcknowledged = false,
            lastSyncedAtLabel = "Disconnected"
        )
    }

    fun setExecutionSafetyMode(
        mode: ExecutionSafetyMode,
        acknowledgeLiveRisk: Boolean = _indstocksState.value.liveTradingRiskAcknowledged
    ) {
        val effectiveMode = if (mode == ExecutionSafetyMode.LIVE_EXECUTION && !acknowledgeLiveRisk) {
            ExecutionSafetyMode.READ_ONLY_BROKER
        } else {
            mode
        }
        indstocksStore.saveExecutionMode(effectiveMode)
        indstocksStore.saveLiveRiskAcknowledged(acknowledgeLiveRisk)
        _indstocksState.value = _indstocksState.value.copy(
            executionMode = effectiveMode,
            liveTradingRiskAcknowledged = acknowledgeLiveRisk,
            statusNotice = "Execution mode: ${effectiveMode.title}"
        )
    }

    suspend fun selectIndstocksExpiry(expiry: String) {
        _indstocksState.value = _indstocksState.value.copy(selectedExpiry = expiry)
        refreshLiveMarketData(_deskState.value.selectedTimeframe)
    }

    suspend fun previewIndstocksMarginForRecommendation(
        recommendation: FinalTradeRecommendation,
        lots: Int
    ) {
        val state = _indstocksState.value
        val token = state.accessToken
        if (token.isBlank()) return

        val estPrice = estimateEntryPriceForRecommendation(recommendation)
        val lotSize = state.activeContractLotSize.coerceAtLeast(1)
        val qty = lots * lotSize
        val resolved = resolveSecurityIdAndSymbolForRecommendation(recommendation)
        val secId = resolved?.first ?: IndstocksApiClient.NIFTY_UNDERLYING_SCRIP
        val marginRes = indstocksClient.calculateMargin(
            accessToken = token,
            securityId = secId,
            exchange = "NSE",
            segment = "DERIVATIVE",
            txnType = "BUY",
            product = "MARGIN",
            qty = qty,
            price = estPrice
        )
        marginRes.onSuccess { preview ->
            _indstocksState.value = _indstocksState.value.copy(marginPreview = preview)
        }.onFailure { err ->
            _indstocksState.value = _indstocksState.value.copy(
                marginPreview = null,
                statusNotice = "Margin preview unavailable: ${err.message}"
            )
        }
    }

    suspend fun submitIndstocksLiveOrder(
        recommendation: FinalTradeRecommendation,
        lots: Int,
        useSmartGtt: Boolean = false,
        orderType: String = "LIMIT",
        overridePrice: Double? = null
    ): Result<String> {
        val state = _indstocksState.value
        if (state.executionMode != ExecutionSafetyMode.LIVE_EXECUTION || !state.liveTradingRiskAcknowledged) {
            return Result.failure(IllegalStateException("Enable Live + Smart GTT mode and acknowledge risk before submitting live orders."))
        }
        if (state.accessToken.isBlank()) {
            return Result.failure(IllegalStateException("INDstocks Access Token not connected."))
        }

        val resolved = resolveSecurityIdAndSymbolForRecommendation(recommendation)
            ?: return Result.failure(
                IllegalStateException("Could not resolve DERIVATIVE security_id for ${recommendation.instrument} via INDstocks search/instruments.")
            )
        val (securityId, tradingSymbol) = resolved

        val lotSize = state.activeContractLotSize.coerceAtLeast(1)
        val qty = lots * lotSize
        val price = (overridePrice ?: estimateEntryPriceForRecommendation(recommendation)).coerceAtLeast(1.0)
        val targetPrice = price + max(25.0, price * 0.28)
        val slPrice = max(2.0, price - max(14.0, price * 0.16))
        val needsSlice = qty > state.activeContractFreezeQty

        _indstocksState.value = _indstocksState.value.copy(isBusy = true, lastError = null)
        val res = if (useSmartGtt) {
            indstocksClient.placeSmartOrder(
                accessToken = state.accessToken,
                req = IndstocksSmartOrderRequest(
                    txnType = "BUY",
                    exchange = "NSE",
                    segment = "DERIVATIVE",
                    product = "MARGIN",
                    orderType = orderType,
                    validity = "DAY",
                    securityId = securityId,
                    qty = qty,
                    price = price,
                    targetPrice = targetPrice,
                    stopLossPrice = slPrice,
                    sliceOrder = needsSlice
                )
            )
        } else {
            indstocksClient.placeOrder(
                accessToken = state.accessToken,
                req = IndstocksOrderRequest(
                    txnType = "BUY",
                    exchange = "NSE",
                    segment = "DERIVATIVE",
                    product = "MARGIN",
                    orderType = orderType,
                    validity = "DAY",
                    securityId = securityId,
                    qty = qty,
                    price = price
                )
            )
        }

        return res.fold(
            onSuccess = { placed ->
                val childNote = placed.childOrderId?.let { " • Child #$it (${placed.childOrderStatus.orEmpty()})" }.orEmpty()
                val sliceNote = if (placed.sliceOrderIds.size > 1) " • ${placed.sliceOrderIds.size} slices" else ""
                val localTrack = ExecutedOrder(
                    id = placed.orderId,
                    timestamp = System.currentTimeMillis(),
                    instrument = tradingSymbol.ifBlank { recommendation.instrument },
                    signal = recommendation.signal,
                    lots = lots,
                    quantity = qty,
                    entryPrice = price,
                    currentPrice = price,
                    targetPrice = targetPrice,
                    stopLossPrice = slPrice,
                    status = if (placed.isSmartOrder) "GTT_ACTIVE" else placed.status,
                    pnl = 0.0,
                    charges = 11.8 + (qty * price * 0.00065),
                    netPnl = -(11.8 + (qty * price * 0.00065))
                )
                _deskState.value = _deskState.value.copy(
                    activeOrders = listOf(localTrack) + _deskState.value.activeOrders
                )
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    statusNotice = "${placed.message} (#${placed.orderId}$childNote$sliceNote)"
                )
                syncIndstocksPortfolioAndOrders()
                Result.success(placed.orderId)
            },
            onFailure = { err ->
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    lastError = err.message ?: "INDstocks live order failed"
                )
                Result.failure(err)
            }
        )
    }

    suspend fun modifyIndstocksOrder(
        orderId: String,
        segment: String = "DERIVATIVE",
        isSmartOrder: Boolean = false,
        newQty: Int,
        newLimitPrice: Double,
        newSlTriggerPrice: Double? = null,
        newSlLimitPrice: Double? = null,
        newTgtTriggerPrice: Double? = null,
        newTgtLimitPrice: Double? = null
    ): Result<String> {
        val token = _indstocksState.value.accessToken
        if (token.isBlank()) return Result.failure(IllegalStateException("INDstocks Access Token not connected."))
        _indstocksState.value = _indstocksState.value.copy(isBusy = true, lastError = null)

        val smartRoute = isSmartOrder || orderId.startsWith("GTT-", ignoreCase = true)
        val res = if (smartRoute) {
            indstocksClient.modifySmartOrder(
                accessToken = token,
                req = IndstocksSmartOrderModifyRequest(
                    orderId = orderId,
                    segment = segment,
                    qty = newQty,
                    limitPrice = newLimitPrice,
                    slTriggerPrice = newSlTriggerPrice,
                    slLimitPrice = newSlLimitPrice,
                    tgtTriggerPrice = newTgtTriggerPrice,
                    tgtLimitPrice = newTgtLimitPrice
                )
            )
        } else {
            indstocksClient.modifyOrder(
                accessToken = token,
                req = IndstocksOrderModifyRequest(
                    orderId = orderId,
                    segment = segment,
                    qty = newQty,
                    limitPrice = newLimitPrice
                )
            )
        }

        return res.fold(
            onSuccess = { mod ->
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    statusNotice = "${mod.message} (${mod.status})"
                )
                syncIndstocksPortfolioAndOrders()
                Result.success(mod.orderId)
            },
            onFailure = { err ->
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    lastError = err.message ?: "Order modify failed"
                )
                Result.failure(err)
            }
        )
    }

    suspend fun cancelIndstocksOrder(
        orderId: String,
        segment: String = "DERIVATIVE",
        isSmartOrder: Boolean = false
    ) {
        val token = _indstocksState.value.accessToken
        if (token.isBlank()) return
        _indstocksState.value = _indstocksState.value.copy(isBusy = true)
        val smartRoute = isSmartOrder || orderId.startsWith("GTT-", ignoreCase = true)
        val cancelRes = if (smartRoute) {
            indstocksClient.cancelSmartOrder(token, orderId, segment)
        } else {
            indstocksClient.cancelOrder(token, orderId, segment)
        }
        cancelRes
            .onSuccess { msg ->
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    statusNotice = msg
                )
                syncIndstocksPortfolioAndOrders()
            }
            .onFailure { err ->
                _indstocksState.value = _indstocksState.value.copy(
                    isBusy = false,
                    lastError = err.message
                )
            }
    }

    private suspend fun resolveSecurityIdAndSymbolForRecommendation(
        rec: FinalTradeRecommendation
    ): Pair<String, String>? {
        val token = _indstocksState.value.accessToken
        if (token.isBlank()) return null

        val strikeInt = rec.strike.roundToInt()
        val optType = rec.optionType.uppercase()

        // 1. Instant hit from live option-chain legSecurities cache (CE / PE)
        if (optType == "CE" || optType == "PE") {
            cachedOptionLegSecurities["${strikeInt}_$optType"]?.let { leg ->
                return leg.securityId to leg.tradingSymbol
            }
        } else if (optType == "FUTURES") {
            cachedNiftyFutures.firstOrNull()?.let { fut ->
                return fut.securityId to fut.tradingSymbol
            }
        }

        // 2. Live search via GET /market/instruments/search
        val searched = indstocksClient.searchDerivativeContracts(
            accessToken = token,
            underlying = "NIFTY",
            segment = "DERIVATIVE",
            instrumentType = if (optType == "FUTURES") "FUTIDX" else "OPTIDX",
            expiry = _indstocksState.value.selectedExpiry,
            strikeFrom = if (optType == "FUTURES") null else rec.strike,
            strikeTo = if (optType == "FUTURES") null else rec.strike,
            optionType = if (optType == "FUTURES") null else optType,
            pageSize = 20
        ).getOrNull().orEmpty()

        val searchedHit = if (optType == "FUTURES") {
            searched.firstOrNull { !it.tradingSymbol.contains("BANKNIFTY", true) && !it.tradingSymbol.contains("FINNIFTY", true) }
        } else {
            searched.firstOrNull {
                abs(it.strikePrice - rec.strike) < 1.0 &&
                    it.optionType.equals(optType, ignoreCase = true)
            } ?: searched.firstOrNull()
        }
        if (searchedHit != null) {
            return searchedHit.securityId to searchedHit.tradingSymbol
        }

        // 3. Fallback to /market/instruments?source=fno CSV cache
        if (cachedFnoInstruments.isEmpty()) {
            cachedFnoInstruments = indstocksClient.fetchInstrumentsCsv(token, "fno").getOrDefault(emptyList())
        }
        val csvHit = cachedFnoInstruments.firstOrNull { row ->
            row.symbol.contains("NIFTY", ignoreCase = true) &&
                !row.symbol.contains("BANKNIFTY", ignoreCase = true) &&
                !row.symbol.contains("FINNIFTY", ignoreCase = true) &&
                ((optType == "FUTURES" && row.optionType.isBlank()) ||
                    (abs(row.strikePrice - rec.strike) < 1.0 && row.optionType.equals(optType, ignoreCase = true)) ||
                    (row.symbol.contains(strikeInt.toString()) && row.symbol.contains(optType, ignoreCase = true)))
        }
        return csvHit?.let { it.securityId to it.tradingSymbol }
    }

    private suspend fun resolveSecurityIdForRecommendation(rec: FinalTradeRecommendation): String? =
        resolveSecurityIdAndSymbolForRecommendation(rec)?.first

    suspend fun refreshLiveMarketData(timeframe: Timeframe = _deskState.value.selectedTimeframe) = coroutineScope {
        _deskState.value = _deskState.value.copy(isRefreshing = true)
        val current = _deskState.value
        val istTime = formatCurrentIstShort()
        val brokerState = _indstocksState.value
        val indstocksToken = brokerState.accessToken

        var primaryQuote: LiveIndexQuote? = null
        var primaryVix: LiveVixQuote? = null
        var primaryOptionChain: OptionsChainSnapshot? = null
        var primaryLatencyMs = 0L
        var fallbackNote: String? = null

        // 1. Primary Feed: INDstocks Trading API Suite v1 when access token is configured
        if (indstocksToken.isNotBlank()) {
            val indStartMs = System.currentTimeMillis()
            // Fetch NIFTY and VIX separately so an invalid secondary scrip-code never 400s the whole batch
            val niftyQuoteDeferred = async {
                indstocksClient.fetchMarketQuotes(indstocksToken, listOf(IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE))
            }
            val vixQuoteDeferred = async {
                indstocksClient.fetchMarketQuotes(indstocksToken, listOf(IndstocksApiClient.INDIA_VIX_SCRIP_CODE))
            }
            val candlesDeferred = async {
                indstocksClient.fetchHistoricalCandles(
                    accessToken = indstocksToken,
                    scripCode = IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE,
                    timeframe = timeframe
                )
            }
            val expiriesDeferred = async {
                if (brokerState.availableExpiries.isNotEmpty()) Result.success(brokerState.availableExpiries)
                else indstocksClient.fetchExpiries(indstocksToken, "NIFTY", "DERIVATIVE")
            }

            val niftyQuoteRes = niftyQuoteDeferred.await()
            val vixQuoteRes = vixQuoteDeferred.await()
            val candlesRes = candlesDeferred.await()
            val expiriesRes = expiriesDeferred.await()

            var quoteMap = niftyQuoteRes.getOrNull().orEmpty()
            var niftyItem = quoteMap[IndstocksApiClient.NIFTY_INDEX_SCRIP_CODE] ?: quoteMap.values.firstOrNull {
                it.symbol.contains("NIFTY", ignoreCase = true) && !it.symbol.contains("VIX", ignoreCase = true)
            }
            var indCandles = candlesRes.getOrNull().orEmpty()

            // If index scrip quote isn't available yet, fall back to active NIFTY near-month futures contract
            if (niftyItem == null) {
                if (cachedNiftyFutures.isEmpty()) {
                    cachedNiftyFutures = indstocksClient.searchDerivativeContracts(
                        accessToken = indstocksToken,
                        underlying = "NIFTY",
                        segment = "DERIVATIVE",
                        instrumentType = "FUTIDX",
                        pageSize = 5
                    ).getOrNull().orEmpty()
                }
                val futScripCode = cachedNiftyFutures.firstOrNull()?.scripCode
                if (!futScripCode.isNullOrBlank()) {
                    val futQuotes = indstocksClient.fetchMarketQuotes(indstocksToken, listOf(futScripCode)).getOrNull().orEmpty()
                    niftyItem = futQuotes[futScripCode] ?: futQuotes.values.firstOrNull()
                    if (indCandles.isEmpty()) {
                        indCandles = indstocksClient.fetchHistoricalCandles(indstocksToken, futScripCode, timeframe)
                            .getOrNull().orEmpty()
                    }
                }
            }

            val vixMap = vixQuoteRes.getOrNull().orEmpty()
            val vixItem = vixMap[IndstocksApiClient.INDIA_VIX_SCRIP_CODE]
                ?: quoteMap[IndstocksApiClient.INDIA_VIX_SCRIP_CODE]
                ?: vixMap.values.firstOrNull { it.symbol.contains("VIX", ignoreCase = true) }

            val expiries = expiriesRes.getOrNull().orEmpty()
            val targetExpiry = brokerState.selectedExpiry ?: expiries.firstOrNull()

            if (niftyItem != null) {
                primaryQuote = LiveIndexQuote(
                    spotPrice = niftyItem.lastPrice,
                    prevClose = niftyItem.prevClose,
                    dayOpen = niftyItem.open,
                    dayHigh = niftyItem.high,
                    dayLow = niftyItem.low,
                    marketTimestampSec = niftyItem.timestampSec,
                    candles = indCandles,
                    sourceLabel = "INDstocks Live API"
                )
                if (vixItem != null && vixItem.lastPrice > 0.0) {
                    primaryVix = LiveVixQuote(vixValue = vixItem.lastPrice, vixChangePct = vixItem.changePct)
                }
                if (!targetExpiry.isNullOrBlank()) {
                    indstocksClient.fetchOptionChainWithSecurities(
                        accessToken = indstocksToken,
                        spotPrice = niftyItem.lastPrice,
                        expiry = targetExpiry,
                        indiaVix = primaryVix?.vixValue ?: current.spot.indiaVix,
                        lotSizeHint = brokerState.activeContractLotSize
                    ).getOrNull()?.let { chainRes ->
                        primaryOptionChain = chainRes.snapshot
                        if (chainRes.legSecurities.isNotEmpty()) {
                            cachedOptionLegSecurities = chainRes.legSecurities
                        }
                    }
                }
                primaryLatencyMs = (System.currentTimeMillis() - indStartMs).coerceAtLeast(1L)
                _indstocksState.value = _indstocksState.value.copy(
                    status = IndstocksConnectionStatus.CONNECTED,
                    availableExpiries = expiries.ifEmpty { _indstocksState.value.availableExpiries },
                    selectedExpiry = targetExpiry ?: _indstocksState.value.selectedExpiry,
                    lastLatencyMs = primaryLatencyMs,
                    lastSyncedAtLabel = istTime,
                    lastError = null
                )
            } else {
                val errMsg = niftyQuoteRes.exceptionOrNull()?.message ?: candlesRes.exceptionOrNull()?.message
                val isAuthErr = errMsg?.contains("401") == true || errMsg?.contains("403") == true
                fallbackNote = if (isAuthErr) "INDstocks token expired — Yahoo/NSE fallback active" else "INDstocks unreachable — Yahoo/NSE fallback active"
                _indstocksState.value = _indstocksState.value.copy(
                    status = if (isAuthErr) IndstocksConnectionStatus.TOKEN_EXPIRED else IndstocksConnectionStatus.ERROR,
                    lastError = errMsg,
                    statusNotice = fallbackNote
                )
            }
        }

        // 2. Fallback / Complementary Public Feed (Yahoo Finance Chart v8 + NSE Official Index API)
        val result = if (primaryQuote != null) {
            val fallbackTracker = liveMarketClient.fetchNseIndexTracker().getOrNull()
            com.example.network.LiveMarketFetchResult(
                isLive = true,
                indexQuote = primaryQuote,
                vixQuote = primaryVix ?: liveMarketClient.fetchYahooVix().getOrNull(),
                indexTracker = fallbackTracker,
                latencyMs = primaryLatencyMs,
                statusMessage = "INDstocks Primary Feed Synced"
            )
        } else {
            liveMarketClient.fetchLiveMarketSnapshot(timeframe)
        }

        if (result.isLive && result.indexQuote != null) {
            val quote = result.indexQuote
            val spotPrice = quote.spotPrice
            val prevClose = quote.prevClose
            val change = spotPrice - prevClose
            val changePct = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0
            val vix = result.vixQuote?.vixValue ?: current.spot.indiaVix
            val vixChangePct = result.vixQuote?.vixChangePct ?: current.spot.vixChangePct

            val liveCandles = if (quote.candles.size >= 5) {
                quote.candles
            } else {
                generateCandlesForTimeframe(
                    spotPrice,
                    timeframe,
                    if (change >= 0) MarketBias.BULLISH else MarketBias.BEARISH
                )
            }

            val technicals = computeTechnicals(liveCandles, spotPrice)
            val gapPts = quote.dayOpen - prevClose
            val gapType = when {
                gapPts >= 20.0 -> "GAP UP"
                gapPts <= -20.0 -> "GAP DOWN"
                else -> "FLAT"
            }

            val pivot = (quote.dayHigh + quote.dayLow + spotPrice) / 3.0
            val r1 = (2.0 * pivot) - quote.dayLow
            val s1 = (2.0 * pivot) - quote.dayHigh
            val r2 = pivot + (quote.dayHigh - quote.dayLow)
            val s2 = pivot - (quote.dayHigh - quote.dayLow)

            val updatedSpot = NiftySpotData(
                spotPrice = spotPrice,
                change = change,
                changePct = changePct,
                dayOpen = quote.dayOpen,
                dayHigh = quote.dayHigh,
                dayLow = quote.dayLow,
                prevClose = prevClose,
                indiaVix = vix,
                vixChangePct = vixChangePct,
                gapType = gapType,
                gapPoints = gapPts,
                marketStructure = when (technicals.trendDirection) {
                    MarketBias.BULLISH -> "Bullish Expansion (HH/HL)"
                    MarketBias.BEARISH -> "Bearish Breakdown (LH/LL)"
                    else -> "Range Consolidation"
                },
                pdh = quote.dayHigh,
                pdl = quote.dayLow,
                dailyPivot = pivot,
                r1 = r1,
                r2 = r2,
                s1 = s1,
                s2 = s2,
                weeklyHigh = max(quote.dayHigh, liveCandles.maxOf { it.high }),
                weeklyLow = min(quote.dayLow, liveCandles.minOf { it.low })
            )

            val rebuiltState = rebuildDerivedDeskState(
                baseState = current.copy(
                    spot = updatedSpot,
                    candles = liveCandles,
                    technicals = technicals,
                    selectedTimeframe = timeframe,
                    selectedScenarioName = DeskState.SCENARIO_LIVE_FEED,
                    isLiveMarketData = true,
                    dataSourceLabel = if (fallbackNote != null) "${quote.sourceLabel} (Fallback)" else quote.sourceLabel,
                    lastSyncLabel = "Live Synced $istTime (${result.latencyMs}ms)",
                    isRefreshing = false,
                    liveNetworkError = fallbackNote
                ),
                overrideOptionsChain = primaryOptionChain
            )
            _deskState.value = rebuiltState

            result.indexTracker?.let { tracker ->
                _nseMcpState.value = _nseMcpState.value.copy(
                    indexTracker = tracker,
                    isConnected = true,
                    isFallbackData = false,
                    latencyMs = result.latencyMs,
                    lastSyncTime = "Index API Synced $istTime",
                    syncStatusMessage = result.statusMessage
                )
            }
        } else {
            _deskState.value = current.copy(
                isRefreshing = false,
                isLiveMarketData = false,
                dataSourceLabel = "Cached NSE Reference",
                lastSyncLabel = "Offline Fallback ($istTime)",
                liveNetworkError = fallbackNote ?: result.statusMessage
            )
        }
    }

    suspend fun syncNseBhavcopy(symbol: String = "NIFTY 50") {
        _nseMcpState.value = _nseMcpState.value.copy(isSyncing = true)
        val result = nseMcpClient.queryBhavcopy(symbol)
        val record = result.record
        val istTime = formatCurrentIstShort()

        _nseMcpState.value = _nseMcpState.value.copy(
            isSyncing = false,
            isConnected = result.isLiveNetworkSuccess,
            isFallbackData = !result.isLiveNetworkSuccess,
            latencyMs = result.latencyMs,
            latestBhavcopy = record,
            logs = result.logs + _nseMcpState.value.logs,
            lastSyncTime = if (result.isLiveNetworkSuccess) {
                "Synced Live (${record.tradeDate}, ${result.latencyMs}ms)"
            } else {
                "Cached Reference (Offline at $istTime)"
            },
            syncStatusMessage = result.statusMessage
        )

        val current = _deskState.value
        val change = record.close - record.prevClose
        val changePct = if (record.prevClose > 0.0) (change / record.prevClose) * 100.0 else 0.0
        val pivot = (record.high + record.low + record.close) / 3.0
        val updatedSpot = current.spot.copy(
            spotPrice = record.close,
            dayOpen = record.open,
            dayHigh = record.high,
            dayLow = record.low,
            prevClose = record.prevClose,
            change = change,
            changePct = changePct,
            dailyPivot = pivot,
            r1 = (2.0 * pivot) - record.low,
            s1 = (2.0 * pivot) - record.high
        )

        val trendBias = if (change > 15.0) MarketBias.BULLISH else if (change < -15.0) MarketBias.BEARISH else MarketBias.SIDEWAYS
        val syncedCandles = generateCandlesForTimeframe(record.close, current.selectedTimeframe, trendBias)
        val syncedTechnicals = computeTechnicals(syncedCandles, record.close)

        _deskState.value = rebuildDerivedDeskState(
            current.copy(
                spot = updatedSpot,
                candles = syncedCandles,
                technicals = syncedTechnicals,
                isLiveMarketData = result.isLiveNetworkSuccess,
                dataSourceLabel = if (result.isLiveNetworkSuccess) "NSE Official MCP" else "NSE Cached Reference",
                lastSyncLabel = if (result.isLiveNetworkSuccess) "MCP Synced $istTime" else "Cached Fallback $istTime",
                liveNetworkError = if (result.isLiveNetworkSuccess) null else result.statusMessage
            )
        )
    }

    fun onHeartbeat() {
        val current = _deskState.value
        val updatedSession = MarketHoursEngine.getMarketSessionStatus(current.isLiveStreaming)
        if (current.sessionStatus.currentTimeIst != updatedSession.currentTimeIst ||
            current.sessionStatus.isExchangeOpen != updatedSession.isExchangeOpen
        ) {
            _deskState.value = current.copy(sessionStatus = updatedSession)
        }
        if (current.isLiveStreaming) {
            simulateMarketTick()
        }
    }

    fun switchScenario(scenarioName: String) {
        val currentStreaming = _deskState.value.isLiveStreaming
        val newState = createInitialDeskState(scenarioName)
        _deskState.value = newState.copy(
            isLiveStreaming = currentStreaming,
            sessionStatus = MarketHoursEngine.getMarketSessionStatus(currentStreaming)
        )
    }

    fun switchTimeframe(tf: Timeframe) {
        val current = _deskState.value
        val newCandles = generateCandlesForTimeframe(current.spot.spotPrice, tf, current.technicals.trendDirection)
        val tech = computeTechnicals(newCandles, current.spot.spotPrice)
        _deskState.value = current.copy(
            selectedTimeframe = tf,
            candles = newCandles,
            technicals = tech
        )
        reevaluateAgents()
    }

    fun switchWorkflow(workflow: TradingWorkflow) {
        _deskState.value = _deskState.value.copy(selectedWorkflow = workflow)
    }

    fun switchTradingStyle(style: TradingStyle) {
        _deskState.value = _deskState.value.copy(selectedTradingStyle = style)
    }

    fun toggleStreaming() {
        val current = _deskState.value
        val nextStreaming = !current.isLiveStreaming
        _deskState.value = current.copy(
            isLiveStreaming = nextStreaming,
            sessionStatus = MarketHoursEngine.getMarketSessionStatus(nextStreaming)
        )
    }

    fun estimateEntryPriceForRecommendation(recommendation: FinalTradeRecommendation): Double {
        val strikeObj = _deskState.value.optionsChain.strikes.find { it.strikePrice == recommendation.strike }
        return when (recommendation.optionType) {
            "CE" -> strikeObj?.callLtp ?: 145.0
            "PE" -> strikeObj?.putLtp ?: 135.0
            "FUTURES" -> _deskState.value.futures.currentMonthPrice
            else -> strikeObj?.callLtp ?: 140.0
        }
    }

    fun executeAuthorizedTrade(recommendation: FinalTradeRecommendation, lots: Int = 2) {
        if (recommendation.signal == SignalType.NO_TRADE) return

        val lotSize = 75
        val qty = lots * lotSize
        val price = estimateEntryPriceForRecommendation(recommendation)
        val targetDelta = max(25.0, price * 0.28)
        val slDelta = max(14.0, price * 0.16)

        val order = ExecutedOrder(
            id = "ORD-${UUID.randomUUID().toString().take(6).uppercase()}",
            timestamp = System.currentTimeMillis(),
            instrument = recommendation.instrument,
            signal = recommendation.signal,
            lots = lots,
            quantity = qty,
            entryPrice = price,
            currentPrice = price,
            targetPrice = price + targetDelta,
            stopLossPrice = max(5.0, price - slDelta),
            status = "ACTIVE",
            pnl = 0.0,
            charges = (lots * 40.0) + (qty * price * 0.00065),
            netPnl = -((lots * 40.0) + (qty * price * 0.00065))
        )

        val updatedOrders = listOf(order) + _deskState.value.activeOrders
        _deskState.value = _deskState.value.copy(activeOrders = updatedOrders)
    }

    fun closeOrder(orderId: String) {
        val current = _deskState.value
        val updated = current.activeOrders.map { order ->
            if (order.id == orderId) {
                order.copy(status = "CLOSED")
            } else order
        }
        val closedOrder = current.activeOrders.find { it.id == orderId }
        val pnlGain = closedOrder?.netPnl ?: 0.0
        _deskState.value = current.copy(
            activeOrders = updated,
            totalRealizedPnl = current.totalRealizedPnl + pnlGain
        )
    }

    fun simulateMarketTick() {
        tickCounter++

        val current = _deskState.value
        val trend = current.technicals.trendDirection
        val drift = when (trend) {
            MarketBias.BULLISH -> 1.8
            MarketBias.BEARISH -> -1.8
            else -> 0.2
        }
        val noise = (sin(tickCounter.toDouble() * 0.7) * 2.5) + (cos(tickCounter.toDouble() * 1.3) * 1.5)
        val tickDelta = drift + noise
        val newSpotPrice = current.spot.spotPrice + tickDelta

        val newSpot = current.spot.copy(
            spotPrice = newSpotPrice,
            change = newSpotPrice - current.spot.prevClose,
            changePct = ((newSpotPrice - current.spot.prevClose) / current.spot.prevClose) * 100.0,
            dayHigh = max(current.spot.dayHigh, newSpotPrice),
            dayLow = min(current.spot.dayLow, newSpotPrice)
        )

        val newFuturesPrice = newSpotPrice + current.futures.basis + (drift * 0.5)
        val newFutures = current.futures.copy(
            currentMonthPrice = newFuturesPrice,
            basis = newFuturesPrice - newSpotPrice
        )

        val updatedOrders = current.activeOrders.map { order ->
            if (order.status == "ACTIVE") {
                val isBullishOrder = order.signal == SignalType.BUY_CE || order.signal == SignalType.BULLISH_FUTURES
                val isBearishOrder = order.signal == SignalType.BUY_PE || order.signal == SignalType.BEARISH_FUTURES
                val premiumShift = when {
                    isBullishOrder -> tickDelta * 0.52
                    isBearishOrder -> -tickDelta * 0.48
                    else -> 0.0
                }
                val newLtp = max(2.0, order.currentPrice + premiumShift)
                val grossPnl = (newLtp - order.entryPrice) * order.quantity
                val net = grossPnl - order.charges
                val status = when {
                    newLtp >= order.targetPrice -> "TARGET_REACHED"
                    newLtp <= order.stopLossPrice -> "STOPPED_OUT"
                    else -> "ACTIVE"
                }
                order.copy(currentPrice = newLtp, pnl = grossPnl, netPnl = net, status = status)
            } else order
        }

        val updatedCandles = current.candles.toMutableList()
        if (updatedCandles.isNotEmpty()) {
            val last = updatedCandles.last()
            val newLast = last.copy(
                high = max(last.high, newSpotPrice),
                low = min(last.low, newSpotPrice),
                close = newSpotPrice,
                volume = last.volume + 350L
            )
            updatedCandles[updatedCandles.size - 1] = newLast
        }

        _deskState.value = current.copy(
            spot = newSpot,
            futures = newFutures,
            candles = updatedCandles,
            activeOrders = updatedOrders
        )

        if (tickCounter % 5 == 0) {
            reevaluateAgents()
        }
    }

    private fun reevaluateAgents() {
        val s = _deskState.value
        val rep1 = multiAgentSystem.evaluateMarketIntelligence(s.spot, s.futures)
        val rep2 = multiAgentSystem.evaluateTechnicalAnalysis(s.technicals, s.spot.spotPrice)
        val rep3 = multiAgentSystem.evaluateOptionsChain(s.optionsChain, s.spot.spotPrice)
        val rep4 = multiAgentSystem.evaluateOptionsGreeks(s.optionsChain, s.spot.spotPrice, s.spot.indiaVix)
        val rep5 = multiAgentSystem.evaluateFuturesInstitutional(s.futures, s.spot.spotPrice)
        val rep6 = multiAgentSystem.evaluateBreakoutPatterns(s.breakout, s.spot.spotPrice)
        val rep7 = multiAgentSystem.evaluateSmartMoney(s.smartMoney, s.spot.spotPrice)

        val (centralReport, recommendation) = multiAgentSystem.reconcileAndDecide(
            rep1, rep2, rep3, rep4, rep5, rep6, rep7,
            s.spot, s.optionsChain, s.technicals, s.futures
        )

        val allReports = listOf(rep1, rep2, rep3, rep4, rep5, rep6, rep7, centralReport)
        val auditList = if (s.historicalAuditLogs.none { it.instrument == recommendation.instrument && it.signal == recommendation.signal }) {
            (listOf(recommendation) + s.historicalAuditLogs).take(20)
        } else s.historicalAuditLogs

        _deskState.value = s.copy(
            agentReports = allReports,
            finalRecommendation = recommendation,
            historicalAuditLogs = auditList
        )
    }

    private fun rebuildDerivedDeskState(
        baseState: DeskState,
        overrideOptionsChain: OptionsChainSnapshot? = null
    ): DeskState {
        val spot = baseState.spot
        val tech = baseState.technicals
        val trend = tech.trendDirection
        val vix = spot.indiaVix
        val pcr = overrideOptionsChain?.pcr ?: when (trend) {
            MarketBias.BULLISH -> 1.24
            MarketBias.BEARISH -> 0.72
            else -> 0.98
        }
        val fiiRatio = when (trend) {
            MarketBias.BULLISH -> 64.5
            MarketBias.BEARISH -> 35.0
            else -> 51.0
        }
        val futuresBasis = if (trend == MarketBias.BULLISH) 44.5 else if (trend == MarketBias.BEARISH) -14.0 else 22.0
        val weeklyExpiry = overrideOptionsChain?.expiryDate ?: computeWeeklyExpiryLabel()
        val monthlyExpiry = computeMonthlyExpiryLabel()

        val futures = FuturesData(
            currentMonthExpiry = monthlyExpiry,
            currentMonthPrice = spot.spotPrice + futuresBasis,
            currentMonthOi = 12640000L,
            currentMonthOiChange = if (trend == MarketBias.BULLISH) 760000L else if (trend == MarketBias.BEARISH) 850000L else -95000L,
            currentMonthBuildup = when (trend) {
                MarketBias.BULLISH -> BuildupType.LONG_BUILDUP
                MarketBias.BEARISH -> BuildupType.SHORT_BUILDUP
                else -> BuildupType.NEUTRAL
            },
            nextMonthExpiry = computeNextMonthExpiryLabel(),
            nextMonthPrice = spot.spotPrice + futuresBasis + 82.0,
            nextMonthOi = 3350000L,
            basis = futuresBasis,
            fiiLongContracts = (fiiRatio * 1500).toLong(),
            fiiShortContracts = ((100.0 - fiiRatio) * 1500).toLong(),
            fiiNetRatio = fiiRatio,
            diiNetActivityCr = if (trend == MarketBias.BULLISH) 1680.0 else if (trend == MarketBias.BEARISH) -540.0 else 280.0,
            institutionalBias = trend
        )

        val optionsChain = overrideOptionsChain ?: run {
            val strikes = generateOptionsChainStrikes(spot.spotPrice, trend, vix)
            val atmStrike = (spot.spotPrice / 50.0).roundToInt() * 50.0
            val maxCallStrike = strikes.maxByOrNull { it.callOi }?.strikePrice ?: (atmStrike + 200.0)
            val maxPutStrike = strikes.maxByOrNull { it.putOi }?.strikePrice ?: (atmStrike - 200.0)
            val atmObj = strikes.find { it.strikePrice == atmStrike } ?: strikes.first()
            OptionsChainSnapshot(
                strikes = strikes,
                atmStrike = atmStrike,
                totalCallOi = strikes.sumOf { it.callOi },
                totalPutOi = strikes.sumOf { it.putOi },
                pcr = pcr,
                maxCallOiStrike = maxCallStrike,
                maxPutOiStrike = maxPutStrike,
                maxPainStrike = atmStrike,
                ivPercentile = if (vix > 15.0) 64.0 else 38.0,
                atmStraddlePremium = atmObj.callLtp + atmObj.putLtp,
                expectedMovePoints = OptionsGreeksCalculator.calculateExpectedMove(spot.spotPrice, vix, 4.0),
                expiryDate = weeklyExpiry
            )
        }
        val atmStrike = optionsChain.atmStrike

        val smartMoney = SmartMoneySnapshot(
            bosDetected = trend != MarketBias.SIDEWAYS,
            bosType = when (trend) {
                MarketBias.BULLISH -> "Bullish BOS above ${(atmStrike - 50).toInt()}"
                MarketBias.BEARISH -> "Bearish BOS below ${(atmStrike + 50).toInt()}"
                else -> "Consolidation Range"
            },
            chochDetected = false,
            chochType = "None",
            fvgZones = listOf(
                PriceZone("15m FVG Imbalance", spot.spotPrice - 35.0, spot.spotPrice - 18.0, trend != MarketBias.BEARISH)
            ),
            orderBlocks = listOf(
                OrderBlock("Institutional Demand OB", spot.spotPrice - 60.0, spot.spotPrice - 42.0, isBullish = true, isMitigated = false)
            ),
            currentZone = if (spot.spotPrice >= tech.vwap) "Equilibrium / Above VWAP" else "Discount Zone",
            liquiditySweepObserved = trend != MarketBias.SIDEWAYS
        )

        val breakout = BreakoutSnapshot(
            orb15mHigh = spot.dayHigh,
            orb15mLow = spot.dayLow,
            orbStatus = when (trend) {
                MarketBias.BULLISH -> "BULLISH MOMENTUM (> ${tech.vwap.toInt()})"
                MarketBias.BEARISH -> "BEARISH BREAKDOWN (< ${tech.vwap.toInt()})"
                else -> "CONSOLIDATION (${spot.dayLow.toInt()} - ${spot.dayHigh.toInt()})"
            },
            pdhStatus = if (spot.spotPrice >= spot.pdh - 15.0) "Testing Session High (${spot.pdh.toInt()})" else "Below Resistance",
            pdlStatus = if (spot.spotPrice <= spot.pdl + 15.0) "Testing Session Low (${spot.pdl.toInt()})" else "Support Holding",
            patternDetected = tech.candlestickPattern,
            volumeConfirmed = trend != MarketBias.SIDEWAYS
        )

        val rep1 = multiAgentSystem.evaluateMarketIntelligence(spot, futures)
        val rep2 = multiAgentSystem.evaluateTechnicalAnalysis(tech, spot.spotPrice)
        val rep3 = multiAgentSystem.evaluateOptionsChain(optionsChain, spot.spotPrice)
        val rep4 = multiAgentSystem.evaluateOptionsGreeks(optionsChain, spot.spotPrice, vix)
        val rep5 = multiAgentSystem.evaluateFuturesInstitutional(futures, spot.spotPrice)
        val rep6 = multiAgentSystem.evaluateBreakoutPatterns(breakout, spot.spotPrice)
        val rep7 = multiAgentSystem.evaluateSmartMoney(smartMoney, spot.spotPrice)

        val (centralReport, recommendation) = multiAgentSystem.reconcileAndDecide(
            rep1, rep2, rep3, rep4, rep5, rep6, rep7,
            spot, optionsChain, tech, futures
        )

        val auditList = (listOf(recommendation) + baseState.historicalAuditLogs)
            .distinctBy { "${it.signal}_${it.instrument}" }
            .take(20)

        return baseState.copy(
            futures = futures,
            optionsChain = optionsChain,
            smartMoney = smartMoney,
            breakout = breakout,
            agentReports = listOf(rep1, rep2, rep3, rep4, rep5, rep6, rep7, centralReport),
            finalRecommendation = recommendation,
            historicalAuditLogs = auditList
        )
    }

    private fun createInitialDeskState(scenarioName: String): DeskState {
        val baseSpot: Double
        val prevClose: Double
        val trend: MarketBias
        val gapType: String
        val gapPoints: Double
        val pcr: Double
        val fiiRatio: Double
        val vix: Double
        val orbStatus: String
        val pattern: String
        val bosType: String

        when (scenarioName) {
            "Bearish Breakdown (PE Rally)" -> {
                baseSpot = 24895.40
                prevClose = 25037.40
                trend = MarketBias.BEARISH
                gapType = "GAP DOWN"
                gapPoints = -88.0
                pcr = 0.68
                fiiRatio = 32.5
                vix = 15.4
                orbStatus = "BEARISH BREAKDOWN (< 24,940)"
                pattern = "Bearish Breakdown below Previous Day Low"
                bosType = "Bearish BOS (Lower Low broken at 24,920)"
            }
            "Expiry Day Pinning (Range-Bound)" -> {
                baseSpot = 25102.10
                prevClose = 25090.10
                trend = MarketBias.SIDEWAYS
                gapType = "FLAT"
                gapPoints = 4.0
                pcr = 0.98
                fiiRatio = 51.0
                vix = 11.8
                orbStatus = "CONSOLIDATION (Within 25,080 - 25,130)"
                pattern = "Symmetrical Triangle / Compression"
                bosType = "No BOS (Range consolidation)"
            }
            "Conflicting Signals (No-Trade Trap)" -> {
                baseSpot = 25060.80
                prevClose = 25048.80
                trend = MarketBias.SIDEWAYS
                gapType = "GAP UP"
                gapPoints = 35.0
                pcr = 0.92
                fiiRatio = 48.0
                vix = 14.1
                orbStatus = "WITHIN RANGE"
                pattern = "Liquidity Sweep Trap / False Breakout"
                bosType = "Conflicting Signals on Multiple Timeframes"
            }
            "Bullish Breakout (CE Expansion)" -> {
                baseSpot = 25248.60
                prevClose = 25113.60
                trend = MarketBias.BULLISH
                gapType = "GAP UP"
                gapPoints = 94.0
                pcr = 1.34
                fiiRatio = 68.4
                vix = 13.25
                orbStatus = "BULLISH BREAKOUT (> 25,200)"
                pattern = "Bull Flag Breakout with Volume Surge"
                bosType = "Bullish BOS (Higher High printed above 25,220)"
            }
            else -> { // DeskState.SCENARIO_LIVE_FEED fallback baseline aligned to current NIFTY 50 close (25,144.30)
                baseSpot = 25144.30
                prevClose = 25139.25
                trend = MarketBias.BULLISH
                gapType = "FLAT"
                gapPoints = 5.05
                pcr = 1.28
                fiiRatio = 64.2
                vix = 13.25
                orbStatus = "BULLISH CONTINUATION (> 25,100)"
                pattern = "Steady Institutional Accumulation"
                bosType = "Bullish BOS (Higher High holding above 25,100)"
            }
        }

        val change = baseSpot - prevClose
        val changePct = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0

        val spotData = NiftySpotData(
            spotPrice = baseSpot,
            change = change,
            changePct = changePct,
            dayOpen = prevClose + gapPoints,
            dayHigh = baseSpot + 48.0,
            dayLow = baseSpot - 62.0,
            prevClose = prevClose,
            indiaVix = vix,
            vixChangePct = if (trend == MarketBias.BEARISH) 4.2 else -1.85,
            gapType = gapType,
            gapPoints = gapPoints,
            marketStructure = if (trend == MarketBias.BULLISH) "Bullish Expansion (HH/HL)" else if (trend == MarketBias.BEARISH) "Bearish Breakdown (LH/LL)" else "Tight Pinning Compression",
            pdh = baseSpot + 32.0,
            pdl = baseSpot - 85.0,
            dailyPivot = baseSpot - 15.0,
            r1 = baseSpot + 65.0,
            r2 = baseSpot + 125.0,
            s1 = baseSpot - 65.0,
            s2 = baseSpot - 130.0,
            weeklyHigh = baseSpot + 240.0,
            weeklyLow = baseSpot - 290.0
        )

        val weeklyExpiry = computeWeeklyExpiryLabel()
        val monthlyExpiry = computeMonthlyExpiryLabel()
        val nextMonthlyExpiry = computeNextMonthExpiryLabel()

        val futuresBasis = if (trend == MarketBias.BULLISH) 48.5 else if (trend == MarketBias.BEARISH) -18.0 else 24.0
        val futures = FuturesData(
            currentMonthExpiry = monthlyExpiry,
            currentMonthPrice = baseSpot + futuresBasis,
            currentMonthOi = 12450000L,
            currentMonthOiChange = if (trend == MarketBias.BULLISH) 840000L else if (trend == MarketBias.BEARISH) 920000L else -120000L,
            currentMonthBuildup = if (trend == MarketBias.BULLISH) BuildupType.LONG_BUILDUP else if (trend == MarketBias.BEARISH) BuildupType.SHORT_BUILDUP else BuildupType.NEUTRAL,
            nextMonthExpiry = nextMonthlyExpiry,
            nextMonthPrice = baseSpot + futuresBasis + 85.0,
            nextMonthOi = 3200000L,
            basis = futuresBasis,
            fiiLongContracts = (fiiRatio * 1500).toLong(),
            fiiShortContracts = ((100.0 - fiiRatio) * 1500).toLong(),
            fiiNetRatio = fiiRatio,
            diiNetActivityCr = if (trend == MarketBias.BULLISH) 1845.0 else if (trend == MarketBias.BEARISH) -620.0 else 310.0,
            institutionalBias = trend
        )

        val strikes = generateOptionsChainStrikes(baseSpot, trend, vix)
        val atmStrike = (baseSpot / 50.0).roundToInt() * 50.0
        val maxCallStrike = strikes.maxByOrNull { it.callOi }?.strikePrice ?: (atmStrike + 200.0)
        val maxPutStrike = strikes.maxByOrNull { it.putOi }?.strikePrice ?: (atmStrike - 200.0)

        val atmObj = strikes.find { it.strikePrice == atmStrike } ?: strikes.first()
        val straddlePremium = atmObj.callLtp + atmObj.putLtp
        val expectedMove = OptionsGreeksCalculator.calculateExpectedMove(baseSpot, vix, 4.0)

        val optionsChain = OptionsChainSnapshot(
            strikes = strikes,
            atmStrike = atmStrike,
            totalCallOi = strikes.sumOf { it.callOi },
            totalPutOi = strikes.sumOf { it.putOi },
            pcr = pcr,
            maxCallOiStrike = maxCallStrike,
            maxPutOiStrike = maxPutStrike,
            maxPainStrike = atmStrike,
            ivPercentile = if (vix > 15.0) 64.0 else 38.0,
            atmStraddlePremium = straddlePremium,
            expectedMovePoints = expectedMove,
            expiryDate = weeklyExpiry
        )

        val candles = generateCandlesForTimeframe(baseSpot, Timeframe.M15, trend)
        val technicals = computeTechnicals(candles, baseSpot)

        val smartMoney = SmartMoneySnapshot(
            bosDetected = trend != MarketBias.SIDEWAYS,
            bosType = bosType,
            chochDetected = false,
            chochType = "None",
            fvgZones = listOf(
                PriceZone("15m FVG Impulsive Imbalance", baseSpot - 32.0, baseSpot - 18.0, trend == MarketBias.BULLISH)
            ),
            orderBlocks = listOf(
                OrderBlock("Institutional Demand OB", baseSpot - 55.0, baseSpot - 40.0, isBullish = true, isMitigated = false)
            ),
            currentZone = if (trend == MarketBias.BULLISH) "Discount Zone (38% of Daily Range)" else "Premium Zone (68% of Daily Range)",
            liquiditySweepObserved = trend == MarketBias.BULLISH
        )

        val breakout = BreakoutSnapshot(
            orb15mHigh = baseSpot + 35.0,
            orb15mLow = baseSpot - 45.0,
            orbStatus = orbStatus,
            pdhStatus = if (trend == MarketBias.BULLISH) "Clean Breakout with Volume Confirmation" else "Resistance Holding",
            pdlStatus = if (trend == MarketBias.BEARISH) "Breakdown Confirmed" else "Support Maintained",
            patternDetected = pattern,
            volumeConfirmed = trend != MarketBias.SIDEWAYS
        )

        val rep1 = multiAgentSystem.evaluateMarketIntelligence(spotData, futures)
        val rep2 = multiAgentSystem.evaluateTechnicalAnalysis(technicals, baseSpot)
        val rep3 = multiAgentSystem.evaluateOptionsChain(optionsChain, baseSpot)
        val rep4 = multiAgentSystem.evaluateOptionsGreeks(optionsChain, baseSpot, vix)
        val rep5 = multiAgentSystem.evaluateFuturesInstitutional(futures, baseSpot)
        val rep6 = multiAgentSystem.evaluateBreakoutPatterns(breakout, baseSpot)
        val rep7 = multiAgentSystem.evaluateSmartMoney(smartMoney, baseSpot)

        val (centralReport, recommendation) = multiAgentSystem.reconcileAndDecide(
            rep1, rep2, rep3, rep4, rep5, rep6, rep7,
            spotData, optionsChain, technicals, futures
        )

        val compactExpiry = weeklyExpiry.replace("-", "")
        val initialActiveOrder = ExecutedOrder(
            id = "ORD-NF8842",
            timestamp = System.currentTimeMillis() - 1800000L,
            instrument = "NIFTY $compactExpiry ${atmStrike.toInt()} CE",
            signal = SignalType.BUY_CE,
            lots = 2,
            quantity = 50,
            entryPrice = max(25.0, atmObj.callLtp - 24.5),
            currentPrice = atmObj.callLtp,
            targetPrice = atmObj.callLtp + 38.0,
            stopLossPrice = max(5.0, atmObj.callLtp - 44.0),
            status = "ACTIVE",
            pnl = 24.5 * 50,
            charges = 85.0,
            netPnl = (24.5 * 50) - 85.0
        )

        val sessionStatus = MarketHoursEngine.getMarketSessionStatus(false)
        return DeskState(
            spot = spotData,
            futures = futures,
            optionsChain = optionsChain,
            technicals = technicals,
            smartMoney = smartMoney,
            breakout = breakout,
            agentReports = listOf(rep1, rep2, rep3, rep4, rep5, rep6, rep7, centralReport),
            finalRecommendation = recommendation,
            candles = candles,
            selectedScenarioName = scenarioName,
            isLiveStreaming = sessionStatus.isExchangeOpen,
            sessionStatus = sessionStatus,
            activeOrders = if (trend == MarketBias.BULLISH) listOf(initialActiveOrder) else emptyList(),
            historicalAuditLogs = listOf(recommendation),
            isLiveMarketData = false,
            dataSourceLabel = if (scenarioName == DeskState.SCENARIO_LIVE_FEED) "Cached NSE Reference" else "Scenario Replay",
            lastSyncLabel = if (scenarioName == DeskState.SCENARIO_LIVE_FEED) "Waiting for live sync" else "Preset active"
        )
    }

    private fun generateOptionsChainStrikes(spot: Double, trend: MarketBias, vix: Double): List<OptionStrike> {
        val atm = (spot / 50.0).roundToInt() * 50.0
        val strikes = mutableListOf<OptionStrike>()

        for (i in -8..8) {
            val strike = atm + (i * 50)
            val dte = 4.0

            val callGreeks = OptionsGreeksCalculator.calculateCallGreeks(spot, strike, vix + 1.2, dte)
            val putGreeks = OptionsGreeksCalculator.calculatePutGreeks(spot, strike, vix + 0.8, dte)

            val baseCallOi = when {
                strike == atm + 200.0 -> 8400000L
                strike > spot -> (6200000L - (i * 250000)).coerceAtLeast(1200000L)
                else -> (3100000L - abs(i) * 300000).coerceAtLeast(500000L)
            }
            val basePutOi = when {
                strike == atm - 200.0 -> 9100000L
                strike < spot -> (6500000L - (abs(i) * 220000)).coerceAtLeast(1400000L)
                else -> (2800000L - (i * 280000)).coerceAtLeast(400000L)
            }

            val callOiChange = if (trend == MarketBias.BULLISH) {
                if (strike <= atm) -480000L else 320000L
            } else {
                if (strike >= atm) 950000L else -120000L
            }

            val putOiChange = if (trend == MarketBias.BULLISH) {
                if (strike <= atm + 50) 1240000L else 450000L
            } else {
                if (strike >= atm) -640000L else 280000L
            }

            val callBuildup = if (callOiChange > 0) {
                if (callGreeks.price > 100) BuildupType.LONG_BUILDUP else BuildupType.SHORT_BUILDUP
            } else {
                if (callGreeks.price > 100) BuildupType.SHORT_COVERING else BuildupType.LONG_UNWINDING
            }

            val putBuildup = if (putOiChange > 0) {
                if (putGreeks.price > 100) BuildupType.LONG_BUILDUP else BuildupType.SHORT_BUILDUP
            } else {
                if (putGreeks.price > 100) BuildupType.SHORT_COVERING else BuildupType.LONG_UNWINDING
            }

            strikes.add(
                OptionStrike(
                    strikePrice = strike,
                    callOi = baseCallOi,
                    callOiChange = callOiChange,
                    callLtp = callGreeks.price,
                    callIv = vix + 1.2,
                    callDelta = callGreeks.delta,
                    callGamma = callGreeks.gamma,
                    callTheta = callGreeks.theta,
                    callVega = callGreeks.vega,
                    callBuildup = callBuildup,
                    putOi = basePutOi,
                    putOiChange = putOiChange,
                    putLtp = putGreeks.price,
                    putIv = vix + 0.8,
                    putDelta = putGreeks.delta,
                    putGamma = putGreeks.gamma,
                    putTheta = putGreeks.theta,
                    putVega = putGreeks.vega,
                    putBuildup = putBuildup,
                    isAtm = strike == atm,
                    isMaxCallOi = strike == atm + 200.0,
                    isMaxPutOi = strike == atm - 200.0
                )
            )
        }
        return strikes
    }

    private fun generateCandlesForTimeframe(currentPrice: Double, tf: Timeframe, trend: MarketBias): List<Candle> {
        val count = 40
        val candles = mutableListOf<Candle>()
        var price = currentPrice - when (trend) {
            MarketBias.BULLISH -> 120.0
            MarketBias.BEARISH -> -110.0
            else -> 15.0
        }

        val stepMs = tf.minutes * 60 * 1000L
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DATE, -2)
        } else if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            cal.add(Calendar.DATE, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 15)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val baseTime = cal.timeInMillis

        var runningVwapNum = 0.0
        var runningVwapDen = 0.0

        for (i in 0 until count) {
            val drift = when (trend) {
                MarketBias.BULLISH -> (i.toDouble() / count) * 4.5
                MarketBias.BEARISH -> -(i.toDouble() / count) * 4.5
                else -> 0.0
            }
            val wave = sin(i.toDouble() * 0.45) * 14.0
            val open = price
            val close = open + drift + wave * 0.4
            val high = max(open, close) + (abs(sin(i * 1.1)) * 12.0)
            val low = min(open, close) - (abs(cos(i * 1.1)) * 10.0)
            val volume = 25000L + (abs(sin(i * 0.8)) * 45000L).toLong()

            val tp = (high + low + close) / 3.0
            runningVwapNum += tp * volume
            runningVwapDen += volume
            val vwap = runningVwapNum / runningVwapDen

            candles.add(
                Candle(
                    timestamp = baseTime + (i * stepMs),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    vwap = vwap
                )
            )
            price = close
        }

        if (candles.isNotEmpty()) {
            val last = candles.last()
            candles[candles.size - 1] = last.copy(
                close = currentPrice,
                high = max(last.high, currentPrice),
                low = min(last.low, currentPrice)
            )
        }

        return candles
    }

    private fun computeTechnicals(candles: List<Candle>, currentSpot: Double): TechnicalAnalysisSnapshot {
        val rsi = TechnicalCalculator.calculateRsi(candles, 14)
        val (macdLine, macdSignal, macdHist) = TechnicalCalculator.calculateMacd(candles)
        val vwap = TechnicalCalculator.calculateVwap(candles)
        val closes = candles.map { it.close }
        val ema9 = TechnicalCalculator.calculateEma(closes, 9)
        val ema20 = TechnicalCalculator.calculateEma(closes, 20)
        val ema50 = TechnicalCalculator.calculateEma(closes, 50)
        val ema200 = ema50 - 45.0
        val (stValue, stBullish) = TechnicalCalculator.calculateSupertrend(candles, 10, 3.0)
        val (adx, diPlus, diMinus) = TechnicalCalculator.calculateAdx(candles, 14)
        val (bbUpper, bbMid, bbLower) = TechnicalCalculator.calculateBollingerBands(candles, 20, 2.0)
        val atr = TechnicalCalculator.calculateAtr(candles, 14)
        val pattern = TechnicalCalculator.detectCandlestickPattern(candles)

        val trendDirection = when {
            ema9 > ema20 && stBullish && rsi > 52.0 -> MarketBias.BULLISH
            ema9 < ema20 && !stBullish && rsi < 48.0 -> MarketBias.BEARISH
            else -> MarketBias.SIDEWAYS
        }

        val fibs = mapOf(
            "23.6%" to (currentSpot - atr * 0.5),
            "38.2%" to (currentSpot - atr * 0.9),
            "50.0%" to (currentSpot - atr * 1.2),
            "61.8%" to (currentSpot - atr * 1.6)
        )

        return TechnicalAnalysisSnapshot(
            trendDirection = trendDirection,
            rsi14 = rsi,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHist = macdHist,
            vwap = vwap,
            ema9 = ema9,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            supertrend = stValue,
            supertrendBullish = stBullish,
            adx = adx,
            diPlus = diPlus,
            diMinus = diMinus,
            bollingerUpper = bbUpper,
            bollingerMiddle = bbMid,
            bollingerLower = bbLower,
            atr14 = atr,
            fibLevels = fibs,
            candlestickPattern = pattern,
            momentumStrength = if (adx > 25.0) "High Trend Strength (ADX ${adx.toInt()})" else "Weak Trend / Compression"
        )
    }

    private fun computeWeeklyExpiryLabel(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val diff = (Calendar.THURSDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DATE, diff)
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(cal.time).uppercase(Locale.US)
    }

    private fun computeMonthlyExpiryLabel(monthOffset: Int = 0): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        if (monthOffset != 0) cal.add(Calendar.MONTH, monthOffset)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            cal.add(Calendar.DATE, -1)
        }
        val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        if (monthOffset == 0 && cal.before(today) && cal.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) {
            return computeMonthlyExpiryLabel(1)
        }
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(cal.time).uppercase(Locale.US)
    }

    private fun computeNextMonthExpiryLabel(): String = computeMonthlyExpiryLabel(1)

    private fun formatCurrentIstShort(): String {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return "${sdf.format(Date())} IST"
    }
}
