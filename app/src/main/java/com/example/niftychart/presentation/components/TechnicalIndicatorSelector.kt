package com.example.niftychart.presentation.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.niftychart.domain.model.SmaPeriod
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import java.util.Locale

/**
 * Interactive technical indicator toggle strip for SMA 20/50/100/200 overlays
 * and Wilder RSI panel visibility + period selection (7 / 14 / 21).
 */
@Composable
fun TechnicalIndicatorSelector(
    enabledSmas: Set<SmaPeriod>,
    onToggleSma: (SmaPeriod) -> Unit,
    rsiEnabled: Boolean,
    onToggleRsi: () -> Unit,
    rsiPeriod: Int,
    onSelectRsiPeriod: (Int) -> Unit,
    latestSmaValues: Map<SmaPeriod, Double?> = emptyMap(),
    latestRsi: Double? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalChartPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("technical_indicator_selector"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmaPeriod.entries.forEach { sma ->
                val enabled = sma in enabledSmas
                val dotColor = Color(sma.colorArgb)
                val latest = latestSmaValues[sma]
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (enabled) dotColor.copy(alpha = 0.16f) else palette.surfaceHighlight
                        )
                        .border(
                            width = 1.dp,
                            color = if (enabled) dotColor else palette.border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleSma(sma) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Toggle ${sma.label} indicator" }
                        .testTag("indicator_chip_${sma.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (enabled) dotColor else palette.textMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (enabled && latest != null) {
                            "${sma.label} (${String.format(Locale.US, "%.0f", latest)})"
                        } else {
                            sma.label
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) palette.textPrimary else palette.textSecondary,
                        style = TabularNumericStyle
                    )
                }
            }

            // RSI Toggle Chip
            val rsiColor = Color(0xFFC084FC)
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (rsiEnabled) rsiColor.copy(alpha = 0.16f) else palette.surfaceHighlight)
                    .border(
                        width = 1.dp,
                        color = if (rsiEnabled) rsiColor else palette.border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleRsi() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Toggle RSI panel" }
                    .testTag("indicator_chip_RSI"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (rsiEnabled) rsiColor else palette.textMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (rsiEnabled && latestRsi != null) {
                        "RSI($rsiPeriod) ${String.format(Locale.US, "%.1f", latestRsi)}"
                    } else {
                        "RSI($rsiPeriod)"
                    },
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rsiEnabled) palette.textPrimary else palette.textSecondary,
                    style = TabularNumericStyle
                )
            }

            if (rsiEnabled) {
                listOf(7, 14, 21).forEach { periodOption ->
                    val active = rsiPeriod == periodOption
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) rsiColor else palette.surfaceVariant)
                            .border(1.dp, if (active) rsiColor else palette.border, RoundedCornerShape(6.dp))
                            .clickable { onSelectRsiPeriod(periodOption) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("rsi_period_$periodOption"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${periodOption}P",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.Black else palette.textSecondary,
                            style = TabularNumericStyle
                        )
                    }
                }
            }
        }
    }
}
