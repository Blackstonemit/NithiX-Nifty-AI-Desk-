package com.example.niftychart.presentation.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SmaPeriod
import com.example.ui.theme.LocalChartPalette
import kotlin.math.abs

/**
 * Reusable interactive Compose Canvas candlestick chart component combining:
 * - [CandlestickCanvas] for dynamic auto-scaled bullish/bearish bars & axes
 * - [MovingAverageOverlay] for toggleable SMA 20/50/100/200 lines
 * - [ChartCrosshair] for touch & long-press candle inspection
 * Supports horizontal pan scrolling, two-finger pinch-to-zoom, tap/drag crosshair, and long-press inspect.
 */
@Composable
fun NiftyCandlestickChart(
    candles: List<OHLCV>,
    visibleCandleCount: Int,
    scrollRightOffset: Int,
    selectedGlobalIndex: Int?,
    isCrosshairLocked: Boolean,
    enabledSmas: Set<SmaPeriod>,
    smaLines: Map<SmaPeriod, List<Double?>>,
    onPanByBars: (Int) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onSelectCandle: (Int?, Boolean) -> Unit,
    onResetViewport: () -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 280.dp
) {
    val palette = LocalChartPalette.current
    val density = LocalDensity.current
    val rightAxisWidthPx = remember(density) { with(density) { 54.dp.toPx() } }

    val viewport = remember(candles.size, visibleCandleCount, scrollRightOffset) {
        ChartScaleCalculator.computeViewport(
            totalCandles = candles.size,
            visibleCount = visibleCandleCount,
            scrollRightOffset = scrollRightOffset
        )
    }

    val visibleSlice = remember(candles, viewport) {
        if (viewport.visibleCount <= 0) emptyList()
        else candles.subList(viewport.startIndex, viewport.endIndexExclusive)
    }

    val visibleSmaValues = remember(smaLines, enabledSmas, viewport) {
        if (enabledSmas.isEmpty() || viewport.visibleCount <= 0) emptyList()
        else buildList {
            for (sma in enabledSmas) {
                val series = smaLines[sma] ?: continue
                for (i in viewport.startIndex until viewport.endIndexExclusive) {
                    series.getOrNull(i)?.let { add(it) }
                }
            }
        }
    }

    val priceBounds = remember(visibleSlice, visibleSmaValues) {
        ChartScaleCalculator.computePriceBounds(
            visibleCandles = visibleSlice,
            extraValues = visibleSmaValues,
            verticalPaddingRatio = 0.08
        )
    }

    val currentViewport by rememberUpdatedState(viewport)
    val currentCandlesSize by rememberUpdatedState(candles.size)
    val currentLocked by rememberUpdatedState(isCrosshairLocked)
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .testTag("nifty_candlestick_chart")
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val plotWidthPx = (totalWidthPx - rightAxisWidthPx).coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap / Long-press / Double-tap gesture layer
                .pointerInput(candles.size, plotWidthPx) {
                    detectTapGestures(
                        onTap = { pos ->
                            val vp = currentViewport
                            if (vp.visibleCount <= 0) return@detectTapGestures
                            val localIdx = ChartScaleCalculator.xToVisibleIndex(
                                xPx = pos.x,
                                visibleCount = vp.visibleCount,
                                plotLeftPx = 0f,
                                plotWidthPx = plotWidthPx
                            )
                            val globalIdx = (vp.startIndex + localIdx).coerceIn(0, (currentCandlesSize - 1).coerceAtLeast(0))
                            if (currentLocked && selectedGlobalIndex == globalIdx) {
                                onSelectCandle(null, false)
                            } else {
                                onSelectCandle(globalIdx, true)
                            }
                        },
                        onLongPress = { pos ->
                            val vp = currentViewport
                            if (vp.visibleCount <= 0) return@detectTapGestures
                            val localIdx = ChartScaleCalculator.xToVisibleIndex(
                                xPx = pos.x,
                                visibleCount = vp.visibleCount,
                                plotLeftPx = 0f,
                                plotWidthPx = plotWidthPx
                            )
                            val globalIdx = (vp.startIndex + localIdx).coerceIn(0, (currentCandlesSize - 1).coerceAtLeast(0))
                            onSelectCandle(globalIdx, true)
                        },
                        onDoubleTap = {
                            onSelectCandle(null, false)
                            onResetViewport()
                        }
                    )
                }
                // Coordinated 2-finger pinch-zoom + 1-finger pan OR crosshair scrub
                .pointerInput(plotWidthPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        accumulatedDragPx = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (abs(zoomChange - 1f) > 0.015f) {
                                    onPinchZoom(zoomChange)
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                val dx = change.position.x - change.previousPosition.x
                                if (abs(dx) > 0.5f) {
                                    val vp = currentViewport
                                    if (currentLocked) {
                                        val localIdx = ChartScaleCalculator.xToVisibleIndex(
                                            xPx = change.position.x,
                                            visibleCount = vp.visibleCount,
                                            plotLeftPx = 0f,
                                            plotWidthPx = plotWidthPx
                                        )
                                        val globalIdx = (vp.startIndex + localIdx)
                                            .coerceIn(0, (currentCandlesSize - 1).coerceAtLeast(0))
                                        onSelectCandle(globalIdx, true)
                                    } else {
                                        accumulatedDragPx += dx
                                        val slotW = ChartScaleCalculator.computeStepWidthPx(plotWidthPx, vp.visibleCount)
                                        val bars = ChartScaleCalculator.computePanDeltaBars(accumulatedDragPx, slotW)
                                        if (bars != 0) {
                                            onPanByBars(bars)
                                            accumulatedDragPx -= bars * slotW
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            CandlestickCanvas(
                candles = candles,
                viewport = viewport,
                priceBounds = priceBounds,
                selectedGlobalIndex = selectedGlobalIndex,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nifty_candlestick_canvas")
            )

            MovingAverageOverlay(
                smaLines = smaLines,
                enabledSmas = enabledSmas,
                viewport = viewport,
                priceBounds = priceBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("moving_average_overlay")
            )

            ChartCrosshair(
                candles = candles,
                viewport = viewport,
                priceBounds = priceBounds,
                selectedGlobalIndex = selectedGlobalIndex,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chart_crosshair")
            )
        }
    }
}
