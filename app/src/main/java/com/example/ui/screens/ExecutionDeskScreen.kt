package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeskState
import com.example.engine.agents.OptionsExecutionAgent
import com.example.model.AlertAction
import com.example.model.ExecutedOrder
import com.example.model.ExecutionAction
import com.example.model.ExecutionSafetyMode
import com.example.model.IndstocksBrokerState
import com.example.model.IndstocksConnectionStatus
import com.example.model.LiveTradeAlert
import com.example.model.OptionsStrategyType
import com.example.model.PositionLifecycle
import com.example.model.SignalType
import com.example.ui.theme.LocalChartPalette
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import com.example.model.IndstocksOrderRecord
import com.example.model.IndstocksWebSocketStatus

@Composable
fun ExecutionDeskScreen(
    state: DeskState,
    onCloseOrder: (String) -> Unit = {},
    indstocksState: IndstocksBrokerState = IndstocksBrokerState(),
    onSelectExecutionMode: (ExecutionSafetyMode, Boolean) -> Unit = { _, _ -> },
    onSyncBroker: () -> Unit = {},
    onCancelBrokerOrder: (orderId: String, segment: String, isSmartOrder: Boolean) -> Unit = { _, _, _ -> },
    onModifyBrokerOrder: (
        orderId: String,
        segment: String,
        isSmartOrder: Boolean,
        newQty: Int,
        newLimitPrice: Double,
        newSlTrigger: Double?,
        newTgtTrigger: Double?
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val palette = LocalChartPalette.current
    val scrollState = rememberScrollState()
    var showLiveRiskDialog by remember { mutableStateOf(false) }

    val (positions, alerts) = remember(state.activeOrders, state.spot, state.optionsChain, state.technicals) {
        val evaluatedPositions = mutableListOf<PositionLifecycle>()
        val generatedAlerts = mutableListOf<LiveTradeAlert>()

        state.activeOrders.forEach { order ->
            val mapped = order.toPositionLifecycle(state)
            if (order.status == "CLOSED") {
                evaluatedPositions.add(
                    mapped.copy(
                        status = ExecutionAction.FULL_EXIT,
                        exitReason = "Position closed by trader. Net P&L: ₹${String.format(Locale.US, "%,.0f", order.netPnl)}."
                    )
                )
            } else {
                val (updatedPos, alert) = OptionsExecutionAgent.evaluatePositionLifecycle(
                    position = mapped,
                    spot = state.spot,
                    chain = state.optionsChain,
                    tech = state.technicals
                )
                // Keep exact order P&L synced when live quotes match or preserve order live simulation P&L
                val syncedPos = updatedPos.copy(
                    currentPremium = order.currentPrice,
                    unrealizedPnl = order.netPnl,
                    returnPct = if (order.entryPrice > 0.0 && order.quantity > 0) {
                        (order.netPnl / (order.entryPrice * order.quantity)) * 100.0
                    } else updatedPos.returnPct
                )
                evaluatedPositions.add(syncedPos)
                if (alert != null) {
                    generatedAlerts.add(alert)
                }
            }
        }
        evaluatedPositions to generatedAlerts
    }

    val executionReport = remember(positions) {
        OptionsExecutionAgent.generateTradeExecutionReport(positions)
    }
    val openUnrealizedPnl = state.activeOrders.filter { it.status != "CLOSED" }.sumOf { it.netPnl }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(12.dp)
            .testTag("execution_desk_screen")
    ) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OPTIONS EXECUTION & POSITION DESK",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        Text(
            text = "Live trade lifecycle monitor synchronized with authorized F&O orders & trailing risk exits.",
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 0. 3-Mode Execution Safety Switcher (Paper Sim / Broker Read-Only / Live + Smart GTT)
        ExecutionSafetyModeCard(
            indstocksState = indstocksState,
            onSelectMode = { mode ->
                if (mode == ExecutionSafetyMode.LIVE_EXECUTION && !indstocksState.liveTradingRiskAcknowledged) {
                    showLiveRiskDialog = true
                } else {
                    onSelectExecutionMode(mode, indstocksState.liveTradingRiskAcknowledged)
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Live Capital & Realized/Unrealized Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "DESK CAPITAL", fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                Text(
                    text = "₹${String.format(Locale.US, "%,.0f", state.availableCapital)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "UNREALIZED P&L", fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (openUnrealizedPnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", openUnrealizedPnl)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (openUnrealizedPnl >= 0) palette.bullGreen else palette.bearRed,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "REALIZED P&L", fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (state.totalRealizedPnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", state.totalRealizedPnl)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.totalRealizedPnl >= 0) palette.bullGreen else palette.bearRed,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Live Trade Monitoring Alerts Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.goldAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE TRADE MONITORING ALERTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.goldAccentDim)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "${alerts.size} ACTIVE ALERTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (alerts.isEmpty()) {
                    Text(
                        text = if (positions.isEmpty()) {
                            "No active positions yet. Authorize a signal on Terminal to track real-time lifecycle alerts."
                        } else {
                            "All active orders are within standard SL / Target parameters."
                        },
                        fontSize = 11.sp,
                        color = palette.textMuted
                    )
                } else {
                    alerts.forEach { alert ->
                        AlertItemView(alert = alert)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Live Positions Lifecycle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "POSITION LIFECYCLE (${positions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "1-SL → 2-Inval → 3-Rev → 4-Target → 5-Trail",
                        fontSize = 9.sp,
                        color = palette.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (positions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceHighlight)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No authorized trades in execution book. Tap AUTHORIZE ORDER from Terminal when conviction >= 60.",
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                } else {
                    positions.forEachIndexed { idx, pos ->
                        val order = state.activeOrders.getOrNull(idx)
                        PositionLifecycleItem(
                            pos = pos,
                            isClosed = order?.status == "CLOSED",
                            onClose = { order?.let { onCloseOrder(it.id) } }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Trade Execution Report Table
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "TRADE EXECUTION REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (executionReport.isEmpty()) {
                    Text(text = "No execution records available.", fontSize = 11.sp, color = palette.textMuted)
                } else {
                    executionReport.forEach { row ->
                        ExecutionReportItem(row = row)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Risk Control Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.bearRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = palette.bearRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "OPTIONS & FUTURES RISK MANAGEMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bearRed)
                }

                Spacer(modifier = Modifier.height(6.dp))

                val effectiveLot = if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) {
                    indstocksState.activeContractLotSize
                } else 75
                Text(
                    text = "• Margin Rule: Strict defined-risk sizing per NIFTY lot ($effectiveLot Qty).\n" +
                        "• Gamma Exposure: Automatic review if ATM straddle expands > 15%.\n" +
                        "• Hard Prohibition: Never average losing positions without validated hedge.\n" +
                        "• Max Acceptable Portfolio Loss: Capped at 1.5% (₹${String.format(Locale.US, "%,.0f", state.availableCapital * 0.015)}) of desk capital.",
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. INDstocks Broker Portfolio, Order Book & Trade Book Panel
        if (indstocksState.accessToken.isNotBlank() ||
            indstocksState.status == IndstocksConnectionStatus.CONNECTED ||
            indstocksState.holdings.isNotEmpty() ||
            indstocksState.positions.isNotEmpty() ||
            indstocksState.orderBook.isNotEmpty()
        ) {
            IndstocksPortfolioAndBooksCard(
                indstocksState = indstocksState,
                onSync = onSyncBroker,
                onCancelOrder = onCancelBrokerOrder,
                onModifyOrder = onModifyBrokerOrder
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLiveRiskDialog) {
        AlertDialog(
            onDismissRequest = { showLiveRiskDialog = false },
            containerColor = palette.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = palette.bearRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Live INDstocks Orders?", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Live + Smart GTT mode transmits real-money F&O orders to INDstocks REST API (/order and /smart/order).\n\n" +
                        "Note: Per NSE compliance rules, INDstocks live order placement requires your public static IP to be whitelisted in the INDstocks dashboard. " +
                        "You will still review every trade in the confirmation dialog before submission.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveRiskDialog = false
                        onSelectExecutionMode(ExecutionSafetyMode.LIVE_EXECUTION, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.goldAccent, contentColor = Color.Black),
                    modifier = Modifier.testTag("confirm_live_risk_button")
                ) {
                    Text("I Understand — Unlock Live", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveRiskDialog = false }) {
                    Text("Stay in Paper Sim", color = palette.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ExecutionSafetyModeCard(
    indstocksState: IndstocksBrokerState,
    onSelectMode: (ExecutionSafetyMode) -> Unit
) {
    val palette = LocalChartPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("execution_safety_mode_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXECUTION SAFETY MODE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = indstocksState.executionMode.badgeLabel,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when (indstocksState.executionMode) {
                    ExecutionSafetyMode.PAPER_SIMULATION -> palette.accentCyan
                    ExecutionSafetyMode.READ_ONLY_BROKER -> palette.goldAccent
                    ExecutionSafetyMode.LIVE_EXECUTION -> palette.bearRed
                },
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExecutionSafetyMode.values().forEach { mode ->
                val selected = indstocksState.executionMode == mode
                val activeColor = when (mode) {
                    ExecutionSafetyMode.PAPER_SIMULATION -> palette.accentCyan
                    ExecutionSafetyMode.READ_ONLY_BROKER -> palette.goldAccent
                    ExecutionSafetyMode.LIVE_EXECUTION -> palette.bearRed
                }
                val activeBg = when (mode) {
                    ExecutionSafetyMode.PAPER_SIMULATION -> palette.accentCyanDim
                    ExecutionSafetyMode.READ_ONLY_BROKER -> palette.goldAccentDim
                    ExecutionSafetyMode.LIVE_EXECUTION -> palette.bearRedDim
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) activeBg else palette.surfaceVariant)
                        .border(1.dp, if (selected) activeColor else palette.border, RoundedCornerShape(8.dp))
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("execution_mode_${mode.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.badgeLabel,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (selected) activeColor else palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = indstocksState.executionMode.description,
            fontSize = 10.sp,
            color = palette.textMuted
        )
    }
}

@Composable
private fun IndstocksPortfolioAndBooksCard(
    indstocksState: IndstocksBrokerState,
    onSync: () -> Unit,
    onCancelOrder: (orderId: String, segment: String, isSmartOrder: Boolean) -> Unit,
    onModifyOrder: (
        orderId: String,
        segment: String,
        isSmartOrder: Boolean,
        newQty: Int,
        newLimitPrice: Double,
        newSlTrigger: Double?,
        newTgtTrigger: Double?
    ) -> Unit
) {
    val palette = LocalChartPalette.current
    var orderToModify by remember { mutableStateOf<IndstocksOrderRecord?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, palette.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .testTag("indstocks_portfolio_card"),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "INDSTOCKS PORTFOLIO & BOOKS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val wsPriceColor = if (indstocksState.wsPriceStatus == IndstocksWebSocketStatus.STREAMING) palette.bullGreen else palette.textMuted
                            val wsOrdColor = if (indstocksState.wsOrderStatus == IndstocksWebSocketStatus.STREAMING) palette.bullGreen else palette.textMuted
                            Text(
                                text = "Price: ${indstocksState.wsPriceStatus.label}",
                                fontSize = 9.sp,
                                color = wsPriceColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• Orders: ${indstocksState.wsOrderStatus.label}",
                                fontSize = 9.sp,
                                color = wsOrdColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onSync,
                    enabled = !indstocksState.isBusy,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync INDstocks", tint = palette.accentCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync", fontSize = 10.sp, color = palette.accentCyan)
                }
            }

            indstocksState.lastOrderUpdateEvent?.let { evt ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accentCyanDim)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "WS #${evt.orderId} (${evt.entityName.ifBlank { evt.orderType }}): ${evt.statusLabel}" +
                            (evt.executedPrice?.let { " @ ₹${String.format(Locale.US, "%.2f", it)}" } ?: ""),
                        fontSize = 9.5.sp,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            indstocksState.funds?.let { funds ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceHighlight)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("AVAIL MARGIN", fontSize = 8.5.sp, color = palette.textMuted)
                        Text("₹${String.format(Locale.US, "%,.0f", funds.availableMargin)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bullGreen, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("USED", fontSize = 8.5.sp, color = palette.textMuted)
                        Text("₹${String.format(Locale.US, "%,.0f", funds.usedMargin)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("COLLATERAL", fontSize = 8.5.sp, color = palette.textMuted)
                        Text("₹${String.format(Locale.US, "%,.0f", funds.collateral)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Positions (${indstocksState.positions.size}) • Holdings (${indstocksState.holdings.size}) • " +
                    "Orders (${indstocksState.orderBook.size}) • Trades (${indstocksState.tradeBook.size})",
                fontSize = 10.sp,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )

            indstocksState.positions.take(5).forEach { pos ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceHighlight)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pos.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                        Text("Qty: ${pos.netQty} • ${pos.product} • LTP ₹${String.format(Locale.US, "%.1f", pos.lastPrice)}", fontSize = 9.5.sp, color = palette.textMuted)
                    }
                    Text(
                        text = "${if (pos.mtm >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", pos.mtm)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pos.mtm >= 0) palette.bullGreen else palette.bearRed,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            indstocksState.holdings.take(5).forEach { holding ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${holding.symbol} (${holding.exchange})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                        Text("Qty: ${holding.quantity} • Avg ₹${String.format(Locale.US, "%.1f", holding.avgPrice)} • LTP ₹${String.format(Locale.US, "%.1f", holding.lastPrice)}", fontSize = 9.5.sp, color = palette.textMuted)
                    }
                    Text(
                        text = "${if (holding.pnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", holding.pnl)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (holding.pnl >= 0) palette.bullGreen else palette.bearRed,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            indstocksState.orderBook.take(5).forEach { ord ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceHighlight)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ord.txnType} ${ord.symbol} (${ord.status})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ord.isSmartOrder) palette.goldAccent else palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "#${ord.orderId} • Qty ${ord.quantity} @ ₹${String.format(Locale.US, "%.1f", ord.price)}",
                            fontSize = 9.5.sp,
                            color = palette.textMuted
                        )
                    }
                    val isModifiable = ord.status.contains("OPEN", true) ||
                        ord.status.contains("PENDING", true) ||
                        ord.status.contains("TRIGGER", true) ||
                        ord.status.contains("CREATED", true) ||
                        ord.status.contains("RECEIVED", true)
                    if (indstocksState.executionMode == ExecutionSafetyMode.LIVE_EXECUTION && isModifiable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(
                                onClick = { orderToModify = ord },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("modify_broker_order_${ord.orderId}")
                            ) {
                                Text("Modify", fontSize = 10.sp, color = palette.accentCyan, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { onCancelOrder(ord.orderId, ord.segment, ord.isSmartOrder) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cancel_broker_order_${ord.orderId}")
                            ) {
                                Text("Cancel", fontSize = 10.sp, color = palette.bearRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    orderToModify?.let { ord ->
        var qtyInput by remember(ord.orderId) { mutableStateOf(ord.quantity.toString()) }
        var priceInput by remember(ord.orderId) { mutableStateOf(String.format(Locale.US, "%.2f", ord.price)) }
        var slInput by remember(ord.orderId) {
            mutableStateOf(if (ord.slTriggerPrice > 0.0) String.format(Locale.US, "%.2f", ord.slTriggerPrice) else "")
        }
        var tgtInput by remember(ord.orderId) {
            mutableStateOf(if (ord.tgtTriggerPrice > 0.0) String.format(Locale.US, "%.2f", ord.tgtTriggerPrice) else "")
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accentCyan,
            unfocusedBorderColor = palette.border,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            focusedLabelColor = palette.accentCyan,
            unfocusedLabelColor = palette.textSecondary
        )

        AlertDialog(
            onDismissRequest = { orderToModify = null },
            containerColor = palette.surface,
            title = {
                Text(
                    text = if (ord.isSmartOrder) "Modify Smart GTT #${ord.orderId}" else "Modify Order #${ord.orderId}",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ord.symbol, fontSize = 11.sp, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) qtyInput = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("modify_order_qty_input")
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Limit Price (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("modify_order_price_input")
                    )
                    if (ord.isSmartOrder) {
                        OutlinedTextField(
                            value = slInput,
                            onValueChange = { slInput = it },
                            label = { Text("SL Trigger Price (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tgtInput,
                            onValueChange = { tgtInput = it },
                            label = { Text("Target Trigger Price (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedQty = qtyInput.toIntOrNull() ?: ord.quantity
                        val parsedPrice = priceInput.toDoubleOrNull() ?: ord.price
                        val parsedSl = slInput.toDoubleOrNull()
                        val parsedTgt = tgtInput.toDoubleOrNull()
                        orderToModify = null
                        onModifyOrder(ord.orderId, ord.segment, ord.isSmartOrder, parsedQty, parsedPrice, parsedSl, parsedTgt)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black),
                    modifier = Modifier.testTag("confirm_modify_order_button")
                ) {
                    Text("Submit Modify", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToModify = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}

private fun ExecutedOrder.toPositionLifecycle(state: DeskState): PositionLifecycle {
    val strategy = when (signal) {
        SignalType.BUY_PE, SignalType.BEARISH_FUTURES -> OptionsStrategyType.BUY_PE
        else -> OptionsStrategyType.BUY_CE
    }
    val parsedStrike = Regex("""\b(2\d{4})\b""").find(instrument)?.groupValues?.get(1)?.toDoubleOrNull()
        ?: state.optionsChain.atmStrike
    val t1 = entryPrice + max(20.0, (targetPrice - entryPrice) * 0.65)
    val t2 = targetPrice
    val t3 = targetPrice + max(25.0, (targetPrice - entryPrice) * 0.75)

    return PositionLifecycle(
        positionId = id,
        strategy = strategy,
        instrument = instrument,
        strike = parsedStrike,
        expiry = state.optionsChain.expiryDate,
        lots = lots,
        quantity = quantity,
        entryPremium = entryPrice,
        currentPremium = currentPrice,
        initialStopLoss = stopLossPrice,
        trailingStopLoss = stopLossPrice,
        target1 = t1,
        target2 = t2,
        target3 = t3,
        unrealizedPnl = netPnl,
        returnPct = if (entryPrice > 0.0 && quantity > 0) (netPnl / (entryPrice * quantity)) * 100.0 else 0.0,
        status = ExecutionAction.HOLD,
        marginRequired = entryPrice * quantity,
        gammaExposure = "Live Strike ${parsedStrike.roundToInt()} Tracking",
        exitReason = "Status: $status",
        openTimestamp = timestamp
    )
}

@Composable
private fun AlertItemView(alert: LiveTradeAlert) {
    val palette = LocalChartPalette.current
    val alertColor = when (alert.alertType) {
        AlertAction.STOP_LOSS_HIT, AlertAction.EXIT_FULL_POSITION -> palette.bearRed
        AlertAction.BOOK_50_PROFIT, AlertAction.BOOK_75_PROFIT -> palette.bullGreen
        AlertAction.TRAIL_STOP_LOSS -> palette.goldAccent
        else -> palette.accentCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceHighlight)
            .border(1.dp, alertColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(alertColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = alert.alertType.label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = alertColor)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = alert.instrument, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                }

                Text(
                    text = "P${alert.priorityLevel} Trigger",
                    fontSize = 9.sp,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = alert.reason, fontSize = 11.sp, color = palette.textSecondary, lineHeight = 15.sp)

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Entry: ₹${alert.entryPremium.toInt()} | LTP: ₹${alert.currentPremium.toInt()}",
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "P&L: ${if (alert.unrealizedPnlInr >= 0) "+" else ""}₹${alert.unrealizedPnlInr.toInt()} (${alert.pnlPct.toInt()}%)",
                    fontSize = 10.sp,
                    color = if (alert.unrealizedPnlInr >= 0) palette.bullGreen else palette.bearRed,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PositionLifecycleItem(
    pos: PositionLifecycle,
    isClosed: Boolean,
    onClose: () -> Unit
) {
    val palette = LocalChartPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceHighlight)
            .padding(10.dp)
            .testTag("execution_position_${pos.positionId}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pos.instrument,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if (pos.unrealizedPnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", pos.unrealizedPnl)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pos.unrealizedPnl >= 0) palette.bullGreen else palette.bearRed,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!isClosed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_order_${pos.positionId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Position",
                                tint = palette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isClosed) "CLOSED | ${pos.exitReason}" else "Action: ${pos.status.label} | ${pos.exitReason}",
                fontSize = 11.sp,
                color = if (isClosed) palette.textMuted else palette.goldAccent
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "SL: ₹${pos.initialStopLoss.toInt()} | Trail: ₹${pos.trailingStopLoss.toInt()}",
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "T1: ₹${pos.target1.toInt()} | T2: ₹${pos.target2.toInt()}",
                    fontSize = 10.sp,
                    color = palette.bullGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ExecutionReportItem(row: Map<String, String>) {
    val palette = LocalChartPalette.current
    val pnlStr = row["P&L"] ?: "₹0"
    val isLoss = pnlStr.startsWith("-")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceHighlight)
            .padding(8.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "${row["Strategy"]} (Strike ${row["Strike"]})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan)
                Text(text = pnlStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isLoss) palette.bearRed else palette.bullGreen, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Entry: ${row["Entry Premium"]} | Current: ${row["Current Premium"]} | Action: ${row["Exit Action"]}", fontSize = 10.sp, color = palette.textSecondary)
            Text(text = "Trigger: ${row["Exit Reason"]}", fontSize = 9.sp, color = palette.textMuted)
        }
    }
}
