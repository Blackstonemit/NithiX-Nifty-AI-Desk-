package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.model.IndstocksConnectionStatus
import com.example.niftychart.presentation.chart.NiftyChartScreen
import com.example.ui.components.NseMcpDeskDialog
import com.example.ui.components.OrderAuthorizationDialog
import com.example.ui.screens.ChartWorkstationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExecutionDeskScreen
import com.example.ui.screens.FuturesDeskScreen
import com.example.ui.screens.MultiAgentDeskScreen
import com.example.ui.screens.OptionsChainScreen
import com.example.ui.screens.ReportAndAuditScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NiftyDeskViewModel
import com.example.ui.viewmodel.NiftyDeskViewModelFactory
import com.example.ui.viewmodel.SettingsPage

class MainActivity : ComponentActivity() {

    private val viewModel: NiftyDeskViewModel by viewModels {
        NiftyDeskViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        handleOAuthIntent(intent)

        setContent {
            val chartState by viewModel.chartState.collectAsStateWithLifecycle()
            MyApplicationTheme(chartThemeMode = chartState.themeMode) {
                val palette = LocalChartPalette.current
                val state by viewModel.deskState.collectAsStateWithLifecycle()
                val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
                val orderToAuth by viewModel.orderToAuthorize.collectAsStateWithLifecycle()
                val orderLots by viewModel.orderLots.collectAsStateWithLifecycle()
                val showMcpDialog by viewModel.showMcpDialog.collectAsStateWithLifecycle()
                val mcpState by viewModel.nseMcpState.collectAsStateWithLifecycle()
                val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
                val settingsPage by viewModel.settingsPage.collectAsStateWithLifecycle()
                val connectorsState by viewModel.connectorsState.collectAsStateWithLifecycle()
                val indstocksState by viewModel.indstocksState.collectAsStateWithLifecycle()

                BackHandler(enabled = showSettings) {
                    viewModel.navigateSettingsBack()
                }
                BackHandler(enabled = !showSettings && chartState.isRoomOhlcvTerminalOpen) {
                    viewModel.chartCoordinator.openRoomOhlcvTerminal(false)
                }
                BackHandler(enabled = !showSettings && !chartState.isRoomOhlcvTerminalOpen && chartState.isFullScreenWorkstation) {
                    viewModel.chartCoordinator.openFullScreenWorkstation(false)
                }
                BackHandler(enabled = !showSettings && !chartState.isRoomOhlcvTerminalOpen && !chartState.isFullScreenWorkstation && activeTab != 0) {
                    viewModel.selectTab(0)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = palette.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!showSettings && !chartState.isFullScreenWorkstation && !chartState.isRoomOhlcvTerminalOpen) {
                            DeskBottomNavigationBar(
                                activeTab = activeTab,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (showSettings) {
                            SettingsScreen(
                                settingsPage = settingsPage,
                                deskState = state,
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
                                },
                                onAddCustomConnectorWithClientId = { name, url, transport, auth, headerName, secret, headers, connectNow, clientId ->
                                    viewModel.addCustomConnectorAndConnect(
                                        name = name,
                                        url = url,
                                        transportType = transport,
                                        authType = auth,
                                        authHeaderName = headerName,
                                        authSecret = secret,
                                        customHeaders = headers,
                                        connectImmediately = connectNow,
                                        oauthClientId = clientId
                                    )
                                },
                                onUpdateCustomConnectorWithClientId = { id, name, url, transport, auth, headerName, secret, headers, connectNow, clientId ->
                                    viewModel.updateCustomConnectorAndConnect(
                                        connectorId = id,
                                        name = name,
                                        url = url,
                                        transportType = transport,
                                        authType = auth,
                                        authHeaderName = headerName,
                                        authSecret = secret,
                                        customHeaders = headers,
                                        connectImmediately = connectNow,
                                        oauthClientId = clientId
                                    )
                                },
                                onDiscoverOAuth = { viewModel.discoverOAuthMetadata(it) },
                                onStartOAuthBrowser = { id, clientId, customScheme ->
                                    viewModel.startOAuthAuthorization(
                                        connectorId = id,
                                        explicitClientId = clientId,
                                        useCustomSchemeRedirect = customScheme,
                                        onLaunchBrowser = { authUrl ->
                                            runCatching {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                                startActivity(browserIntent)
                                            }
                                        }
                                    )
                                },
                                onSubmitOAuthManualInput = { viewModel.completeOAuthWithManualInput(it) },
                                onApplyManualBearerToken = { id, token -> viewModel.applyManualBearerToken(id, token) },
                                onDismissOAuth = { viewModel.dismissOAuthAuthorization() },
                                indstocksState = indstocksState,
                                onConnectIndstocksToken = { token, cid -> viewModel.connectIndstocksWithToken(token, cid) },
                                onGenerateIndstocksTotp = { cid, mpin, totp -> viewModel.generateIndstocksTokenViaTotp(cid, mpin, totp) },
                                onSyncIndstocksPortfolio = { viewModel.syncIndstocksPortfolio() },
                                onDisconnectIndstocks = { viewModel.disconnectIndstocks() },
                                chartState = chartState,
                                onSelectChartTheme = { viewModel.chartCoordinator.setThemeMode(it) },
                                onSelectLlmProvider = { viewModel.chartCoordinator.selectLlmProvider(it) },
                                onSaveLlmConfig = { viewModel.chartCoordinator.saveLlmConfig(it) },
                                onTestLlmConnection = { viewModel.chartCoordinator.testLlmConnection(it) }
                            )
                        } else if (chartState.isRoomOhlcvTerminalOpen && viewModel.ohlcvChartViewModel != null) {
                            val ohlcvVm = viewModel.ohlcvChartViewModel!!
                            val ohlcvState by ohlcvVm.uiState.collectAsStateWithLifecycle()
                            NiftyChartScreen(
                                uiState = ohlcvState,
                                onSelectInterval = { ohlcvVm.selectInterval(it) },
                                onToggleSma = { ohlcvVm.toggleSma(it) },
                                onToggleRsi = { ohlcvVm.toggleRsi() },
                                onSelectRsiPeriod = { ohlcvVm.setRsiPeriod(it) },
                                onToggleLiveStream = { ohlcvVm.toggleLiveStream() },
                                onRefresh = { ohlcvVm.syncActiveInterval(forceRefresh = true) },
                                onToggleTheme = {
                                    ohlcvVm.toggleTheme()
                                    viewModel.chartCoordinator.toggleThemeMode()
                                },
                                onPanByBars = { ohlcvVm.panByBars(it) },
                                onPinchZoom = { ohlcvVm.applyPinchZoom(it) },
                                onZoomIn = { ohlcvVm.zoomIn() },
                                onZoomOut = { ohlcvVm.zoomOut() },
                                onResetViewport = { ohlcvVm.resetViewport() },
                                onSelectCandle = { idx, locked -> ohlcvVm.selectCandle(idx, locked) },
                                onNavigateBack = { viewModel.chartCoordinator.openRoomOhlcvTerminal(false) }
                            )
                        } else if (chartState.isFullScreenWorkstation) {
                            ChartWorkstationScreen(
                                state = chartState,
                                coordinator = viewModel.chartCoordinator,
                                onClose = { viewModel.chartCoordinator.openFullScreenWorkstation(false) },
                                onOpenLlmSettings = {
                                    viewModel.openSettings()
                                    viewModel.openSettingsPage(SettingsPage.LLM_AND_THEME)
                                }
                            )
                        } else {
                            when (activeTab) {
                                0 -> Column(modifier = Modifier.fillMaxSize()) {
                                    DashboardHeaderBar(
                                        onOpenSettings = { viewModel.openSettings() }
                                    )
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToAgents = { viewModel.selectTab(1) },
                                        onNavigateToOptions = { viewModel.selectTab(2) },
                                        onNavigateToExecution = { viewModel.selectTab(3) }
                                    )
                                }

                                1 -> MultiAgentDeskScreen(
                                    agentReports = state.agentReports,
                                    onSelectAgent = { viewModel.selectAgent(it) }
                                )

                                2 -> {
                                    var derivativesSubTab by remember { mutableStateOf(0) } // 0: Options Chain, 1: Futures & FII
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(palette.surface)
                                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("options_sub_tab")
                                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (derivativesSubTab == 0) palette.accentCyan.copy(alpha = 0.18f) else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (derivativesSubTab == 0) palette.accentCyan.copy(alpha = 0.6f) else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { derivativesSubTab = 0 }
                                                    .padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Options Chain",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (derivativesSubTab == 0) palette.accentCyan else palette.textSecondary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("futures_sub_tab")
                                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (derivativesSubTab == 1) palette.accentCyan.copy(alpha = 0.18f) else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (derivativesSubTab == 1) palette.accentCyan.copy(alpha = 0.6f) else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { derivativesSubTab = 1 }
                                                    .padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Futures & FII",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (derivativesSubTab == 1) palette.accentCyan else palette.textSecondary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        if (derivativesSubTab == 0) {
                                            OptionsChainScreen(
                                                optionsChain = state.optionsChain,
                                                spotPrice = state.spot.spotPrice,
                                                availableExpiries = indstocksState.availableExpiries,
                                                selectedExpiry = indstocksState.selectedExpiry,
                                                isBrokerConnected = indstocksState.status == IndstocksConnectionStatus.CONNECTED,
                                                onSelectExpiry = { viewModel.selectIndstocksExpiry(it) }
                                            )
                                        } else {
                                            FuturesDeskScreen(
                                                futures = state.futures,
                                                spotPrice = state.spot.spotPrice
                                            )
                                        }
                                    }
                                }

                                3 -> ExecutionDeskScreen(
                                    state = state,
                                    onCloseOrder = { viewModel.closePosition(it) },
                                    indstocksState = indstocksState,
                                    onSelectExecutionMode = { mode, ack ->
                                        viewModel.setExecutionSafetyMode(mode, ack)
                                    },
                                    onSyncBroker = { viewModel.syncIndstocksPortfolio() },
                                    onCancelBrokerOrder = { oid, seg, smart ->
                                        viewModel.cancelIndstocksOrder(oid, seg, smart)
                                    },
                                    onModifyBrokerOrder = { oid, seg, smart, qty, price, sl, tgt ->
                                        viewModel.modifyIndstocksOrder(oid, seg, smart, qty, price, sl, tgt)
                                    }
                                )

                                4 -> ReportAndAuditScreen(
                                    state = state
                                )
                            }
                        }

                        // Order Authorization Compliance Dialog
                        orderToAuth?.let { rec ->
                            OrderAuthorizationDialog(
                                recommendation = rec,
                                entryPrice = viewModel.estimateEntryPrice(rec),
                                availableCapital = state.availableCapital,
                                orderLots = orderLots,
                                onLotsChange = { viewModel.updateOrderLots(it) },
                                onConfirm = { viewModel.confirmOrderExecution() },
                                onDismiss = { viewModel.dismissOrderDialog() },
                                executionMode = indstocksState.executionMode,
                                marginPreview = indstocksState.marginPreview,
                                isBrokerBusy = indstocksState.isBusy,
                                brokerError = indstocksState.lastError,
                                lotSize = if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) {
                                    indstocksState.activeContractLotSize
                                } else 75,
                                onConfirmWithOptions = { gtt, ordType ->
                                    viewModel.confirmOrderExecution(useSmartGtt = gtt, orderType = ordType)
                                }
                            )
                        }

