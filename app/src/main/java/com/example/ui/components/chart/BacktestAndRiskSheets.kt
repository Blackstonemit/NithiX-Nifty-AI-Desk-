package com.example.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestConfig
import com.example.model.BacktestStrategyId
import com.example.model.MarketBias
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState
import java.util.Locale
import kotlin.math.max

@Composable
fun MtfAndSignalPanel(
    state: ChartWorkstationUiState,
    onRefreshMtf: () -> Unit
) {
    val palette = LocalChartPalette.current
    val sig = state.technicalSignal
    val mtf = state.mtfConfluence
    val ratingColor = when {
        sig.score >= 20 -> palette.bullGreen
        sig.score <= -20 -> palette.bearRed
        else -> palette.goldAccent
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_mtf_signal_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, ratingColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RULE-BASED TECHNICAL SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, style = TabularNumericStyle)
                Text("${sig.rating} (${if (sig.score > 0) "+" else ""}${sig.score}/100)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ratingColor, style = TabularNumericStyle)
            }
            Text(
                "Trend: ${sig.trendScore} | Momentum: ${sig.momentumScore} | Volume: ${sig.volumeScore} | Structure: ${sig.structureScore}",
                fontSize = 10.5.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
            Text(
                "Invalidation Price: ₹${String.format(Locale.US, "%,.2f", sig.invalidationPrice)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.bearRed,
                style = TabularNumericStyle
            )
            sig.reasons.forEach { r ->
                Text("• $r", fontSize = 10.5.sp, color = palette.textPrimary)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MULTI-TIMEFRAME CONFLUENCE (${mtf.alignmentScorePct}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
            OutlinedButton(onClick = onRefreshMtf) {
                Text("Sync MTF", fontSize = 11.sp)
            }
        }

        mtf.divergenceAlert?.let { alert ->
            Text(
                text = alert,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.goldAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.goldAccentDim)
                    .padding(8.dp)
            )
        }

        if (mtf.rows.isEmpty()) {
            Text("Tap 'Sync MTF' to evaluate live 1D, 4H, 1H, and 15m alignment.", fontSize = 11.sp, color = palette.textMuted)
        } else {
            mtf.rows.forEach { row ->
                val col = when (row.bias) {
                    MarketBias.BULLISH -> palette.bullGreen
                    MarketBias.BEARISH -> palette.bearRed
                    else -> palette.goldAccent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${row.timeframe.label} · ${row.emaAlignment}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, style = TabularNumericStyle)
                        Text("RSI ${String.format(Locale.US, "%.1f", row.rsi)} · Supertrend ${if (row.supertrendBullish) "Bull" else "Bear"}", fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
                    }
                    Text(row.trendLabel.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = col, style = TabularNumericStyle)
                }
            }
        }
    }
}

