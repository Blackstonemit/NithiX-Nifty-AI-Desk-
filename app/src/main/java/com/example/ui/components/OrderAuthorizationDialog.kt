package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.ExecutionSafetyMode
import com.example.model.FinalTradeRecommendation
import com.example.model.IndstocksMarginPreview
import com.example.model.SignalType
import com.example.ui.theme.LocalChartPalette
import java.util.Locale

@Composable
fun OrderAuthorizationDialog(
    recommendation: FinalTradeRecommendation,
    entryPrice: Double,
    availableCapital: Double,
    orderLots: Int,
    onLotsChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    executionMode: ExecutionSafetyMode = ExecutionSafetyMode.PAPER_SIMULATION,
    marginPreview: IndstocksMarginPreview? = null,
    isBrokerBusy: Boolean = false,
    brokerError: String? = null,
    lotSize: Int = 75,
    onConfirmWithOptions: (useSmartGtt: Boolean, orderType: String) -> Unit = { _, _ -> onConfirm() }
) {
    val palette = LocalChartPalette.current
    val effectiveLotSize = lotSize.coerceAtLeast(1)
    val totalQty = orderLots * effectiveLotSize
    val isBullish = recommendation.signal == SignalType.BUY_CE || recommendation.signal == SignalType.BULLISH_FUTURES
    val accentColor = if (isBullish) palette.bullGreen else palette.bearRed

    val estPrice = entryPrice.coerceAtLeast(1.0)
    val totalInvestment = marginPreview?.requiredMargin ?: (totalQty * estPrice)
    val brokerage = orderLots * 40.0
    val stt = (totalQty * estPrice) * 0.000625
    val gst = brokerage * 0.18
    val totalCharges = marginPreview?.brokerageEstimate ?: (brokerage + stt + gst + 5.5)
    val maxPermittedRisk = availableCapital * 0.015

    var useSmartGtt by remember { mutableStateOf(true) }
    var orderType by remember { mutableStateOf("LIMIT") }

    val modeBadgeColor = when (executionMode) {
        ExecutionSafetyMode.PAPER_SIMULATION -> palette.accentCyan
        ExecutionSafetyMode.READ_ONLY_BROKER -> palette.goldAccent
        ExecutionSafetyMode.LIVE_EXECUTION -> palette.bearRed
    }
    val modeBadgeBg = when (executionMode) {
        ExecutionSafetyMode.PAPER_SIMULATION -> palette.accentCyanDim
        ExecutionSafetyMode.READ_ONLY_BROKER -> palette.goldAccentDim
        ExecutionSafetyMode.LIVE_EXECUTION -> palette.bearRedDim
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.5.dp, accentColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .testTag("order_auth_dialog")
        ) {
            // Header: Compliance notice + Safety Mode Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = palette.goldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.order_auth_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(modeBadgeBg)
                        .border(1.dp, modeBadgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = executionMode.badgeLabel,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = modeBadgeColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = stringResource(
                    when (executionMode) {
                        ExecutionSafetyMode.PAPER_SIMULATION -> R.string.order_auth_mode_paper
                        ExecutionSafetyMode.READ_ONLY_BROKER -> R.string.order_auth_mode_readonly
                        ExecutionSafetyMode.LIVE_EXECUTION -> R.string.order_auth_mode_live
                    }
                ),
                fontSize = 10.sp,
                color = if (executionMode == ExecutionSafetyMode.LIVE_EXECUTION) palette.goldAccent else palette.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Contract Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = recommendation.instrument,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.order_auth_signal_ltp_format,
                            recommendation.signal.label,
                            String.format(Locale.US, "%,.2f", estPrice)
                        ),
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lot Size Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.order_auth_position_size),
                        fontSize = 10.sp,
                        color = palette.textMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.order_auth_lots_qty_format, orderLots, totalQty),
                        fontSize = 14.sp,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onLotsChange(orderLots - 1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(palette.surfaceHighlight, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = stringResource(R.string.cd_decrease_lots),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "$orderLots", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { onLotsChange(orderLots + 1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(palette.surfaceHighlight, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_increase_lots),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (executionMode == ExecutionSafetyMode.LIVE_EXECUTION) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("LIMIT", "MARKET").forEach { type ->
                        val selected = orderType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) palette.accentCyanDim else palette.surfaceHighlight)
                                .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                                .clickable { orderType = type }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accentCyan else palette.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { useSmartGtt = !useSmartGtt }
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useSmartGtt,
                        onCheckedChange = { useSmartGtt = it },
                        colors = CheckboxDefaults.colors(checkedColor = palette.goldAccent, checkmarkColor = Color.Black)
                    )
                    Text(
                        text = stringResource(R.string.order_auth_arm_smart_gtt),
                        fontSize = 11.sp,
                        color = palette.goldAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cost & Margin Preview Breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(
                            if (marginPreview != null) R.string.order_auth_indstocks_req_margin else R.string.order_auth_est_capital_req
                        ),
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", totalInvestment)),
                        fontSize = 11.sp,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                marginPreview?.let { mp ->
                    if (mp.spanMargin > 0.0 || mp.exposureMargin > 0.0) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(
                                R.string.order_auth_span_exposure_format,
                                String.format(Locale.US, "%,.0f", mp.spanMargin),
                                String.format(Locale.US, "%,.0f", mp.exposureMargin)
                            ),
                            fontSize = 9.5.sp,
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.order_auth_broker_shortfall), fontSize = 11.sp, color = palette.textSecondary)
                        Text(
                            text = if (mp.shortfall > 0.0) {
                                stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", mp.shortfall))
                            } else {
                                stringResource(R.string.order_auth_covered)
                            },
                            fontSize = 11.sp,
                            color = if (mp.shortfall > 0.0) palette.bearRed else palette.bullGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    mp.chargesBreakdown?.let { cb ->
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(
                                R.string.order_auth_charges_breakdown_format,
                                String.format(Locale.US, "%.1f", cb.brokerage),
                                String.format(Locale.US, "%.2f", cb.gst),
                                String.format(Locale.US, "%.2f", cb.stt + cb.exchangeCharges + cb.stampDuty + cb.sebiTurnoverCharges + cb.ipftCharges)
                            ),
                            fontSize = 9.5.sp,
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.order_auth_brokerage_taxes), fontSize = 11.sp, color = palette.textSecondary)
                    Text(
                        text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%.1f", totalCharges)),
                        fontSize = 11.sp,
                        color = palette.goldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.order_auth_max_risk), fontSize = 11.sp, color = palette.textSecondary)
                    Text(
                        text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.0f", maxPermittedRisk)),
                        fontSize = 11.sp,
                        color = palette.bearRed,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            brokerError?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, fontSize = 10.5.sp, color = palette.bearRed, lineHeight = 14.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = stringResource(R.string.action_cancel), color = palette.textSecondary)
                }

                Button(
                    onClick = { onConfirmWithOptions(useSmartGtt, orderType) },
                    enabled = executionMode != ExecutionSafetyMode.READ_ONLY_BROKER && !isBrokerBusy,
                    modifier = Modifier
                        .weight(1.5f)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_auth_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (executionMode == ExecutionSafetyMode.LIVE_EXECUTION) palette.goldAccent else accentColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isBrokerBusy) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = stringResource(
                                when (executionMode) {
                                    ExecutionSafetyMode.PAPER_SIMULATION -> R.string.order_auth_transmit
                                    ExecutionSafetyMode.READ_ONLY_BROKER -> R.string.order_auth_readonly_locked
                                    ExecutionSafetyMode.LIVE_EXECUTION -> if (useSmartGtt) {
                                        R.string.order_auth_send_live_gtt
                                    } else {
                                        R.string.order_auth_send_live_order
                                    }
                                }
                            ),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
