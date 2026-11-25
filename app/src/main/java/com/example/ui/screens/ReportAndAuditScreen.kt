package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DeskState
import com.example.model.SignalType
import com.example.ui.theme.LocalChartPalette
import java.util.Locale

@Composable
fun ReportAndAuditScreen(
    state: DeskState
) {
    val palette = LocalChartPalette.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val rec = state.finalRecommendation
    val totalCallOiChange = state.optionsChain.strikes.sumOf { it.callOiChange }
    val totalPutOiChange = state.optionsChain.strikes.sumOf { it.putOiChange }
    val callOiSummary = stringResource(
        R.string.report_oi_change_label_format,
        "${if (totalCallOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", totalCallOiChange)}",
        stringResource(if (totalCallOiChange < 0) R.string.report_short_covering else R.string.report_call_writing)
    )
    val putOiSummary = stringResource(
        R.string.report_oi_change_label_format,
        "${if (totalPutOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", totalPutOiChange)}",
        stringResource(if (totalPutOiChange >= 0) R.string.report_put_writing else R.string.report_put_unwinding)
    )

    val premDiscLabel = context.getString(if (state.futures.basis >= 0) R.string.futures_premium else R.string.futures_discount)
    val basisSign = if (state.futures.basis >= 0) "+" else ""
    val fullReportText = buildString {
        val divider = "========================================="
        appendLine(divider)
        appendLine(context.getString(R.string.report_clipboard_header))
        appendLine(divider)
        appendLine(context.getString(R.string.report_section_1).removePrefix("1. "))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_nifty_spot), String.format(Locale.US, "%,.2f", state.spot.spotPrice)))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_futures_price),
                context.getString(
                    R.string.report_futures_price_format,
                    String.format(Locale.US, "%,.2f", state.futures.currentMonthPrice),
                    "$basisSign${String.format(Locale.US, "%.1f", state.futures.basis)}"
                )
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_india_vix),
                context.getString(
                    R.string.report_vix_value_format,
                    String.format(Locale.US, "%.2f", state.spot.indiaVix),
                    String.format(Locale.US, "%.2f", state.spot.vixChangePct)
                )
            )
        )
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_market_trend), state.technicals.trendDirection.name))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_important_support),
                context.getString(R.string.report_levels_pair_format, state.spot.s1.toInt(), state.spot.dayLow.toInt())
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_important_resistance),
                context.getString(R.string.report_levels_pair_format, state.spot.r1.toInt(), state.spot.dayHigh.toInt())
            )
        )
        appendLine()
        appendLine(context.getString(R.string.report_section_2).removePrefix("2. "))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_trend_direction), state.technicals.trendDirection.name))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_rsi), String.format(Locale.US, "%.1f", state.technicals.rsi14)))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_macd),
                context.getString(
                    R.string.report_macd_hist_format,
                    String.format(Locale.US, "%.2f", state.technicals.macdLine),
                    String.format(Locale.US, "%.2f", state.technicals.macdHist)
                )
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_vwap),
                context.getString(R.string.label_inr_amount_format, state.technicals.vwap.toInt().toString())
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_ema_alignment),
                context.getString(
                    R.string.report_ema_alignment_format,
                    state.technicals.ema9.toInt(),
                    state.technicals.ema20.toInt(),
                    state.technicals.ema50.toInt()
                )
            )
        )
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_breakout_status), state.breakout.orbStatus))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_momentum_strength), state.technicals.momentumStrength))
        appendLine()
        appendLine(context.getString(R.string.report_section_3).removePrefix("3. "))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_atm_strike), state.optionsChain.atmStrike.toInt().toString()))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_highest_call_oi),
                context.getString(R.string.report_ce_strike_format, state.optionsChain.maxCallOiStrike.toInt())
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_highest_put_oi),
                context.getString(R.string.report_pe_strike_format, state.optionsChain.maxPutOiStrike.toInt())
            )
        )
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_chg_call_oi), callOiSummary))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_chg_put_oi), putOiSummary))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_pcr), String.format(Locale.US, "%.2f", state.optionsChain.pcr)))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_support_strike),
                context.getString(R.string.report_pe_strike_format, state.optionsChain.maxPutOiStrike.toInt())
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_resistance_strike),
                context.getString(R.string.report_ce_strike_format, state.optionsChain.maxCallOiStrike.toInt())
            )
        )
        appendLine()
        appendLine(context.getString(R.string.report_section_4).removePrefix("4. "))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_futures_oi_chg),
                context.getString(
                    R.string.report_oi_change_label_format,
                    String.format(Locale.US, "%,d", state.futures.currentMonthOiChange),
                    state.futures.currentMonthBuildup.label
                )
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_price_oi_interp),
                context.getString(R.string.report_interp_confirmed_format, state.futures.currentMonthBuildup.label)
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_futures_prem_disc),
                context.getString(R.string.report_prem_disc_format, premDiscLabel, String.format(Locale.US, "%.1f", state.futures.basis))
            )
        )
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_inst_pos),
                context.getString(R.string.report_fii_long_text_format, String.format(Locale.US, "%.1f%%", state.futures.fiiNetRatio))
            )
        )
        appendLine()
        appendLine(divider)
        appendLine(context.getString(R.string.report_clipboard_final_rec))
        appendLine(divider)
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_direction), rec.direction.name))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_signal), rec.signal.label))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_instrument), rec.instrument))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_expiry), rec.expiryDate))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_strike), rec.strike.toInt().toString()))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_option_type), rec.optionType))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_entry_zone), rec.entryZone))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_stop_loss), rec.stopLoss))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_target_1), rec.target1))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_target_2), rec.target2))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_target_3), rec.target3))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_risk_reward), rec.riskToReward))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_expected_holding), rec.expectedHoldingPeriod))
        appendLine(
            context.getString(
                R.string.report_clipboard_line_format,
                context.getString(R.string.report_label_confidence_score),
                context.getString(R.string.report_confidence_100_format, rec.confidenceScore)
            )
        )
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_label_setup_invalidation), rec.setupInvalidationLevel))
        appendLine(context.getString(R.string.report_clipboard_line_format, context.getString(R.string.report_trade_rationale_header).removeSuffix(":"), rec.tradeRationale))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(12.dp)
            .testTag("report_and_audit_screen")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.report_screen_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(context.getString(R.string.report_clip_label), fullReportText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.report_copied_toast), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.action_copy_upper), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Formatted Institutional Intelligence Report Card (Section 7 Verbatim)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                ReportSectionHeader(title = stringResource(R.string.report_section_1))
                ReportItem(label = stringResource(R.string.report_label_nifty_spot), value = String.format(Locale.US, "%,.2f", state.spot.spotPrice))
                ReportItem(
                    label = stringResource(R.string.report_label_futures_price),
                    value = stringResource(
                        R.string.report_futures_price_format,
                        String.format(Locale.US, "%,.2f", state.futures.currentMonthPrice),
                        "$basisSign${String.format(Locale.US, "%.1f", state.futures.basis)}"
                    )
                )
                ReportItem(
                    label = stringResource(R.string.report_label_india_vix),
                    value = stringResource(
                        R.string.report_vix_value_format,
                        String.format(Locale.US, "%.2f", state.spot.indiaVix),
                        state.spot.vixChangePct.toString()
                    )
                )
                ReportItem(label = stringResource(R.string.report_label_market_trend), value = state.technicals.trendDirection.name)
                ReportItem(
                    label = stringResource(R.string.report_label_important_support),
                    value = stringResource(R.string.report_levels_pair_format, state.spot.s1.toInt(), state.spot.dayLow.toInt())
                )
                ReportItem(
                    label = stringResource(R.string.report_label_important_resistance),
                    value = stringResource(R.string.report_levels_pair_format, state.spot.r1.toInt(), state.spot.dayHigh.toInt())
                )

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = stringResource(R.string.report_section_2))
                ReportItem(label = stringResource(R.string.report_label_trend_direction), value = state.technicals.trendDirection.name)
                ReportItem(label = stringResource(R.string.report_label_rsi_14), value = String.format(Locale.US, "%.1f", state.technicals.rsi14))
                ReportItem(
                    label = stringResource(R.string.report_label_macd),
                    value = stringResource(
                        R.string.report_macd_signal_format,
                        String.format(Locale.US, "%.2f", state.technicals.macdLine),
                        String.format(Locale.US, "%.2f", state.technicals.macdSignal)
                    )
                )
                ReportItem(
                    label = stringResource(R.string.report_label_vwap),
                    value = stringResource(R.string.label_inr_amount_format, state.technicals.vwap.toInt().toString())
                )
                ReportItem(
                    label = stringResource(R.string.report_label_ema_alignment),
                    value = stringResource(
                        R.string.report_ema_alignment_format,
                        state.technicals.ema9.toInt(),
                        state.technicals.ema20.toInt(),
                        state.technicals.ema50.toInt()
                    )
                )
                ReportItem(label = stringResource(R.string.report_label_breakout_status), value = state.breakout.orbStatus)
                ReportItem(label = stringResource(R.string.report_label_momentum_strength), value = state.technicals.momentumStrength)

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = stringResource(R.string.report_section_3))
                ReportItem(label = stringResource(R.string.report_label_atm_strike), value = "${state.optionsChain.atmStrike.toInt()}")
                ReportItem(
                    label = stringResource(R.string.report_label_highest_call_oi),
                    value = stringResource(R.string.report_ce_strike_format, state.optionsChain.maxCallOiStrike.toInt())
                )
                ReportItem(
                    label = stringResource(R.string.report_label_highest_put_oi),
                    value = stringResource(R.string.report_pe_strike_format, state.optionsChain.maxPutOiStrike.toInt())
                )
                ReportItem(label = stringResource(R.string.report_label_chg_call_oi), value = callOiSummary)
                ReportItem(label = stringResource(R.string.report_label_chg_put_oi), value = putOiSummary)
                ReportItem(label = stringResource(R.string.report_label_pcr), value = String.format(Locale.US, "%.2f", state.optionsChain.pcr))
                ReportItem(
                    label = stringResource(R.string.report_label_support_strike),
                    value = stringResource(R.string.report_pe_strike_format, state.optionsChain.maxPutOiStrike.toInt())
                )
                ReportItem(
                    label = stringResource(R.string.report_label_resistance_strike),
                    value = stringResource(R.string.report_ce_strike_format, state.optionsChain.maxCallOiStrike.toInt())
                )

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = stringResource(R.string.report_section_4))
                ReportItem(
                    label = stringResource(R.string.report_label_futures_oi_chg),
                    value = stringResource(
                        R.string.report_oi_change_label_format,
                        String.format(Locale.US, "%,d", state.futures.currentMonthOiChange),
                        state.futures.currentMonthBuildup.label
                    )
                )
                ReportItem(
                    label = stringResource(R.string.report_label_price_oi_interp),
                    value = stringResource(R.string.report_interp_backed_format, state.futures.currentMonthBuildup.label)
                )
                ReportItem(
                    label = stringResource(R.string.report_label_futures_prem_disc),
                    value = stringResource(
                        R.string.report_prem_disc_format,
                        stringResource(if (state.futures.basis >= 0) R.string.futures_premium else R.string.futures_discount),
                        String.format(Locale.US, "%.1f", state.futures.basis)
                    )
                )
                ReportItem(
                    label = stringResource(R.string.report_label_inst_pos),
                    value = stringResource(R.string.report_fii_long_ratio_format, String.format(Locale.US, "%.1f%%", state.futures.fiiNetRatio))
                )

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = stringResource(R.string.report_section_5))
                ReportItem(label = stringResource(R.string.report_label_direction), value = rec.direction.name)
                ReportItem(label = stringResource(R.string.report_label_signal), value = rec.signal.label)
                ReportItem(label = stringResource(R.string.report_label_instrument), value = rec.instrument)
                ReportItem(label = stringResource(R.string.report_label_expiry), value = rec.expiryDate)
                ReportItem(label = stringResource(R.string.report_label_strike), value = "${rec.strike.toInt()}")
                ReportItem(label = stringResource(R.string.report_label_option_type), value = rec.optionType)
                ReportItem(label = stringResource(R.string.report_label_entry_zone), value = rec.entryZone)
                ReportItem(label = stringResource(R.string.report_label_stop_loss), value = rec.stopLoss)
                ReportItem(label = stringResource(R.string.report_label_target_1), value = rec.target1)
                ReportItem(label = stringResource(R.string.report_label_target_2), value = rec.target2)
                ReportItem(label = stringResource(R.string.report_label_target_3), value = rec.target3)
                ReportItem(label = stringResource(R.string.report_label_risk_reward), value = rec.riskToReward)
                ReportItem(label = stringResource(R.string.report_label_holding_period), value = rec.expectedHoldingPeriod)
                ReportItem(label = stringResource(R.string.report_label_confidence_score), value = stringResource(R.string.report_confidence_100_format, rec.confidenceScore))
                ReportItem(label = stringResource(R.string.report_label_invalidation_level), value = rec.setupInvalidationLevel)

                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(R.string.report_trade_rationale_header), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = rec.tradeRationale, fontSize = 11.sp, color = palette.textSecondary, lineHeight = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Historical Trade and Decision Audit Log
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.report_decision_audit_log), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        state.historicalAuditLogs.forEach { logRec ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = logRec.signal.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (logRec.signal == SignalType.BUY_CE) palette.bullGreen else if (logRec.signal == SignalType.BUY_PE) palette.bearRed else palette.goldAccent)
                        Text(text = stringResource(R.string.report_score_pct_format, logRec.confidenceScore), fontSize = 11.sp, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = logRec.instrument, fontSize = 11.sp, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                    Text(text = stringResource(R.string.signal_invalidation_format, logRec.setupInvalidationLevel), fontSize = 10.sp, color = palette.textMuted)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReportSectionHeader(title: String) {
    val palette = LocalChartPalette.current
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = palette.accentCyan,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ReportItem(label: String, value: String) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(R.string.report_item_bullet_format, label), fontSize = 10.sp, color = palette.textMuted)
        Text(text = value, fontSize = 10.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}
