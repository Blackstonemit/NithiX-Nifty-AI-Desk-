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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
                Text(stringResource(R.string.mtf_rule_signal_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, style = TabularNumericStyle)
                Text(
                    stringResource(R.string.mtf_sig_score_format, sig.rating, if (sig.score > 0) "+" else "", sig.score),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ratingColor,
                    style = TabularNumericStyle
                )
            }
            Text(
                stringResource(R.string.mtf_sig_breakdown_format, sig.trendScore, sig.momentumScore, sig.volumeScore, sig.structureScore),
                fontSize = 10.5.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
            Text(
                stringResource(R.string.mtf_invalidation_price_format, String.format(Locale.US, "%,.2f", sig.invalidationPrice)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.bearRed,
                style = TabularNumericStyle
            )
            sig.reasons.forEach { r ->
                Text(stringResource(R.string.agents_bullet_format, r), fontSize = 10.5.sp, color = palette.textPrimary)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mtf_confluence_header_format, mtf.alignmentScorePct), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
            OutlinedButton(onClick = onRefreshMtf) {
                Text(stringResource(R.string.mtf_sync_button), fontSize = 11.sp)
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
            Text(stringResource(R.string.mtf_empty_hint), fontSize = 11.sp, color = palette.textMuted)
        } else {
            mtf.rows.forEach { row ->
                val col = when (row.bias) {
                    MarketBias.BULLISH -> palette.bullGreen
                    MarketBias.BEARISH -> palette.bearRed
                    else -> palette.goldAccent
                }
                val stLabel = if (row.supertrendBullish) stringResource(R.string.mtf_bull_short) else stringResource(R.string.mtf_bear_short)
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
                        Text(stringResource(R.string.watchlist_item_sub_format, row.timeframe.label, row.emaAlignment), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, style = TabularNumericStyle)
                        Text(stringResource(R.string.mtf_row_rsi_supertrend_format, String.format(Locale.US, "%.1f", row.rsi), stLabel), fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
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
    onUpdateRisk: (capital: Double?, riskPct: Double?, rrRatio: Double?, customSl: Double?, clearCustomSl: Boolean, showOnChart: Boolean?, lotSize: Int?) -> Unit
) {
    val palette = LocalChartPalette.current
    val plan = state.riskPlan
    var capInput by remember(state.riskCapital) { mutableStateOf(state.riskCapital.toInt().toString()) }
    var pctInput by remember(state.riskPct) { mutableStateOf(state.riskPct.toString()) }
    var lotInput by remember(state.riskLotSize) { mutableStateOf(state.riskLotSize.toString()) }
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
            Text(stringResource(R.string.risk_plot_lines_toggle), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Switch(checked = state.showRiskOnChart, onCheckedChange = { onUpdateRisk(null, null, null, null, false, it, null) })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = capInput,
                onValueChange = {
                    capInput = it
                    it.toDoubleOrNull()?.let { c -> onUpdateRisk(c, null, null, null, false, null, null) }
                },
                label = { Text(stringResource(R.string.risk_capital_label), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1.1f)
            )
            OutlinedTextField(
                value = pctInput,
                onValueChange = {
                    pctInput = it
                    it.toDoubleOrNull()?.let { r -> onUpdateRisk(null, r, null, null, false, null, null) }
                },
                label = { Text(stringResource(R.string.risk_pct_label), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(0.65f)
            )
            OutlinedTextField(
                value = lotInput,
                onValueChange = { raw ->
                    lotInput = raw
                    raw.toIntOrNull()?.takeIf { it > 0 }?.let { lot ->
                        onUpdateRisk(null, null, null, null, false, null, lot)
                    }
                },
                label = { Text(stringResource(R.string.risk_lot_size_label), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(0.65f).testTag("risk_lot_size_input")
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = slInput,
                onValueChange = {
                    slInput = it
                    val v = it.toDoubleOrNull()
                    if (v != null && v > 0.0) onUpdateRisk(null, null, null, v, false, null, null)
                },
                label = { Text(stringResource(R.string.risk_custom_sl_label), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    slInput = ""
                    onUpdateRisk(null, null, null, plan.atrStop15x, false, true, null)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.risk_atr_15x), fontSize = 10.sp) }
            OutlinedButton(
                onClick = {
                    slInput = ""
                    onUpdateRisk(null, null, null, plan.atrStop20x, false, true, null)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.risk_atr_20x), fontSize = 10.sp) }
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
            val biasLabel = if (plan.isLong) stringResource(R.string.risk_long_bias) else stringResource(R.string.risk_short_bias)
            Text(
                stringResource(R.string.risk_sizing_header_format, biasLabel),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentCyan,
                style = TabularNumericStyle
            )
            Text(
                stringResource(R.string.risk_max_budget_format, String.format(Locale.US, "%,.0f", plan.riskAmountInr), plan.riskPct.toString()),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                style = TabularNumericStyle
            )
            Text(
                stringResource(R.string.risk_recommended_size_format, plan.recommendedShares, plan.recommendedNiftyLots, plan.lotSize),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.goldAccent,
                style = TabularNumericStyle
            )
            Text(
                stringResource(R.string.risk_notional_exposure_format, String.format(Locale.US, "%,.0f", plan.positionValueInr)),
                fontSize = 11.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.risk_entry_sl_format, String.format(Locale.US, "%,.2f", plan.entryPrice), String.format(Locale.US, "%,.2f", plan.stopLossPrice)),
                fontSize = 11.sp,
                color = palette.bearRed,
                style = TabularNumericStyle
            )
            Text(
                stringResource(
                    R.string.risk_targets_format,
                    String.format(Locale.US, "%,.1f", plan.target1),
                    String.format(Locale.US, "%,.1f", plan.target2),
                    String.format(Locale.US, "%,.1f", plan.target3)
                ),
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
        Text(stringResource(R.string.backtest_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
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
                label = { Text(stringResource(R.string.backtest_slippage_label), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = brokerageText,
                onValueChange = { brokerageText = it },
                label = { Text(stringResource(R.string.backtest_brokerage_label), fontSize = 11.sp) },
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
            Text(stringResource(R.string.backtest_run_format, state.candles.size), fontWeight = FontWeight.Bold)
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
                    stringResource(
                        R.string.backtest_return_vs_bh_format,
                        String.format(Locale.US, "%+.2f%%", res.totalReturnPct),
                        String.format(Locale.US, "%+.2f%%", res.buyAndHoldReturnPct)
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = retCol,
                    style = TabularNumericStyle
                )
                Text(
                    stringResource(
                        R.string.backtest_net_pnl_stats_format,
                        String.format(Locale.US, "%+,.0f", res.netProfitInr),
                        res.trades.size,
                        String.format(Locale.US, "%.1f%%", res.winRatePct)
                    ),
                    fontSize = 10.5.sp,
                    color = palette.textPrimary,
                    style = TabularNumericStyle
                )
                Text(
                    stringResource(
                        R.string.backtest_pf_dd_sharpe_format,
                        String.format(Locale.US, "%.2f", res.profitFactor),
                        String.format(Locale.US, "%.2f%%", res.maxDrawdownPct),
                        String.format(Locale.US, "%.2f", res.sharpeRatio)
                    ),
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
                        Text(
                            stringResource(
                                R.string.backtest_trade_row_format,
                                t.side,
                                String.format(Locale.US, "%,.1f", t.entryPrice),
                                String.format(Locale.US, "%,.1f", t.exitPrice)
                            ),
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            style = TabularNumericStyle
                        )
                        Text(
                            stringResource(
                                R.string.backtest_trade_ret_format,
                                String.format(Locale.US, "%+.2f%%", t.returnPct),
                                String.format(Locale.US, "%+,.0f", t.netPnl)
                            ),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = tc,
                            style = TabularNumericStyle
                        )
                    }
                }
            }
        }
    }
}
