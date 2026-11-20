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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val callOiSummary = "${if (totalCallOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", totalCallOiChange)} (${if (totalCallOiChange < 0) "Short Covering" else "Call Writing"})"
    val putOiSummary = "${if (totalPutOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", totalPutOiChange)} (${if (totalPutOiChange >= 0) "Put Writing" else "Put Unwinding"})"

    val fullReportText = buildString {
        appendLine("=========================================")
        appendLine("NIFTY 50 MARKET INTELLIGENCE REPORT")
        appendLine("=========================================")
        appendLine("Market Snapshot")
        appendLine("- NIFTY spot: ${String.format(Locale.US, "%,.2f", state.spot.spotPrice)}")
        appendLine("- Futures price: ₹${String.format(Locale.US, "%,.2f", state.futures.currentMonthPrice)} (${if (state.futures.basis >= 0) "+" else ""}${String.format(Locale.US, "%.1f", state.futures.basis)} pts)")
        appendLine("- India VIX: ${String.format(Locale.US, "%.2f", state.spot.indiaVix)} (${String.format(Locale.US, "%.2f", state.spot.vixChangePct)}%)")
        appendLine("- Market trend: ${state.technicals.trendDirection.name}")
        appendLine("- Important support: ${state.spot.s1.toInt()} / ${state.spot.dayLow.toInt()}")
        appendLine("- Important resistance: ${state.spot.r1.toInt()} / ${state.spot.dayHigh.toInt()}")
        appendLine()
        appendLine("Technical Analysis")
        appendLine("- Trend direction: ${state.technicals.trendDirection.name}")
        appendLine("- RSI: ${String.format(Locale.US, "%.1f", state.technicals.rsi14)}")
        appendLine("- MACD: ${String.format(Locale.US, "%.2f", state.technicals.macdLine)} (Hist: ${String.format(Locale.US, "%.2f", state.technicals.macdHist)})")
        appendLine("- VWAP: ₹${state.technicals.vwap.toInt()}")
        appendLine("- EMA alignment: 9 EMA (${state.technicals.ema9.toInt()}) / 20 EMA (${state.technicals.ema20.toInt()}) / 50 EMA (${state.technicals.ema50.toInt()})")
        appendLine("- Breakout/breakdown status: ${state.breakout.orbStatus}")
        appendLine("- Momentum strength: ${state.technicals.momentumStrength}")
        appendLine()
        appendLine("Options Chain Analysis")
        appendLine("- ATM strike: ${state.optionsChain.atmStrike.toInt()}")
        appendLine("- Highest Call OI: ${state.optionsChain.maxCallOiStrike.toInt()} CE")
        appendLine("- Highest Put OI: ${state.optionsChain.maxPutOiStrike.toInt()} PE")
        appendLine("- Change in Call OI: $callOiSummary")
        appendLine("- Change in Put OI: $putOiSummary")
        appendLine("- PCR: ${String.format(Locale.US, "%.2f", state.optionsChain.pcr)}")
        appendLine("- Important support strike: ${state.optionsChain.maxPutOiStrike.toInt()} PE")
        appendLine("- Important resistance strike: ${state.optionsChain.maxCallOiStrike.toInt()} CE")
        appendLine()
        appendLine("Futures Positioning")
        appendLine("- Futures OI change: ${String.format(Locale.US, "%,d", state.futures.currentMonthOiChange)} (${state.futures.currentMonthBuildup.label})")
        appendLine("- Price and OI interpretation: ${state.futures.currentMonthBuildup.label} confirmed by FII ratio")
        appendLine("- Futures premium/discount: ${if (state.futures.basis >= 0) "Premium" else "Discount"} of ${String.format(Locale.US, "%.1f", state.futures.basis)} pts")
        appendLine("- Institutional positioning: ${String.format(Locale.US, "%.1f%%", state.futures.fiiNetRatio)} FII Index Long")
        appendLine()
        appendLine("=========================================")
        appendLine("FINAL TRADE RECOMMENDATION")
        appendLine("=========================================")
        appendLine("- Direction: ${rec.direction.name}")
        appendLine("- Signal: ${rec.signal.label}")
        appendLine("- Instrument: ${rec.instrument}")
        appendLine("- Expiry: ${rec.expiryDate}")
        appendLine("- Strike: ${rec.strike.toInt()}")
        appendLine("- Option type: ${rec.optionType}")
        appendLine("- Entry zone: ${rec.entryZone}")
        appendLine("- Stop-loss: ${rec.stopLoss}")
        appendLine("- Target 1: ${rec.target1}")
        appendLine("- Target 2: ${rec.target2}")
        appendLine("- Target 3: ${rec.target3}")
        appendLine("- Risk-to-reward: ${rec.riskToReward}")
        appendLine("- Expected holding period: ${rec.expectedHoldingPeriod}")
        appendLine("- Confidence score: ${rec.confidenceScore} / 100")
        appendLine("- Setup invalidation level: ${rec.setupInvalidationLevel}")
        appendLine("- Trade rationale: ${rec.tradeRationale}")
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
                    text = "NIFTY 50 INTELLIGENCE REPORT",
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
                    val clip = ClipData.newPlainText("NIFTY 50 Report", fullReportText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Full Intelligence Report Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "COPY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
                ReportSectionHeader(title = "1. Market Snapshot")
                ReportItem(label = "NIFTY spot", value = String.format(Locale.US, "%,.2f", state.spot.spotPrice))
                ReportItem(label = "Futures price", value = "₹${String.format(Locale.US, "%,.2f", state.futures.currentMonthPrice)} (${if (state.futures.basis >= 0) "+" else ""}${String.format(Locale.US, "%.1f", state.futures.basis)} pts)")
                ReportItem(label = "India VIX", value = "${String.format(Locale.US, "%.2f", state.spot.indiaVix)} (${state.spot.vixChangePct}%)")
                ReportItem(label = "Market trend", value = state.technicals.trendDirection.name)
                ReportItem(label = "Important support", value = "${state.spot.s1.toInt()} / ${state.spot.dayLow.toInt()}")
                ReportItem(label = "Important resistance", value = "${state.spot.r1.toInt()} / ${state.spot.dayHigh.toInt()}")

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = "2. Technical Analysis")
                ReportItem(label = "Trend direction", value = state.technicals.trendDirection.name)
                ReportItem(label = "RSI (14)", value = String.format(Locale.US, "%.1f", state.technicals.rsi14))
                ReportItem(label = "MACD", value = "${String.format(Locale.US, "%.2f", state.technicals.macdLine)} (Signal: ${String.format(Locale.US, "%.2f", state.technicals.macdSignal)})")
                ReportItem(label = "VWAP", value = "₹${state.technicals.vwap.toInt()}")
                ReportItem(label = "EMA alignment", value = "9 EMA (${state.technicals.ema9.toInt()}) / 20 EMA (${state.technicals.ema20.toInt()}) / 50 EMA (${state.technicals.ema50.toInt()})")
                ReportItem(label = "Breakout/breakdown status", value = state.breakout.orbStatus)
                ReportItem(label = "Momentum strength", value = state.technicals.momentumStrength)

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = "3. Options Chain Analysis")
                ReportItem(label = "ATM strike", value = "${state.optionsChain.atmStrike.toInt()}")
                ReportItem(label = "Highest Call OI", value = "${state.optionsChain.maxCallOiStrike.toInt()} CE")
                ReportItem(label = "Highest Put OI", value = "${state.optionsChain.maxPutOiStrike.toInt()} PE")
                ReportItem(label = "Change in Call OI", value = callOiSummary)
                ReportItem(label = "Change in Put OI", value = putOiSummary)
                ReportItem(label = "PCR", value = String.format(Locale.US, "%.2f", state.optionsChain.pcr))
                ReportItem(label = "Important support strike", value = "${state.optionsChain.maxPutOiStrike.toInt()} PE")
                ReportItem(label = "Important resistance strike", value = "${state.optionsChain.maxCallOiStrike.toInt()} CE")

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = "4. Futures Positioning")
                ReportItem(label = "Futures OI change", value = "${String.format(Locale.US, "%,d", state.futures.currentMonthOiChange)} (${state.futures.currentMonthBuildup.label})")
                ReportItem(label = "Price & OI interpretation", value = "${state.futures.currentMonthBuildup.label} backed by FII flows")
                ReportItem(label = "Futures premium/discount", value = "${if (state.futures.basis >= 0) "Premium" else "Discount"} of ${String.format(Locale.US, "%.1f", state.futures.basis)} pts")
                ReportItem(label = "Institutional positioning", value = "${String.format(Locale.US, "%.1f%%", state.futures.fiiNetRatio)} FII Index Long Ratio")

                Spacer(modifier = Modifier.height(12.dp))
                ReportSectionHeader(title = "5. FINAL TRADE RECOMMENDATION")
                ReportItem(label = "Direction", value = rec.direction.name)
                ReportItem(label = "Signal", value = rec.signal.label)
                ReportItem(label = "Instrument", value = rec.instrument)
                ReportItem(label = "Expiry", value = rec.expiryDate)
                ReportItem(label = "Strike", value = "${rec.strike.toInt()}")
                ReportItem(label = "Option type", value = rec.optionType)
                ReportItem(label = "Entry zone", value = rec.entryZone)
                ReportItem(label = "Stop-loss", value = rec.stopLoss)
                ReportItem(label = "Target 1", value = rec.target1)
                ReportItem(label = "Target 2", value = rec.target2)
                ReportItem(label = "Target 3", value = rec.target3)
                ReportItem(label = "Risk-to-reward", value = rec.riskToReward)
                ReportItem(label = "Holding period", value = rec.expectedHoldingPeriod)
                ReportItem(label = "Confidence score", value = "${rec.confidenceScore} / 100")
                ReportItem(label = "Invalidation level", value = rec.setupInvalidationLevel)

                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Trade rationale:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = rec.tradeRationale, fontSize = 11.sp, color = palette.textSecondary, lineHeight = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Historical Trade and Decision Audit Log
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "DECISION AUDIT LOG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
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
                        Text(text = "Score: ${logRec.confidenceScore}%", fontSize = 11.sp, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = logRec.instrument, fontSize = 11.sp, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                    Text(text = "Invalidation: ${logRec.setupInvalidationLevel}", fontSize = 10.sp, color = palette.textMuted)
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
        Text(text = "• $label:", fontSize = 10.sp, color = palette.textMuted)
        Text(text = value, fontSize = 10.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}
