package com.example.niftychart.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.example.niftychart.domain.model.ChartInterval
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle

/**
 * Interactive timeframe ribbon supporting 1m, 5m, 15m, 30m, 1h, and 1d intervals
 * with 48dp accessible touch targets.
 */
@Composable
fun ChartTimeframeSelector(
    selectedInterval: ChartInterval,
    onSelectInterval: (ChartInterval) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalChartPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("chart_timeframe_selector"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChartInterval.entries.forEach { interval ->
            val selected = interval == selectedInterval
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.accentCyan else palette.surfaceHighlight)
                    .border(
                        width = 1.dp,
                        color = if (selected) palette.accentCyan else palette.border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectInterval(interval) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Select ${interval.displayLabel} timeframe" }
                    .testTag("nifty_tf_${interval.code}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = interval.displayLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.Black else palette.textSecondary,
                    style = TabularNumericStyle
                )
            }
        }
    }
}
