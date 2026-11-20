package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DeskState
import com.example.data.NiftyMarketRepository
import com.example.data.SharedPreferencesIndstocksCredentialStore
import com.example.data.SharedPreferencesMcpConnectorStore
import com.example.data.local.ChartSettingsStore
import com.example.data.local.ChartWorkstationDatabase
import com.example.data.local.InMemoryChartSettingsStore
import com.example.data.local.RoomChartSettingsRepository
import com.example.model.AgentReport
import com.example.model.ExecutionSafetyMode
import com.example.model.FinalTradeRecommendation
import com.example.model.IndstocksBrokerState
import com.example.model.McpAuthType
import com.example.model.McpConnectorsUiState
import com.example.model.McpTransportType
import com.example.model.NseMcpState
import com.example.model.Timeframe
import com.example.model.TradingStyle
import com.example.model.TradingWorkflow
import com.example.network.LoopbackOAuthServer
import com.example.network.McpOAuthClient
import com.example.niftychart.data.local.NiftyOHLCVDao
import com.example.niftychart.di.DatabaseModule
import com.example.niftychart.di.RepositoryModule
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.repository.NiftyMarketRepository as RoomNiftyMarketRepository
import com.example.niftychart.presentation.chart.ChartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SettingsPage {
    ROOT,
    CONNECTORS,
    LLM_AND_THEME
}