                        // Official NSE India MCP & Index Tracker Desk Dialog
                        if (showMcpDialog) {
                            NseMcpDeskDialog(
                                mcpState = mcpState,
                                onSyncNow = { viewModel.syncNseMcp() },
                                onOpenConnectorsSettings = { viewModel.openConnectorsSettings() },
                                onDismiss = { viewModel.dismissMcpDialog() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "niftydesk" && data.host == "oauth") {
            viewModel.handleOAuthCallbackUri(data)
        }
    }
}

@Composable
fun DashboardHeaderBar(
    onOpenSettings: () -> Unit
) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("dashboard_header_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.accentCyanDim)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "NITHIX NIFTY DESK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Market Overview & Price Action",
                        fontSize = 9.5.sp,
                        color = palette.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.accentCyan.copy(alpha = 0.14f))
                    .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "DASHBOARD",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                .testTag("open_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = palette.accentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DeskBottomNavigationBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val palette = LocalChartPalette.current
    val tabs = listOf(
        NavigationItem("Dashboard", Icons.Default.Assessment),
        NavigationItem("9 Agents", Icons.Default.Psychology),
        NavigationItem("Options", Icons.Default.Layers),
        NavigationItem("Execution", Icons.Default.ElectricBolt),
        NavigationItem("Report", Icons.Default.Description)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                val isSelected = activeTab == index
                Box(
                    modifier = Modifier
                        .testTag("nav_tab_$index")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) palette.accentCyan.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) palette.accentCyan else palette.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) palette.accentCyan else palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector
)
