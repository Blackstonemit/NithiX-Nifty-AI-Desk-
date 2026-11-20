package com.example.niftychart.presentation.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.niftychart.domain.model.RsiSeries
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import java.util.Locale

/**
 * Separate Wilder RSI indicator sub-panel rendered beneath [NiftyCandlestickChart].
 * Displays 0..100 range, overbought (70), center (50), and oversold (30) threshold levels,
 * shaded threshold bands, aligned visible RSI line, and vertical crosshair sync.
 */
@Composable
fun RSIIndicatorChart(
    rsiSeries: RsiSeries,
    viewport: ChartViewportRange,
    selectedGlobalIndex: Int?,
    modifier: Modifier = Modifier,
    panelHeight: Dp = 115.dp
) {
    val palette = LocalChartPalette.current
    val density = LocalDensity.current

    val rightAxisWidthPx = remember(density) { with(density) { 54.dp.toPx() } }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }
    val rsiStrokePx = remember(density) { with(density) { 1.8.dp.toPx() } }
    val rsiLineColor = Color(0xFFC084FC)

    val axisPaint = remember(palette.axisLabelArgb, density) {
        Paint().apply {
            color = palette.axisLabelArgb
            textSize = with(density) { 9.5.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    val displayedRsi = selectedGlobalIndex?.let { rsiSeries.values.getOrNull(it) } ?: rsiSeries.latestValue
    val zoneLabel = when {
        displayedRsi == null -> "N/A"
        displayedRsi >= rsiSeries.overboughtLevel -> "OB"
        displayedRsi <= rsiSeries.oversoldLevel -> "OS"
        else -> "NEUTRAL"
    }
    val zoneColor = when {
        displayedRsi == null -> palette.textMuted
        displayedRsi >= rsiSeries.overboughtLevel -> palette.bearRed
        displayedRsi <= rsiSeries.oversoldLevel -> palette.bullGreen
        else -> rsiLineColor
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag("rsi_indicator_chart")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RSI (${rsiSeries.period}) • WILDER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(zoneColor.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = zoneLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = zoneColor,
                        style = TabularNumericStyle
                    )
                }
                Text(
                    text = displayedRsi?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = zoneColor,
                    style = TabularNumericStyle
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            val plotLeft = 0f
            val plotRight = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
            val plotTop = 6f
            val plotBottom = (size.height - 6f).coerceAtLeast(plotTop + 1f)
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            val y70 = ChartScaleCalculator.rsiToY(rsiSeries.overboughtLevel, plotTop, plotHeight)
            val y50 = ChartScaleCalculator.rsiToY(rsiSeries.centerLevel, plotTop, plotHeight)
            val y30 = ChartScaleCalculator.rsiToY(rsiSeries.oversoldLevel, plotTop, plotHeight)

            // Overbought (70..100) & Oversold (0..30) subtle bands
            drawRect(
                color = palette.bearRed.copy(alpha = 0.08f),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotWidth, (y70 - plotTop).coerceAtLeast(0f))
            )
            drawRect(
                color = palette.bullGreen.copy(alpha = 0.08f),
                topLeft = Offset(plotLeft, y30),
                size = Size(plotWidth, (plotBottom - y30).coerceAtLeast(0f))
            )

            // Horizontal threshold levels 70 / 50 / 30
            drawLine(
                color = palette.bearRed.copy(alpha = 0.65f),
                start = Offset(plotLeft, y70),
                end = Offset(plotRight, y70),
                strokeWidth = 1.2f,
                pathEffect = dashEffect
            )
            drawLine(
                color = palette.textMuted.copy(alpha = 0.55f),
                start = Offset(plotLeft, y50),
                end = Offset(plotRight, y50),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )
            drawLine(
                color = palette.bullGreen.copy(alpha = 0.65f),
                start = Offset(plotLeft, y30),
                end = Offset(plotRight, y30),
                strokeWidth = 1.2f,
                pathEffect = dashEffect
            )

            // Y-axis reference labels
            drawContext.canvas.nativeCanvas.drawText("70", plotRight + 8f, y70 + 3f, axisPaint)
            drawContext.canvas.nativeCanvas.drawText("50", plotRight + 8f, y50 + 3f, axisPaint)
            drawContext.canvas.nativeCanvas.drawText("30", plotRight + 8f, y30 + 3f, axisPaint)

            drawLine(
                color = palette.border,
                start = Offset(plotRight, plotTop),
                end = Offset(plotRight, plotBottom),
                strokeWidth = 1f
            )

            if (viewport.visibleCount <= 0 || rsiSeries.values.isEmpty()) return@Canvas

            val path = Path()
            var hasStarted = false

            for (localIdx in 0 until viewport.visibleCount) {
                val globalIdx = viewport.startIndex + localIdx
                val rsiVal = rsiSeries.values.getOrNull(globalIdx)
                if (rsiVal == null || !rsiVal.isFinite()) {
                    hasStarted = false
                    continue
                }
                val x = ChartScaleCalculator.visibleIndexToCenterX(
                    visibleIndex = localIdx,
                    visibleCount = viewport.visibleCount,
                    plotLeftPx = plotLeft,
                    plotWidthPx = plotWidth
                )
                val y = ChartScaleCalculator.rsiToY(rsiVal, plotTop, plotHeight)
                if (!hasStarted) {
                    path.moveTo(x, y)
                    hasStarted = true
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = rsiLineColor,
                style = Stroke(
                    width = rsiStrokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Synced vertical crosshair line & active node dot
            val idx = selectedGlobalIndex
            if (idx != null && idx in viewport.startIndex until viewport.endIndexExclusive) {
                val localIdx = idx - viewport.startIndex
                val x = ChartScaleCalculator.visibleIndexToCenterX(
                    visibleIndex = localIdx,
                    visibleCount = viewport.visibleCount,
                    plotLeftPx = plotLeft,
                    plotWidthPx = plotWidth
                )
                drawLine(
                    color = Color(palette.crosshairArgb),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.2f,
                    pathEffect = dashEffect
                )
                val activeVal = rsiSeries.values.getOrNull(idx)
                if (activeVal != null && activeVal.isFinite()) {
                    drawCircle(
                        color = rsiLineColor,
                        radius = 4.5f,
                        center = Offset(x, ChartScaleCalculator.rsiToY(activeVal, plotTop, plotHeight))
                    )
                }
            }
        }
    }
}
