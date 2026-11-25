package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.HeaderTicker
import com.example.ui.components.NiftyDashboardView
import com.example.ui.components.SignalCard
import com.example.ui.theme.LocalChartPalette
import com.example.ui.viewmodel.NiftyDeskViewModel
import com.example.ui.viewmodel.WorkstationInspectorTab

/**
 * Unified DashboardScreen combining real-time NIFTY 50 spot price, India VIX,
 * native Compose CandlestickChart + synchronized Volume Bar Chart, market trend summary,
 * AI consensus SignalCard, Execution Desk shortcut, and adaptive wide-screen layout.
 */
@Composable
fun DashboardScreen(
    viewModel: NiftyDeskViewModel,
    onNavigateToAgents: () -> Unit = {},
    onNavigateToOptions: () -> Unit = {},
    onNavigateToExecution: () -> Unit = {}
) {
    val state by viewModel.deskState.collectAsStateWithLifecycle()
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val palette = LocalChartPalette.current
    val activeOrderCount = state.activeOrders.count { it.status == "ACTIVE" }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("dashboard_screen")
    ) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            val leftScrollState = rememberScrollState()
            val rightScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .verticalScroll(leftScrollState)
                ) {
                    HeaderTicker(
                        spot = state.spot,
                        futures = state.futures,
                        isStreaming = state.isLiveStreaming,
                        selectedScenario = state.selectedScenarioName,
                        sessionStatus = state.sessionStatus,
                        onToggleStreaming = { viewModel.toggleStreaming() },
                        onSelectScenario = { viewModel.switchScenario(it) },
                        latencyMs = state.finalRecommendation.latencyMs,
                        onOpenMcpDesk = { viewModel.openMcpDialog() },
                        isLiveMarketData = state.isLiveMarketData,
                        isRefreshing = state.isRefreshing,
                        lastSyncLabel = state.lastSyncLabel,
                        onRefreshLive = { viewModel.refreshLiveMarketData() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NiftyDashboardView(
                        spot = state.spot,
                        futures = state.futures,
                        optionsChain = state.optionsChain,
                        technicals = state.technicals,
                        smartMoney = state.smartMoney,
                        candles = state.candles,
                        selectedTimeframe = state.selectedTimeframe,
                        onSelectTimeframe = { viewModel.switchTimeframe(it) },
                        sessionStatus = state.sessionStatus,
                        isStreaming = state.isLiveStreaming,
                        isLiveMarketData = state.isLiveMarketData,
                        dataSourceLabel = state.dataSourceLabel,
                        latencyMs = state.finalRecommendation.latencyMs,
                        onNavigateToAgents = onNavigateToAgents,
                        onNavigateToOptions = onNavigateToOptions,
                        workstationState = chartState,
                        onExpandWorkstation = { viewModel.chartCoordinator.openFullScreenWorkstation(true) },
                        onSelectChartType = { viewModel.chartCoordinator.setChartType(it) },
                        onOpenAiAnalyst = {
                            viewModel.chartCoordinator.openFullScreenWorkstation(true)
                            viewModel.chartCoordinator.selectInspectorTab(WorkstationInspectorTab.AI_ANALYST)
                        },
                        onOpenOhlcvTerminal = {
                            viewModel.chartCoordinator.openRoomOhlcvTerminal(true)
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .verticalScroll(rightScrollState)
                ) {
                    SignalCard(
                        recommendation = state.finalRecommendation,
                        onAuthorizeTrade = { viewModel.requestOrderAuthorization(state.finalRecommendation) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExecutionDeskShortcutCard(
                        activeOrderCount = activeOrderCount,
                        onNavigateToExecution = onNavigateToExecution
                    )
                }
            }
        } else {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                HeaderTicker(
                    spot = state.spot,
                    futures = state.futures,
                    isStreaming = state.isLiveStreaming,
                    selectedScenario = state.selectedScenarioName,
                    sessionStatus = state.sessionStatus,
                    onToggleStreaming = { viewModel.toggleStreaming() },
                    onSelectScenario = { viewModel.switchScenario(it) },
                    latencyMs = state.finalRecommendation.latencyMs,
                    onOpenMcpDesk = { viewModel.openMcpDialog() },
                    isLiveMarketData = state.isLiveMarketData,
                    isRefreshing = state.isRefreshing,
                    lastSyncLabel = state.lastSyncLabel,
                    onRefreshLive = { viewModel.refreshLiveMarketData() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                NiftyDashboardView(
                    spot = state.spot,
                    futures = state.futures,
                    optionsChain = state.optionsChain,
                    technicals = state.technicals,
                    smartMoney = state.smartMoney,
                    candles = state.candles,
                    selectedTimeframe = state.selectedTimeframe,
                    onSelectTimeframe = { viewModel.switchTimeframe(it) },
                    sessionStatus = state.sessionStatus,
                    isStreaming = state.isLiveStreaming,
                    isLiveMarketData = state.isLiveMarketData,
                    dataSourceLabel = state.dataSourceLabel,
                    latencyMs = state.finalRecommendation.latencyMs,
                    onNavigateToAgents = onNavigateToAgents,
                    onNavigateToOptions = onNavigateToOptions,
                    workstationState = chartState,
                    onExpandWorkstation = { viewModel.chartCoordinator.openFullScreenWorkstation(true) },
                    onSelectChartType = { viewModel.chartCoordinator.setChartType(it) },
                    onOpenAiAnalyst = {
                        viewModel.chartCoordinator.openFullScreenWorkstation(true)
                        viewModel.chartCoordinator.selectInspectorTab(WorkstationInspectorTab.AI_ANALYST)
                    },
                    onOpenOhlcvTerminal = {
                        viewModel.chartCoordinator.openRoomOhlcvTerminal(true)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SignalCard(
                    recommendation = state.finalRecommendation,
                    onAuthorizeTrade = { viewModel.requestOrderAuthorization(state.finalRecommendation) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ExecutionDeskShortcutCard(
                    activeOrderCount = activeOrderCount,
                    onNavigateToExecution = onNavigateToExecution
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ExecutionDeskShortcutCard(
    activeOrderCount: Int,
    onNavigateToExecution: () -> Unit
) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable { onNavigateToExecution() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("dashboard_execution_link"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                tint = palette.goldAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.execution_desk_active_count, activeOrderCount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(R.string.execution_desk_subtitle),
                    fontSize = 9.5.sp,
                    color = palette.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_open_execution_desk),
            tint = palette.accentCyan,
            modifier = Modifier.size(18.dp)
        )
    }
}
