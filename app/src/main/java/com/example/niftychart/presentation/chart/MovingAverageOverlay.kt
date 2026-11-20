package com.example.niftychart.presentation.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.niftychart.domain.model.SmaPeriod

/**
 * Renders enabled Simple Moving Average (SMA 20/50/100/200) colored polylines aligned
 * pixel-for-pixel over the visible candlestick viewport.
 */
@Composable
fun MovingAverageOverlay(
    smaLines: Map<SmaPeriod, List<Double?>>,
    enabledSmas: Set<SmaPeriod>,
    viewport: ChartViewportRange,
    priceBounds: PriceScaleBounds,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rightAxisWidthPx = remember(density) { with(density) { 54.dp.toPx() } }
    val bottomAxisHeightPx = remember(density) { with(density) { 22.dp.toPx() } }
    val topPaddingPx = remember(density) { with(density) { 10.dp.toPx() } }
    val lineStrokePx = remember(density) { with(density) { 1.6.dp.toPx() } }

    Canvas(modifier = modifier) {
        if (enabledSmas.isEmpty() || viewport.visibleCount <= 0) return@Canvas

        val plotLeft = 0f
        val plotRight = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
        val plotTop = topPaddingPx
        val plotBottom = (size.height - bottomAxisHeightPx).coerceAtLeast(plotTop + 1f)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        val visibleCount = viewport.visibleCount

        for (sma in SmaPeriod.entries) {
            if (sma !in enabledSmas) continue
            val values = smaLines[sma] ?: continue
            if (values.isEmpty()) continue

            val path = Path()
            var hasStarted = false

            for (localIdx in 0 until visibleCount) {
                val globalIdx = viewport.startIndex + localIdx
                val smaValue = values.getOrNull(globalIdx)
                if (smaValue == null || !smaValue.isFinite()) {
                    hasStarted = false
                    continue
                }

                val x = ChartScaleCalculator.visibleIndexToCenterX(
                    visibleIndex = localIdx,
                    visibleCount = visibleCount,
                    plotLeftPx = plotLeft,
                    plotWidthPx = plotWidth
                )
                val y = ChartScaleCalculator.priceToY(
                    price = smaValue,
                    bounds = priceBounds,
                    topPx = plotTop,
                    plotHeightPx = plotHeight
                ).coerceIn(plotTop, plotBottom)

                if (!hasStarted) {
                    path.moveTo(x, y)
                    hasStarted = true
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(sma.colorArgb),
                style = Stroke(
                    width = lineStrokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