class NiftyDeskViewModel(
    private val repository: NiftyMarketRepository = NiftyMarketRepository(),
    chartSettingsStore: ChartSettingsStore = InMemoryChartSettingsStore(),
    private val ohlcvRepository: RoomNiftyMarketRepository? = null,
    ohlcvDao: NiftyOHLCVDao? = null
) : ViewModel() {

    val deskState: StateFlow<DeskState> = repository.deskState
    val chartCoordinator = ChartWorkstationCoordinator(
        scope = viewModelScope,
        settingsStore = chartSettingsStore,
        marketDataRepo = com.example.data.MarketChartDataRepository(ohlcvDao = ohlcvDao),
        indstocksTokenProvider = { indstocksState.value.accessToken }
    )
    val chartState: StateFlow<ChartWorkstationUiState> = chartCoordinator.state
    val ohlcvChartViewModel: ChartViewModel? = ohlcvRepository?.let { repo ->
        ChartViewModel(repository = repo)
    }
    val nseMcpState: StateFlow<NseMcpState> = repository.nseMcpState
    val connectorsState: StateFlow<McpConnectorsUiState> = repository.connectorsState
    val indstocksState: StateFlow<IndstocksBrokerState> = repository.indstocksState

    private val _showMcpDialog = MutableStateFlow(false)
    val showMcpDialog: StateFlow<Boolean> = _showMcpDialog.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _settingsPage = MutableStateFlow(SettingsPage.ROOT)
    val settingsPage: StateFlow<SettingsPage> = _settingsPage.asStateFlow()

    private val _selectedAgent = MutableStateFlow<AgentReport?>(null)
    val selectedAgent: StateFlow<AgentReport?> = _selectedAgent.asStateFlow()

    private val _orderToAuthorize = MutableStateFlow<FinalTradeRecommendation?>(null)
    val orderToAuthorize: StateFlow<FinalTradeRecommendation?> = _orderToAuthorize.asStateFlow()

    private val _orderLots = MutableStateFlow(2)
    val orderLots: StateFlow<Int> = _orderLots.asStateFlow()

    private val _activeTab = MutableStateFlow(0) // 0: Live Terminal, 1: 9-Agent Desk, 2: Options/Futures Desk, 3: Execution, 4: Report
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private var heartbeatCounter = 0
    private val oauthCallbackHelper = McpOAuthClient()
    private var activeLoopbackServer: LoopbackOAuthServer? = null

    init {
        repository.startIndstocksStreams(viewModelScope)
        viewModelScope.launch {
            repository.deskState.collect { ds ->
                chartCoordinator.syncFromDashboardCandles(
                    candles = ds.candles,
                    timeframe = ds.selectedTimeframe,
                    dataSourceLabel = ds.dataSourceLabel,
                    isLive = ds.isLiveMarketData
                )
                if (ohlcvRepository != null && ds.candles.isNotEmpty() &&
                    ds.selectedScenarioName == DeskState.SCENARIO_LIVE_FEED
                ) {
                    val interval = ChartInterval.fromTimeframe(ds.selectedTimeframe)
                    val now = System.currentTimeMillis()
                    val domainBars = ds.candles.map {
                        OHLCV.fromLegacyCandle(it, OHLCV.SYMBOL_NIFTY_50, interval, now)
                    }
                    launch(Dispatchers.IO) {
                        ohlcvRepository.upsertCandles(domainBars)
                    }
                }
            }
        }
        refreshLiveMarketData()
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                heartbeatCounter++
                repository.onHeartbeat()
                if (heartbeatCounter % 30 == 0 &&
                    deskState.value.sessionStatus.isExchangeOpen &&
                    deskState.value.selectedScenarioName == DeskState.SCENARIO_LIVE_FEED
                ) {
                    repository.refreshLiveMarketData(deskState.value.selectedTimeframe)
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _showSettings.value = false
        _activeTab.value = tab
    }

    fun openSettings() {
        _settingsPage.value = SettingsPage.ROOT
        _showSettings.value = true
    }

    fun openConnectorsSettings() {
        _showMcpDialog.value = false
        _settingsPage.value = SettingsPage.CONNECTORS
        _showSettings.value = true
    }

    fun openSettingsPage(page: SettingsPage) {
        _settingsPage.value = page
    }

    fun navigateSettingsBack() {
        if (_settingsPage.value != SettingsPage.ROOT) {
            _settingsPage.value = SettingsPage.ROOT
        } else {
            _showSettings.value = false
        }
    }

    fun closeSettings() {
        _showSettings.value = false
        _settingsPage.value = SettingsPage.ROOT
    }

    fun selectAgent(agent: AgentReport?) {
        _selectedAgent.value = agent
    }

    fun switchScenario(scenario: String) {
        repository.switchScenario(scenario)
        if (scenario == DeskState.SCENARIO_LIVE_FEED) {
            refreshLiveMarketData()
        }
    }

    fun switchTimeframe(tf: Timeframe) {
        repository.switchTimeframe(tf)
        chartCoordinator.setTimeframe(tf)
        if (deskState.value.selectedScenarioName == DeskState.SCENARIO_LIVE_FEED &&
            chartCoordinator.state.value.activeSymbol.symbol == "NIFTY 50"
        ) {
            viewModelScope.launch {
                repository.refreshLiveMarketData(tf)
            }
        }
    }

    fun switchWorkflow(workflow: TradingWorkflow) {
        repository.switchWorkflow(workflow)
    }

    fun switchTradingStyle(style: TradingStyle) {
        repository.switchTradingStyle(style)
    }

    fun toggleStreaming() {
        repository.toggleStreaming()
    }

    fun refreshLiveMarketData() {
        viewModelScope.launch {
            repository.refreshLiveMarketData(deskState.value.selectedTimeframe)
        }
    }

    fun estimateEntryPrice(rec: FinalTradeRecommendation): Double {
        return repository.estimateEntryPriceForRecommendation(rec)
    }

    fun requestOrderAuthorization(rec: FinalTradeRecommendation) {
        _orderToAuthorize.value = rec
        if (indstocksState.value.accessToken.isNotBlank() &&
            indstocksState.value.executionMode != ExecutionSafetyMode.PAPER_SIMULATION
        ) {
            viewModelScope.launch {
                repository.previewIndstocksMarginForRecommendation(rec, _orderLots.value)
            }
        }
    }

    fun dismissOrderDialog() {
        _orderToAuthorize.value = null
    }

    fun updateOrderLots(lots: Int) {
        val clamped = lots.coerceIn(1, 20)
        _orderLots.value = clamped
        val rec = _orderToAuthorize.value
        if (rec != null &&
            indstocksState.value.accessToken.isNotBlank() &&
            indstocksState.value.executionMode != ExecutionSafetyMode.PAPER_SIMULATION
        ) {
            viewModelScope.launch {
                repository.previewIndstocksMarginForRecommendation(rec, clamped)
            }
        }
    }

    fun confirmOrderExecution(
        useSmartGtt: Boolean = false,
        orderType: String = "LIMIT"
    ) {
        val rec = _orderToAuthorize.value ?: return
        when (indstocksState.value.executionMode) {
            ExecutionSafetyMode.PAPER_SIMULATION -> {
                repository.executeAuthorizedTrade(rec, _orderLots.value)
                _orderToAuthorize.value = null
            }
            ExecutionSafetyMode.READ_ONLY_BROKER -> {
                // Read-only mode blocks order placement; dialog remains informative or trader switches mode
                _orderToAuthorize.value = null
            }
            ExecutionSafetyMode.LIVE_EXECUTION -> {
                viewModelScope.launch {
                    val res = repository.submitIndstocksLiveOrder(
                        recommendation = rec,
                        lots = _orderLots.value,
                        useSmartGtt = useSmartGtt,
                        orderType = orderType
                    )
                    if (res.isSuccess) {
                        _orderToAuthorize.value = null
                    }
                }
            }
        }
    }

    fun connectIndstocksWithToken(token: String, clientId: String? = null) {
        viewModelScope.launch {
            repository.saveAndConnectIndstocksToken(token, clientId)
        }
    }

    fun generateIndstocksTokenViaTotp(clientId: String, mpin: String, totp: String) {
        viewModelScope.launch {
            repository.generateIndstocksTokenViaTotp(clientId, mpin, totp)
        }
    }

    fun syncIndstocksPortfolio() {
        viewModelScope.launch {
            repository.syncIndstocksPortfolioAndOrders()
        }
    }

    fun disconnectIndstocks() {
        repository.disconnectIndstocks()
    }

    fun setExecutionSafetyMode(mode: ExecutionSafetyMode, acknowledgeLiveRisk: Boolean) {
        repository.setExecutionSafetyMode(mode, acknowledgeLiveRisk)
        if (mode != ExecutionSafetyMode.PAPER_SIMULATION && indstocksState.value.accessToken.isNotBlank()) {
            viewModelScope.launch {
                repository.syncIndstocksPortfolioAndOrders()
            }
        }
    }

    fun selectIndstocksExpiry(expiry: String) {
        viewModelScope.launch {
            repository.selectIndstocksExpiry(expiry)
        }
    }

    fun modifyIndstocksOrder(
        orderId: String,
        segment: String = "DERIVATIVE",
        isSmartOrder: Boolean = false,
        newQty: Int,
        newLimitPrice: Double,
        newSlTriggerPrice: Double? = null,
        newTgtTriggerPrice: Double? = null
    ) {
        viewModelScope.launch {
            repository.modifyIndstocksOrder(
                orderId = orderId,
                segment = segment,
                isSmartOrder = isSmartOrder,
                newQty = newQty,
                newLimitPrice = newLimitPrice,
                newSlTriggerPrice = newSlTriggerPrice,
                newTgtTriggerPrice = newTgtTriggerPrice
            )
        }
    }

    fun cancelIndstocksOrder(
        orderId: String,
        segment: String = "DERIVATIVE",
        isSmartOrder: Boolean = false
    ) {
        viewModelScope.launch {
            repository.cancelIndstocksOrder(orderId, segment, isSmartOrder)
        }
    }

    fun closePosition(orderId: String) {
        repository.closeOrder(orderId)
    }

    fun openMcpDialog() {
        _showMcpDialog.value = true
    }

    fun dismissMcpDialog() {
        _showMcpDialog.value = false
    }

    fun syncNseMcp(symbol: String = "NIFTY 50") {
        viewModelScope.launch {
            repository.syncNseBhavcopy(symbol)
        }
    }

    fun validateConnectorUrl(rawUrl: String): String? = repository.validateConnectorUrl(rawUrl)

    fun addCustomConnectorAndConnect(
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String> = emptyMap(),
        connectImmediately: Boolean = true,
        oauthClientId: String = ""
    ) {
        val created = repository.addCustomConnector(
            name = name,
            url = url,
            transportType = transportType,
            authType = authType,
            authHeaderName = authHeaderName,
            authSecret = authSecret,
            customHeaders = customHeaders,
            oauthClientId = oauthClientId
        )
        if (connectImmediately) {
            viewModelScope.launch {
                repository.connectAndDiscoverConnector(created.id)
            }
        }
    }

    fun updateCustomConnectorAndConnect(
        connectorId: String,
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String> = emptyMap(),
        connectImmediately: Boolean = true,
        oauthClientId: String = ""
    ) {
        val updated = repository.updateCustomConnector(
            connectorId = connectorId,
            name = name,
            url = url,
            transportType = transportType,
            authType = authType,
            authHeaderName = authHeaderName,
            authSecret = authSecret,
            customHeaders = customHeaders,
            oauthClientId = oauthClientId
        )
        if (updated != null && connectImmediately) {
            viewModelScope.launch {
                repository.connectAndDiscoverConnector(updated.id)
            }
        }
    }

    fun removeCustomConnector(connectorId: String) {
        repository.removeCustomConnector(connectorId)
    }

    fun toggleConnectorEnabled(connectorId: String) {
        repository.toggleConnectorEnabled(connectorId)
    }

    fun connectConnector(connectorId: String) {
        viewModelScope.launch {
            repository.connectAndDiscoverConnector(connectorId)
        }
    }

    fun discoverOAuthMetadata(connectorId: String) {
        viewModelScope.launch {
            repository.discoverOAuthForConnector(connectorId)
        }
    }

    fun startOAuthAuthorization(
        connectorId: String,
        explicitClientId: String = "",
        useCustomSchemeRedirect: Boolean = false,
        onLaunchBrowser: (String) -> Unit
    ) {
        viewModelScope.launch {
            activeLoopbackServer?.close()
            activeLoopbackServer = null

            val redirectUri = if (useCustomSchemeRedirect) {
                McpOAuthClient.CUSTOM_APP_REDIRECT_URI
            } else {
                val server = runCatching {
                    oauthCallbackHelper.startLoopbackCallbackServer { callbackUri ->
                        handleOAuthCallbackUri(callbackUri)
                    }
                }.getOrNull()
                activeLoopbackServer = server
                server?.redirectUri ?: McpOAuthClient.CUSTOM_APP_REDIRECT_URI
            }

            val flow = repository.prepareOAuthAuthorization(
                connectorId = connectorId,
                explicitClientId = explicitClientId,
                redirectUri = redirectUri
            )
            if (flow != null) {
                onLaunchBrowser(flow.authorizationUrl)
            } else {
                activeLoopbackServer?.close()
                activeLoopbackServer = null
            }
        }
    }

    fun handleOAuthCallbackUri(uri: Uri) {
        viewModelScope.launch {
            val ok = repository.completeOAuthWithCallbackOrCode(uri.toString())
            if (ok) {
                activeLoopbackServer?.close()
                activeLoopbackServer = null
            }
        }
    }

    fun completeOAuthWithManualInput(codeOrCallbackUrl: String) {
        viewModelScope.launch {
            val ok = repository.completeOAuthWithCallbackOrCode(codeOrCallbackUrl)
            if (ok) {
                activeLoopbackServer?.close()
                activeLoopbackServer = null
            }
        }
    }

    fun applyManualBearerToken(connectorId: String, token: String) {
        viewModelScope.launch {
            activeLoopbackServer?.close()
            activeLoopbackServer = null
            repository.applyManualBearerToken(connectorId, token)
        }
    }

    fun dismissOAuthAuthorization() {
        activeLoopbackServer?.close()
        activeLoopbackServer = null
        repository.clearActiveOAuthFlow()
    }

    fun executeConnectorTool(connectorId: String, toolName: String, argumentsJson: String) {
        viewModelScope.launch {
            repository.executeConnectorTool(connectorId, toolName, argumentsJson)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopIndstocksStreams()
        activeLoopbackServer?.close()
        activeLoopbackServer = null
    }
}

class NiftyDeskViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appCtx = context.applicationContext
        val store = SharedPreferencesMcpConnectorStore(appCtx)
        val indstocksStore = SharedPreferencesIndstocksCredentialStore(appCtx)
        val repository = NiftyMarketRepository(
            connectorStore = store,
            indstocksStore = indstocksStore
        )
        val chartDb = ChartWorkstationDatabase.getInstance(appCtx)
        val chartSettingsStore = RoomChartSettingsRepository(chartDb)
        val ohlcvDao = DatabaseModule.provideNiftyOhlcvDao(appCtx)
        val ohlcvRepo = RepositoryModule.provideNiftyMarketRepository(
            context = appCtx,
            indstocksTokenProvider = { repository.indstocksState.value.accessToken }
        )
        return NiftyDeskViewModel(
            repository = repository,
            chartSettingsStore = chartSettingsStore,
            ohlcvRepository = ohlcvRepo,
            ohlcvDao = ohlcvDao
        ) as T
    }
}
