package com.example.niftychart.presentation.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import com.example.ui.theme.LocalChartPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Jetpack Compose Canvas candlestick renderer.
 * Draws only the visible viewport slice with dynamic Y-axis price scaling, horizontal gridlines,
 * IST X-axis time labels, green bullish / red bearish wicks and bodies, and candle highlight.
 */
@Composable
fun CandlestickCanvas(
    candles: List<OHLCV>,
    viewport: ChartViewportRange,
    priceBounds: PriceScaleBounds,
    selectedGlobalIndex: Int?,
    modifier: Modifier = Modifier
) {
    val palette = LocalChartPalette.current
    val density = LocalDensity.current

    val rightAxisWidthPx = remember(density) { with(density) { 54.dp.toPx() } }
    val bottomAxisHeightPx = remember(density) { with(density) { 22.dp.toPx() } }
    val topPaddingPx = remember(density) { with(density) { 10.dp.toPx() } }
    val wickStrokePx = remember(density) { with(density) { 1.4.dp.toPx() } }
    val gridDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

    val interval = candles.firstOrNull()?.interval ?: ChartInterval.M15
    val xAxisFormat = remember(interval) {
        val pattern = if (interval == ChartInterval.D1) "dd MMM" else "HH:mm"
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val yLabelPaint = remember(palette.axisLabelArgb, density) {
        Paint().apply {
            color = palette.axisLabelArgb
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    val xLabelPaint = remember(palette.textMuted, density) {
        Paint().apply {
            color = palette.textMuted.toArgb()
            textSize = with(density) { 9.5.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        if (candles.isEmpty() || viewport.visibleCount <= 0) return@Canvas

        val plotLeft = 0f
        val plotRight = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
        val plotTop = topPaddingPx
        val plotBottom = (size.height - bottomAxisHeightPx).coerceAtLeast(plotTop + 1f)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val visibleCount = viewport.visibleCount
        val slotWidth = ChartScaleCalculator.computeStepWidthPx(plotWidth, visibleCount)
        val bodyWidth = ChartScaleCalculator.computeCandleBodyWidthPx(slotWidth)

        // 1. Horizontal grid lines & dynamic Y-axis price labels (5 tiers)
        val gridRows = 4
        for (step in 0..gridRows) {
            val fraction = step / gridRows.toFloat()
            val y = plotTop + plotHeight * fraction
            val priceAtLine = priceBounds.maxPrice - priceBounds.span * fraction

            drawLine(
                color = palette.gridLine,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1f,
                pathEffect = gridDashEffect
            )

            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", priceAtLine),
                plotRight + 6f,
                y + 4f,
                yLabelPaint
            )
        }

        // Right & bottom border separators
        drawLine(
            color = palette.border,
            start = Offset(plotRight, plotTop),
            end = Offset(plotRight, plotBottom),
            strokeWidth = 1f
        )
        drawLine(
            color = palette.border,
            start = Offset(plotLeft, plotBottom),
            end = Offset(plotRight, plotBottom),
            strokeWidth = 1f
        )

        // 2. Render visible candlesticks only
        repeat(visibleCount) { localIdx ->
            val globalIdx = viewport.startIndex + localIdx
            if (globalIdx !in candles.indices) return@repeat

            val candle = candles[globalIdx]
            val centerX = ChartScaleCalculator.visibleIndexToCenterX(
                visibleIndex = localIdx,
                visibleCount = visibleCount,
                plotLeftPx = plotLeft,
                plotWidthPx = plotWidth
            )

            // Selected candle highlight backdrop
            if (selectedGlobalIndex == globalIdx) {
                drawRect(
                    color = palette.accentCyan.copy(alpha = 0.14f),
                    topLeft = Offset(centerX - slotWidth * 0.5f, plotTop),
                    size = Size(slotWidth, plotHeight)
                )
            }

            val yHigh = ChartScaleCalculator.priceToY(candle.high, priceBounds, plotTop, plotHeight)
            val yLow = ChartScaleCalculator.priceToY(candle.low, priceBounds, plotTop, plotHeight)
            val yOpen = ChartScaleCalculator.priceToY(candle.open, priceBounds, plotTop, plotHeight)
            val yClose = ChartScaleCalculator.priceToY(candle.close, priceBounds, plotTop, plotHeight)

            val candleColor = if (candle.isBullish) palette.bullGreen else palette.bearRed

            // Upper and lower wicks
            drawLine(
                color = candleColor,
                start = Offset(centerX, yHigh),
                end = Offset(centerX, yLow),
                strokeWidth = wickStrokePx
            )

            // Candle body
            val bodyTop = min(yOpen, yClose)
            val bodyBottom = max(yOpen, yClose)
            val bodyHeight = abs(bodyBottom - bodyTop).coerceAtLeast(2f)

            drawRect(
                color = candleColor,
                topLeft = Offset(centerX - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
        }

        // 3. Time-based X-axis labels evenly sampled across visible candles
        val tickIndices = buildList {
            if (visibleCount <= 1) {
                add(0)
            } else {
                val labelSlots = min(4, visibleCount - 1)
                repeat(labelSlots + 1) { i ->
                    add(localIdxForFraction(i, labelSlots, visibleCount - 1))
                }
            }
        }.distinct()

        for (localIdx in tickIndices) {
            val globalIdx = viewport.startIndex + localIdx
            if (globalIdx !in candles.indices) continue
            val ts = candles[globalIdx].timestamp
            val label = xAxisFormat.format(Date(ts))
            val centerX = ChartScaleCalculator.visibleIndexToCenterX(
                visibleIndex = localIdx,
                visibleCount = visibleCount,
                plotLeftPx = plotLeft,
                plotWidthPx = plotWidth
            )
            val clampedX = centerX.coerceIn(4f, (plotRight - 42f).coerceAtLeast(4f))
            drawContext.canvas.nativeCanvas.drawText(
                label,
                clampedX - 14f,
                size.height - 6f,
                xLabelPaint
            )
        }
    }
}

private fun localIdxForFraction(step: Int, totalSteps: Int, maxLocalIdx: Int): Int {
    if (totalSteps <= 0) return 0
    return ((step.toFloat() / totalSteps.toFloat()) * maxLocalIdx).toInt().coerceIn(0, maxLocalIdx)
}
