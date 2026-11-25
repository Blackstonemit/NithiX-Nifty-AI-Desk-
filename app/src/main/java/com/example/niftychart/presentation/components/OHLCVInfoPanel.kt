package com.example.niftychart.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.FeedConnectionStatus
import com.example.niftychart.domain.model.OHLCV
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Displays OHLCV metrics, net/percentage move, feed connection state, and timestamp
 * for either the actively inspected (crosshair/long-press) candle or the latest candle.
 */
@Composable
fun OHLCVInfoPanel(
    candle: OHLCV?,
    isInspected: Boolean,
    connectionStatus: FeedConnectionStatus,
    modifier: Modifier = Modifier,
    syncLabel: String = ""
) {
    val palette = LocalChartPalette.current
    val timeFormatter = remember(candle?.interval) {
        val pattern = if (candle?.interval == ChartInterval.D1) "dd MMM yyyy" else "dd MMM HH:mm"
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .testTag("ohlcv_info_panel"),
        shape = RoundedCornerShape(10.dp),
        color = palette.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (connectionStatus) {
                        FeedConnectionStatus.LIVE -> palette.bullGreen
                        FeedConnectionStatus.SIMULATED -> palette.accentCyan
                        FeedConnectionStatus.RECONNECTING -> palette.goldAccent
                        FeedConnectionStatus.OFFLINE -> palette.bearRed
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = candle?.symbol ?: OHLCV.SYMBOL_NIFTY_50,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val badgeLabel = stringResource(
                        when {
                            isInspected -> R.string.badge_inspect
                            connectionStatus == FeedConnectionStatus.LIVE -> R.string.badge_live
                            connectionStatus == FeedConnectionStatus.SIMULATED -> R.string.badge_sim_ref
                            connectionStatus == FeedConnectionStatus.RECONNECTING -> R.string.badge_retrying
                            else -> R.string.badge_offline
                        }
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isInspected) palette.goldAccentDim else palette.surfaceHighlight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInspected) palette.goldAccent else statusColor,
                            style = TabularNumericStyle
                        )
                    }
                }

                candle?.let { activeCandle ->
                    val deltaColor = if (activeCandle.isBullish) palette.bullGreen else palette.bearRed
                    val sign = if (activeCandle.change >= 0.0) "+" else ""
                    Text(
                        text = "₹${String.format(Locale.US, "%,.2f", activeCandle.close)} ($sign${String.format(Locale.US, "%.2f", activeCandle.changePct)}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = deltaColor,
                        style = TabularNumericStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (candle != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OhlcField("O", String.format(Locale.US, "%.2f", candle.open), palette.textPrimary)
                    OhlcField("H", String.format(Locale.US, "%.2f", candle.high), palette.bullGreen)
                    OhlcField("L", String.format(Locale.US, "%.2f", candle.low), palette.bearRed)
                    OhlcField("C", String.format(Locale.US, "%.2f", candle.close), if (candle.isBullish) palette.bullGreen else palette.bearRed)
                    OhlcField("V", formatCompactVolume(candle.volume), palette.accentCyan)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            R.string.ohlcv_timestamp_format,
                            timeFormatter.format(Date(candle.timestamp)),
                            candle.interval.code
                        ),
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        style = TabularNumericStyle
                    )
                    if (syncLabel.isNotBlank()) {
                        Text(
                            text = syncLabel,
                            fontSize = 10.sp,
                            color = palette.textMuted,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.ohlcv_awaiting_bars),
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun OhlcField(
    label: String,
    value: String,
    valueColor: Color
) {
    val palette = LocalChartPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textMuted,
            style = TabularNumericStyle
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            style = TabularNumericStyle
        )
    }
}

private fun formatCompactVolume(volume: Long): String = when {
    volume >= 10_000_000L -> String.format(Locale.US, "%.2fCr", volume / 10_000_000.0)
    volume >= 100_000L -> String.format(Locale.US, "%.1fL", volume / 100_000.0)
    volume >= 1_000L -> String.format(Locale.US, "%.1fK", volume / 1_000.0)
    else -> volume.toString()
}
