package com.example.niftychart.presentation.chart

import com.example.niftychart.domain.model.OHLCV
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Index window slice [startIndex, endIndexExclusive) into the full candle list.
 */
data class ChartViewportRange(
    val startIndex: Int,
    val endIndexExclusive: Int
) {
    val visibleCount: Int get() = (endIndexExclusive - startIndex).coerceAtLeast(0)
}

/**
 * Auto-scaled min/max price bounds (including vertical padding margin) for the visible slice.
 */
data class PriceScaleBounds(
    val minPrice: Double,
    val maxPrice: Double
) {
    val span: Double get() = (maxPrice - minPrice).coerceAtLeast(1e-6)
}

/**
 * Pure deterministic scaling and coordinate transformation math for custom Compose Canvas charts.
 */
object ChartScaleCalculator {

    fun computeViewport(
        totalCandles: Int,
        visibleCount: Int,
        scrollRightOffset: Int
    ): ChartViewportRange {
        if (totalCandles <= 0) return ChartViewportRange(0, 0)
        val clampedVisible = visibleCount.coerceIn(1, totalCandles)
        val maxOffset = (totalCandles - clampedVisible).coerceAtLeast(0)
        val clampedOffset = scrollRightOffset.coerceIn(0, maxOffset)
        val endExclusive = (totalCandles - clampedOffset).coerceIn(1, totalCandles)
        val start = (endExclusive - clampedVisible).coerceAtLeast(0)
        return ChartViewportRange(startIndex = start, endIndexExclusive = endExclusive)
    }

    fun computePriceBounds(
        visibleCandles: List<OHLCV>,
        extraValues: List<Double?> = emptyList(),
        verticalPaddingRatio: Double = 0.08
    ): PriceScaleBounds {
        if (visibleCandles.isEmpty()) return PriceScaleBounds(0.0, 1.0)
        var rawMin = Double.POSITIVE_INFINITY
        var rawMax = Double.NEGATIVE_INFINITY

        for (c in visibleCandles) {
            if (c.low.isFinite()) rawMin = min(rawMin, c.low)
            if (c.high.isFinite()) rawMax = max(rawMax, c.high)
        }
        for (v in extraValues) {
            if (v != null && v.isFinite()) {
                rawMin = min(rawMin, v)
                rawMax = max(rawMax, v)
            }
        }

        if (!rawMin.isFinite() || !rawMax.isFinite()) {
            return PriceScaleBounds(0.0, 1.0)
        }

        val rawSpan = (rawMax - rawMin).let { if (it <= 1e-6) max(rawMax * 0.005, 1.0) else it }
        val pad = rawSpan * verticalPaddingRatio.coerceIn(0.0, 0.4)
        return PriceScaleBounds(
            minPrice = rawMin - pad,
            maxPrice = rawMax + pad
        )
    }

    fun priceToY(
        price: Double,
        bounds: PriceScaleBounds,
        topPx: Float,
        plotHeightPx: Float
    ): Float {
        if (plotHeightPx <= 0f) return topPx
        val ratio = ((price - bounds.minPrice) / bounds.span).toFloat().coerceIn(-0.5f, 1.5f)
        return topPx + plotHeightPx * (1f - ratio)
    }

    fun yToPrice(
        yPx: Float,
        bounds: PriceScaleBounds,
        topPx: Float,
        plotHeightPx: Float
    ): Double {
        if (plotHeightPx <= 0f) return bounds.minPrice
        val normalized = (1f - ((yPx - topPx) / plotHeightPx)).toDouble()
        return bounds.minPrice + normalized * bounds.span
    }

    fun visibleIndexToCenterX(
        visibleIndex: Int,
        visibleCount: Int,
        plotLeftPx: Float,
        plotWidthPx: Float
    ): Float {
        if (visibleCount <= 0 || plotWidthPx <= 0f) return plotLeftPx
        val slotWidth = plotWidthPx / visibleCount.toFloat()
        return plotLeftPx + (visibleIndex + 0.5f) * slotWidth
    }

    fun xToVisibleIndex(
        xPx: Float,
        visibleCount: Int,
        plotLeftPx: Float,
        plotWidthPx: Float
    ): Int {
        if (visibleCount <= 0 || plotWidthPx <= 0f) return 0
        val relative = ((xPx - plotLeftPx) / plotWidthPx).coerceIn(0f, 0.9999f)
        return (relative * visibleCount).toInt().coerceIn(0, visibleCount - 1)
    }

    fun rsiToY(
        rsiValue: Double,
        topPx: Float,
        plotHeightPx: Float
    ): Float {
        if (plotHeightPx <= 0f) return topPx
        val ratio = (rsiValue.coerceIn(0.0, 100.0) / 100.0).toFloat()
        return topPx + plotHeightPx * (1f - ratio)
    }

    fun computeStepWidthPx(plotWidthPx: Float, visibleCount: Int): Float {
        return if (visibleCount <= 0) plotWidthPx else (plotWidthPx / visibleCount.toFloat())
    }

    fun computeCandleBodyWidthPx(slotWidthPx: Float): Float {
        return (slotWidthPx * 0.64f).coerceIn(2f, 24f)
    }

    fun computePanDeltaBars(dragDeltaPx: Float, slotWidthPx: Float): Int {
        if (slotWidthPx <= 0.5f) return 0
        return (dragDeltaPx / slotWidthPx).roundToInt()
    }
}