@Composable
fun RiskCalculatorPanel(
    state: ChartWorkstationUiState,
    onUpdateRisk: (capital: Double?, riskPct: Double?, rrRatio: Double?, customSl: Double?, clearCustomSl: Boolean, showOnChart: Boolean?) -> Unit
) {
    val palette = LocalChartPalette.current
    val plan = state.riskPlan
    var capInput by remember(state.riskCapital) { mutableStateOf(state.riskCapital.toInt().toString()) }
    var pctInput by remember(state.riskPct) { mutableStateOf(state.riskPct.toString()) }
    var slInput by remember(state.customStopLoss) { mutableStateOf(state.customStopLoss?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_risk_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Plot Entry / SL / Target Lines on Chart", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Switch(checked = state.showRiskOnChart, onCheckedChange = { onUpdateRisk(null, null, null, null, false, it) })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = capInput,
                onValueChange = {
                    capInput = it
                    it.toDoubleOrNull()?.let { c -> onUpdateRisk(c, null, null, null, false, null) }
                },
                label = { Text("Capital (₹)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = pctInput,
                onValueChange = {
                    pctInput = it
                    it.toDoubleOrNull()?.let { r -> onUpdateRisk(null, r, null, null, false, null) }
                },
                label = { Text("Risk %", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(0.7f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = slInput,
                onValueChange = {
                    slInput = it
                    val v = it.toDoubleOrNull()
                    if (v != null && v > 0.0) onUpdateRisk(null, null, null, v, false, null)
                },
                label = { Text("Custom Stop-Loss (₹)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                slInput = ""
                onUpdateRisk(null, null, null, plan.atrStop15x, false, true)
            }) { Text("1.5x ATR", fontSize = 10.sp) }
            OutlinedButton(onClick = {
                slInput = ""
                onUpdateRisk(null, null, null, plan.atrStop20x, false, true)
            }) { Text("2.0x ATR", fontSize = 10.sp) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "POSITION SIZING PLAN (${if (plan.isLong) "LONG BIAS" else "SHORT BIAS"})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentCyan,
                style = TabularNumericStyle
            )
            Text("Max Risk Budget: ₹${String.format(Locale.US, "%,.0f", plan.riskAmountInr)} (${plan.riskPct}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, style = TabularNumericStyle)
            Text("Recommended Size: ${plan.recommendedShares} qty (${plan.recommendedNiftyLots} NIFTY lots @ 25)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.goldAccent, style = TabularNumericStyle)
            Text("Notional Exposure: ₹${String.format(Locale.US, "%,.0f", plan.positionValueInr)}", fontSize = 11.sp, color = palette.textSecondary, style = TabularNumericStyle)
            Spacer(Modifier.height(2.dp))
            Text(
                "Entry ₹${String.format(Locale.US, "%,.2f", plan.entryPrice)} | SL ₹${String.format(Locale.US, "%,.2f", plan.stopLossPrice)}",
                fontSize = 11.sp,
                color = palette.bearRed,
                style = TabularNumericStyle
            )
            Text(
                "Targets: 1:1.5 ₹${String.format(Locale.US, "%,.1f", plan.target1)} | 1:2 ₹${String.format(Locale.US, "%,.1f", plan.target2)} | 1:3 ₹${String.format(Locale.US, "%,.1f", plan.target3)}",
                fontSize = 11.sp,
                color = palette.bullGreen,
                style = TabularNumericStyle
            )
        }
    }
}

@Composable
fun BacktestLabPanel(
    state: ChartWorkstationUiState,
    onRunBacktest: (BacktestConfig) -> Unit
) {
    val palette = LocalChartPalette.current
    var selectedStrat by remember { mutableStateOf(state.backtestConfig.strategyId) }
    var slippageText by remember { mutableStateOf(state.backtestConfig.slippagePct.toString()) }
    var brokerageText by remember { mutableStateOf(state.backtestConfig.brokeragePerOrder.toInt().toString()) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_backtest_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("RULE-BASED STRATEGY (ZERO LOOK-AHEAD)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BacktestStrategyId.entries.forEach { strat ->
                val sel = strat == selectedStrat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) palette.accentCyan else palette.surfaceVariant)
                        .border(1.dp, if (sel) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                        .clickable { selectedStrat = strat }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(strat.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.Black else palette.textPrimary)
                }
            }
        }
        Text(selectedStrat.summary, fontSize = 10.5.sp, color = palette.textSecondary)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = slippageText,
                onValueChange = { slippageText = it },
                label = { Text("Slippage %", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = brokerageText,
                onValueChange = { brokerageText = it },
                label = { Text("Brokerage/Order (₹)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                onRunBacktest(
                    state.backtestConfig.copy(
                        strategyId = selectedStrat,
                        slippagePct = slippageText.toDoubleOrNull() ?: 0.03,
                        brokeragePerOrder = brokerageText.toDoubleOrNull() ?: 20.0
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("run_backtest_button"),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black)
        ) {
            Text("Run Historical Backtest (${state.candles.size} bars)", fontWeight = FontWeight.Bold)
        }

        state.backtestResult?.let { res ->
            val retCol = if (res.totalReturnPct >= 0) palette.bullGreen else palette.bearRed
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, retCol.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Strategy Return: ${String.format(Locale.US, "%+.2f%%", res.totalReturnPct)} vs Buy&Hold: ${String.format(Locale.US, "%+.2f%%", res.buyAndHoldReturnPct)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = retCol,
                    style = TabularNumericStyle
                )
                Text(
                    "Net P&L: ₹${String.format(Locale.US, "%+,.0f", res.netProfitInr)} · Trades: ${res.trades.size} · Win Rate: ${String.format(Locale.US, "%.1f%%", res.winRatePct)}",
                    fontSize = 10.5.sp,
                    color = palette.textPrimary,
                    style = TabularNumericStyle
                )
                Text(
                    "Profit Factor: ${String.format(Locale.US, "%.2f", res.profitFactor)} · Max DD: ${String.format(Locale.US, "%.2f%%", res.maxDrawdownPct)} · Sharpe: ${String.format(Locale.US, "%.2f", res.sharpeRatio)}",
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    style = TabularNumericStyle
                )

                // Equity Curve Canvas
                if (res.equityCurve.size >= 2) {
                    val vals = res.equityCurve.map { it.second }
                    val minE = vals.min()
                    val maxE = vals.max()
                    val spanE = max(maxE - minE, 1.0)
                    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp).background(palette.canvasBackground)) {
                        val p = Path()
                        vals.forEachIndexed { idx, v ->
                            val x = (idx.toFloat() / max(vals.lastIndex, 1)) * size.width
                            val y = size.height - (((v - minE) / spanE) * size.height).toFloat()
                            if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        drawPath(p, color = retCol, style = Stroke(2.2f))
                    }
                }

                res.trades.takeLast(5).reversed().forEach { t ->
                    val tc = if (t.netPnl >= 0) palette.bullGreen else palette.bearRed
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.side} @ ₹${String.format(Locale.US, "%,.1f", t.entryPrice)} -> ₹${String.format(Locale.US, "%,.1f", t.exitPrice)}", fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
                        Text("${String.format(Locale.US, "%+.2f%%", t.returnPct)} (₹${String.format(Locale.US, "%+,.0f", t.netPnl)})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tc, style = TabularNumericStyle)
                    }
                }
            }
        }
    }
}
