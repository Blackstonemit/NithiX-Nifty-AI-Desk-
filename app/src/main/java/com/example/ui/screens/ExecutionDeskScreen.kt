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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showLiveRiskDialog by remember { mutableStateOf(false) }

    val (positions, alerts) = remember(state.activeOrders, state.spot, state.optionsChain, state.technicals) {
        val evaluatedPositions = mutableListOf<PositionLifecycle>()
        val generatedAlerts = mutableListOf<LiveTradeAlert>()

        state.activeOrders.forEach { order ->
            val mapped = order.toPositionLifecycle(state, context)
            if (order.status == "CLOSED") {
                evaluatedPositions.add(
                    mapped.copy(
                        status = ExecutionAction.FULL_EXIT,
                        exitReason = context.getString(
                            R.string.execution_closed_reason_format,
                            String.format(Locale.US, "%,.0f", order.netPnl)
                        )
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
                text = stringResource(R.string.execution_screen_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        Text(
            text = stringResource(R.string.execution_screen_subtitle),
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
                Text(text = stringResource(R.string.execution_desk_capital), fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", state.availableCapital)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.execution_unrealized_pnl), fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (openUnrealizedPnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", openUnrealizedPnl)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (openUnrealizedPnl >= 0) palette.bullGreen else palette.bearRed,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = stringResource(R.string.execution_realized_pnl), fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
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
                            text = stringResource(R.string.execution_alerts_title),
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
                        Text(text = stringResource(R.string.execution_active_alerts_format, alerts.size), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (alerts.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (positions.isEmpty()) R.string.execution_no_active_positions
                            else R.string.execution_within_sl_params
                        ),
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
                        text = stringResource(R.string.execution_lifecycle_title_format, positions.size),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.execution_lifecycle_rules),
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
                            text = stringResource(R.string.execution_empty_book),
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
                    Text(text = stringResource(R.string.execution_report_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (executionReport.isEmpty()) {
                    Text(text = stringResource(R.string.execution_no_records), fontSize = 11.sp, color = palette.textMuted)
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
                    Text(text = stringResource(R.string.execution_risk_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bearRed)
                }

                Spacer(modifier = Modifier.height(6.dp))

                val effectiveLot = if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) {
                    indstocksState.activeContractLotSize
                } else 75
                Text(
                    text = stringResource(
                        R.string.execution_risk_body_format,
                        effectiveLot,
                        String.format(Locale.US, "%,.0f", state.availableCapital * 0.015)
                    ),
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
                    Text(stringResource(R.string.execution_live_dialog_title), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.execution_live_dialog_body),
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
                    Text(stringResource(R.string.execution_live_dialog_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveRiskDialog = false }) {
                    Text(stringResource(R.string.execution_live_dialog_dismiss), color = palette.textSecondary)
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
                text = stringResource(R.string.execution_safety_mode_header),
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
                            text = stringResource(R.string.execution_broker_card_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val wsPriceColor = if (indstocksState.wsPriceStatus == IndstocksWebSocketStatus.STREAMING) palette.bullGreen else palette.textMuted
                            val wsOrdColor = if (indstocksState.wsOrderStatus == IndstocksWebSocketStatus.STREAMING) palette.bullGreen else palette.textMuted
                            Text(
                                text = stringResource(R.string.execution_ws_price_format, indstocksState.wsPriceStatus.label),
                                fontSize = 9.sp,
                                color = wsPriceColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = stringResource(R.string.execution_ws_orders_format, indstocksState.wsOrderStatus.label),
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
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_sync_indstocks), tint = palette.accentCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_sync), fontSize = 10.sp, color = palette.accentCyan)
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
                    val execPriceSuffix = evt.executedPrice?.let {
                        stringResource(R.string.execution_ws_exec_price_format, String.format(Locale.US, "%.2f", it))
                    } ?: ""
                    Text(
                        text = stringResource(
                            R.string.execution_ws_order_event_format,
                            evt.orderId,
                            evt.entityName.ifBlank { evt.orderType },
                            evt.statusLabel,
                            execPriceSuffix
                        ),
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
                        Text(stringResource(R.string.execution_avail_margin), fontSize = 8.5.sp, color = palette.textMuted)
                        Text(stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", funds.availableMargin)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bullGreen, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.execution_used_margin), fontSize = 8.5.sp, color = palette.textMuted)
                        Text(stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", funds.usedMargin)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.execution_collateral), fontSize = 8.5.sp, color = palette.textMuted)
                        Text(stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", funds.collateral)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.execution_books_summary_format,
                    indstocksState.positions.size,
                    indstocksState.holdings.size,
                    indstocksState.orderBook.size,
                    indstocksState.tradeBook.size
                ),
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
                        Text(
                            text = stringResource(
                                R.string.execution_pos_details_format,
                                pos.netQty,
                                pos.product,
                                String.format(Locale.US, "%.1f", pos.lastPrice)
                            ),
                            fontSize = 9.5.sp,
                            color = palette.textMuted
                        )
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
                        Text(
                            text = stringResource(R.string.execution_holding_symbol_format, holding.symbol, holding.exchange),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(
                                R.string.execution_holding_details_format,
                                holding.quantity,
                                String.format(Locale.US, "%.1f", holding.avgPrice),
                                String.format(Locale.US, "%.1f", holding.lastPrice)
                            ),
                            fontSize = 9.5.sp,
                            color = palette.textMuted
                        )
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
                            text = stringResource(R.string.execution_order_title_format, ord.txnType, ord.symbol, ord.status),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ord.isSmartOrder) palette.goldAccent else palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(
                                R.string.execution_order_sub_format,
                                ord.orderId,
                                ord.quantity,
                                String.format(Locale.US, "%.1f", ord.price)
                            ),
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
                                Text(stringResource(R.string.action_modify), fontSize = 10.sp, color = palette.accentCyan, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { onCancelOrder(ord.orderId, ord.segment, ord.isSmartOrder) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cancel_broker_order_${ord.orderId}")
                            ) {
                                Text(stringResource(R.string.action_cancel), fontSize = 10.sp, color = palette.bearRed, fontWeight = FontWeight.Bold)
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
                    text = stringResource(
                        if (ord.isSmartOrder) R.string.execution_modify_smart_gtt_format else R.string.execution_modify_order_format,
                        ord.orderId
                    ),
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
                        label = { Text(stringResource(R.string.execution_label_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("modify_order_qty_input")
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text(stringResource(R.string.execution_label_limit_price)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("modify_order_price_input")
                    )
                    if (ord.isSmartOrder) {
                        OutlinedTextField(
                            value = slInput,
                            onValueChange = { slInput = it },
                            label = { Text(stringResource(R.string.execution_label_sl_trigger)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tgtInput,
                            onValueChange = { tgtInput = it },
                            label = { Text(stringResource(R.string.execution_label_target_trigger)) },
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
                    Text(stringResource(R.string.execution_submit_modify), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToModify = null }) {
                    Text(stringResource(R.string.action_cancel), color = palette.textSecondary)
                }
            }
        )
    }
}

private fun ExecutedOrder.toPositionLifecycle(state: DeskState, context: Context): PositionLifecycle {
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
        gammaExposure = context.getString(R.string.execution_gamma_strike_format, parsedStrike.roundToInt()),
        exitReason = context.getString(R.string.execution_status_reason_format, status),
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
                    text = stringResource(R.string.execution_alert_priority_format, alert.priorityLevel),
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
                    text = stringResource(R.string.execution_alert_entry_ltp_format, alert.entryPremium.toInt(), alert.currentPremium.toInt()),
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(
                        R.string.execution_alert_pnl_format,
                        if (alert.unrealizedPnlInr >= 0) "+" else "",
                        alert.unrealizedPnlInr.toInt(),
                        alert.pnlPct.toInt()
                    ),
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
                                contentDescription = stringResource(R.string.cd_close_position),
                                tint = palette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isClosed) {
                    stringResource(R.string.execution_pos_closed_format, pos.exitReason)
                } else {
                    stringResource(R.string.execution_pos_action_format, pos.status.label, pos.exitReason)
                },
                fontSize = 11.sp,
                color = if (isClosed) palette.textMuted else palette.goldAccent
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.execution_pos_sl_trail_format, pos.initialStopLoss.toInt(), pos.trailingStopLoss.toInt()),
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(R.string.execution_pos_targets_format, pos.target1.toInt(), pos.target2.toInt()),
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
    val pnlStr = row["P&L"] ?: stringResource(R.string.label_inr_amount_format, "0")
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
                Text(
                    text = stringResource(R.string.execution_report_strat_strike_format, row["Strategy"] ?: "", row["Strike"] ?: ""),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentCyan
                )
                Text(text = pnlStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isLoss) palette.bearRed else palette.bullGreen, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.execution_report_row_format,
                    row["Entry Premium"] ?: "",
                    row["Current Premium"] ?: "",
                    row["Exit Action"] ?: ""
                ),
                fontSize = 10.sp,
                color = palette.textSecondary
            )
            Text(
                text = stringResource(R.string.execution_report_trigger_format, row["Exit Reason"] ?: ""),
                fontSize = 9.sp,
                color = palette.textMuted
            )
        }
    }
}
