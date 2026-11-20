package com.example.niftychart.presentation.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/**
 * Interactive touch/long-press crosshair overlay drawing dashed vertical/horizontal alignment lines,
 * a highlighted marker on the inspected candle close, and Y/X axis coordinate pills.
 */
@Composable
fun ChartCrosshair(
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
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }

    val interval = candles.firstOrNull()?.interval ?: ChartInterval.M15
    val pillTimeFormat = remember(interval) {
        val pattern = if (interval == ChartInterval.D1) "dd MMM" else "HH:mm"
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val badgeTextPaint = remember(density) {
        Paint().apply {
            color = Color.Black.toArgb()
            textSize = with(density) { 9.5.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val idx = selectedGlobalIndex ?: return@Canvas
        if (idx !in viewport.startIndex until viewport.endIndexExclusive || idx !in candles.indices) {
            return@Canvas
        }

        val plotLeft = 0f
        val plotRight = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
        val plotTop = topPaddingPx
        val plotBottom = (size.height - bottomAxisHeightPx).coerceAtLeast(plotTop + 1f)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val localIdx = idx - viewport.startIndex
        val candle = candles[idx]
        val centerX = ChartScaleCalculator.visibleIndexToCenterX(
            visibleIndex = localIdx,
            visibleCount = viewport.visibleCount,
            plotLeftPx = plotLeft,
            plotWidthPx = plotWidth
        )
        val closeY = ChartScaleCalculator.priceToY(
            price = candle.close,
            bounds = priceBounds,
            topPx = plotTop,
            plotHeightPx = plotHeight
        ).coerceIn(plotTop, plotBottom)

        // Vertical & horizontal crosshair hairlines
        drawLine(
            color = Color(palette.crosshairArgb),
            start = Offset(centerX, plotTop),
            end = Offset(centerX, plotBottom),
            strokeWidth = 1.4f,
            pathEffect = dashEffect
        )
        drawLine(
            color = Color(palette.crosshairArgb),
            start = Offset(plotLeft, closeY),
            end = Offset(plotRight, closeY),
            strokeWidth = 1.4f,
            pathEffect = dashEffect
        )

        // Node circle on close price
        drawCircle(
            color = palette.accentCyan,
            radius = 5.5f,
            center = Offset(centerX, closeY)
        )

        // Right Y-axis price pill
        val pillHeight = 20f
        val yPillTop = (closeY - pillHeight / 2f).coerceIn(plotTop, (plotBottom - pillHeight).coerceAtLeast(plotTop))
        drawRoundRect(
            color = palette.accentCyan,
            topLeft = Offset(plotRight + 2f, yPillTop),
            size = Size((rightAxisWidthPx - 4f).coerceAtLeast(24f), pillHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.1f", candle.close),
            plotRight + 5f,
            yPillTop + pillHeight - 5f,
            badgeTextPaint
        )

        // Bottom X-axis time pill
        val timeText = pillTimeFormat.format(Date(candle.timestamp))
        val xPillWidth = 48f
        val xPillLeft = (centerX - xPillWidth / 2f).coerceIn(plotLeft, (plotRight - xPillWidth).coerceAtLeast(plotLeft))
        drawRoundRect(
            color = palette.accentCyan,
            topLeft = Offset(xPillLeft, plotBottom + 2f),
            size = Size(xPillWidth, 18f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawContext.canvas.nativeCanvas.drawText(
            timeText,
            xPillLeft + 6f,
            plotBottom + 15f,
            badgeTextPaint
        )
    }
}
