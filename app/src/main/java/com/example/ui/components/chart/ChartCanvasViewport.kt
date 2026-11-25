package com.example.ui.components.chart

import android.graphics.Picture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.chart.TimeframeAggregationEngine
import com.example.model.Candle
import com.example.model.ChartAnchorPoint
import com.example.model.ChartDrawing
import com.example.model.ChartType
import com.example.model.DrawingToolType
import com.example.model.PriceScaleMode
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-FPS Compose Canvas rendering engine supporting 7 chart styles, pinch-zoom, pan,
 * Linear/Log/Percentage scale, indicators, SMC zones, S&R clusters, VPVR, 16 pattern markers,
 * 12 vector drawing tools, Risk R:R levels, and bitmap Picture snapshot capture.
 */
@Composable
fun ChartCanvasViewport(
    state: ChartWorkstationUiState,
    canvasHeight: Dp,
    selectedVisibleIndex: Int?,
    onSelectVisibleIndex: (Int?) -> Unit,
    onPanBars: (Int) -> Unit,
    onZoomBars: (Int) -> Unit,
    onDoubleTapReset: () -> Unit,
    onDrawingTap: (rawAnchor: ChartAnchorPoint, snappedAnchor: ChartAnchorPoint) -> Unit,
    snapshotPicture: Picture? = null,
    modifier: Modifier = Modifier
) {
    val candlesAll = state.displayCandles.ifEmpty { state.candles }
    if (candlesAll.isEmpty()) return

    val palette = LocalChartPalette.current
    val density = LocalDensity.current
    val pocLabel = stringResource(R.string.canvas_label_poc)
    val defaultNoteLabel = stringResource(R.string.canvas_default_note)
    val rightAxisMarginPx = remember(density) { with(density) { 54.dp.toPx() } }

    val visCount = state.visibleCandleCount.coerceIn(12, min(250, candlesAll.size))
    val endIdx = (candlesAll.size - state.scrollRightOffset).coerceIn(visCount, candlesAll.size)
    val startIdx = (endIdx - visCount).coerceAtLeast(0)
    val visCandles = candlesAll.subList(startIdx, endIdx)

    val axisPaint = remember(palette.axisLabelArgb) {
        android.graphics.Paint().apply {
            color = palette.axisLabelArgb
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val tagPaint = remember(palette.priceLineTextArgb) {
        android.graphics.Paint().apply {
            color = palette.priceLineTextArgb
            textSize = 21f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
    }
    val overlayLabelPaint = remember(palette.textPrimary) {
        android.graphics.Paint().apply {
            color = palette.axisLabelArgb
            textSize = 20f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val baselineRef = visCandles.first().close.coerceAtLeast(0.01)
    fun transformPrice(price: Double): Double = when (state.scaleMode) {
        PriceScaleMode.LINEAR -> price
        PriceScaleMode.LOGARITHMIC -> ln(price.coerceAtLeast(0.01))
        PriceScaleMode.PERCENTAGE -> ((price - baselineRef) / baselineRef) * 100.0
    }

    fun inversePrice(transformed: Double): Double = when (state.scaleMode) {
        PriceScaleMode.LINEAR -> transformed
        PriceScaleMode.LOGARITHMIC -> kotlin.math.exp(transformed)
        PriceScaleMode.PERCENTAGE -> baselineRef * (1.0 + transformed / 100.0)
    }

    val transformedMinRaw = visCandles.minOf { transformPrice(it.low) }
    val transformedMaxRaw = visCandles.maxOf { transformPrice(it.high) }
    val spanRaw = max(transformedMaxRaw - transformedMinRaw, 1e-4)
    val yPad = spanRaw * (0.10 / state.yZoomFactor.coerceIn(0.45f, 3.0f))
    val minT = transformedMinRaw - yPad
    val maxT = transformedMaxRaw + yPad
    val rangeT = max(maxT - minT, 1e-4)

    val activeVisIdx = selectedVisibleIndex?.coerceIn(0, visCandles.lastIndex)
    val isMultiDay = state.timeframe.minutes >= 375
    val popupTimeFmt = remember(isMultiDay) {
        SimpleDateFormat(if (isMultiDay) "dd MMM yyyy" else "dd MMM hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val gestureModifier = Modifier
        .pointerInput(visCandles, state.activeDrawingTool, state.scaleMode, rightAxisMarginPx) {
            detectTapGestures(
                onDoubleTap = { onDoubleTapReset() },
                onTap = { offset ->
                    val plotW = (size.width - rightAxisMarginPx).coerceAtLeast(1f)
                    val colW = plotW / visCandles.size
                    val vIdx = (offset.x / colW).toInt().coerceIn(0, visCandles.lastIndex)
                    val tappedCandle = visCandles[vIdx]
                    val tappedT = maxT - (offset.y / size.height) * rangeT
                    val tappedPrice = inversePrice(tappedT)

                    if (state.activeDrawingTool != DrawingToolType.NONE) {
                        val ohlc = listOf(tappedCandle.open, tappedCandle.high, tappedCandle.low, tappedCandle.close)
                        val snappedPrice = ohlc.minByOrNull { abs(it - tappedPrice) } ?: tappedPrice
                        onDrawingTap(
                            ChartAnchorPoint(tappedCandle.timestamp, tappedPrice),
                            ChartAnchorPoint(tappedCandle.timestamp, snappedPrice)
                        )
                    } else {
                        onSelectVisibleIndex(if (activeVisIdx == vIdx) null else vIdx)
                    }
                }
            )
        }
        .pointerInput(visCandles.size, state.activeDrawingTool, rightAxisMarginPx) {
            if (state.activeDrawingTool == DrawingToolType.NONE) {
                var dragAccum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                        val plotW = (size.width - rightAxisMarginPx).coerceAtLeast(1f)
                        val colW = max(plotW / visCandles.size, 4f)
                        if (abs(dragAccum) >= colW) {
                            val bars = (dragAccum / colW).roundToInt()
                            if (bars != 0) {
                                onPanBars(bars)
                                dragAccum -= bars * colW
                            }
                        }
                    }
                )
            }
        }
        .pointerInput(visCandles.size) {
            detectTransformGestures { _, _, zoom, _ ->
                if (zoom != 1f && abs(zoom - 1f) > 0.03f) {
                    val deltaBars = if (zoom > 1f) -3 else 3
                    onZoomBars(deltaBars)
                }
            }
        }

    val recordModifier = if (snapshotPicture != null) {
        Modifier.drawWithCache {
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            onDrawWithContent {
                val recCanvas = androidx.compose.ui.graphics.Canvas(snapshotPicture.beginRecording(w, h))
                draw(
                    density = this,
                    layoutDirection = layoutDirection,
                    canvas = recCanvas,
                    size = size
                ) {
                    this@onDrawWithContent.drawContent()
                }
                snapshotPicture.endRecording()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPicture(snapshotPicture)
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.canvasBackground)
            .then(recordModifier)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("candlestick_price_canvas")
                .then(gestureModifier)
        ) {
            val width = size.width
            val height = size.height
            val plotW = (width - rightAxisMarginPx).coerceAtLeast(1f)
            val colW = plotW / visCandles.size
            val bodyW = max(colW * 0.62f, 2.2f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            fun getY(price: Double): Float {
                val t = transformPrice(price)
                return (height - ((t - minT) / rangeT * height)).toFloat().coerceIn(-40f, height + 40f)
            }

            fun getXForGlobalIndex(globalIdx: Int): Float? {
                if (globalIdx < startIdx || globalIdx >= endIdx) return null
                val local = globalIdx - startIdx
                return local * colW + colW / 2f
            }

            fun getXForTimestamp(ts: Long): Float {
                val exactIdx = candlesAll.indexOfFirst { it.timestamp >= ts }.let { if (it == -1) candlesAll.lastIndex else it }
                val local = exactIdx - startIdx
                return local * colW + colW / 2f
            }

            // 1. After-Hours Intraday Session Shading
            if (state.showAfterHours && !isMultiDay) {
                visCandles.forEachIndexed { localIdx, c ->
                    if (TimeframeAggregationEngine.isWithinAfterHoursIst(c.timestamp)) {
                        drawRect(
                            color = palette.afterHoursShade,
                            topLeft = Offset(localIdx * colW, 0f),
                            size = Size(colW, height)
                        )
                    }
                }
            }

            // 2. Price Grid & Right Y-Axis Scale
            val gridSteps = 5
            for (g in 0..gridSteps) {
                val gy = (height / gridSteps) * g
                if (state.showGrid) {
                    drawLine(
                        color = palette.gridLine,
                        start = Offset(0f, gy),
                        end = Offset(plotW, gy),
                        strokeWidth = 1f,
                        pathEffect = dash
                    )
                }
                val tVal = maxT - (g.toDouble() / gridSteps) * rangeT
                val labelStr = when (state.scaleMode) {
                    PriceScaleMode.PERCENTAGE -> String.format(Locale.US, "%+.1f%%", tVal)
                    else -> {
                        val pVal = inversePrice(tVal)
                        if (pVal >= 1000.0) String.format(Locale.US, "%.0f", pVal)
                        else String.format(Locale.US, "%.1f", pVal)
                    }
                }
                val labelY = when (g) {
                    0 -> gy + 18f
                    gridSteps -> gy - 6f
                    else -> gy + 6f
                }
                drawContext.canvas.nativeCanvas.drawText(labelStr, plotW + 6f, labelY, axisPaint)
            }

            // 3. Visible-Range Volume Profile Histogram (Right Edge Background)
            if (state.showVolumeProfile && state.volumeProfile.bins.isNotEmpty()) {
                val maxBinVol = state.volumeProfile.bins.maxOf { it.totalVolume }.coerceAtLeast(1L)
                val maxHistW = plotW * 0.22f
                state.volumeProfile.bins.forEach { bin ->
                    val topY = getY(bin.highPrice)
                    val botY = getY(bin.lowPrice)
                    val h = max(abs(botY - topY) - 1f, 2f)
                    val ratio = bin.totalVolume.toFloat() / maxBinVol.toFloat()
                    val bullW = maxHistW * ratio * (bin.bullVolume.toFloat() / max(bin.totalVolume, 1L).toFloat())
                    val bearW = maxHistW * ratio - bullW
                    val leftEdge = plotW - maxHistW * ratio
                    drawRect(
                        color = palette.bullGreen.copy(alpha = 0.16f),
                        topLeft = Offset(leftEdge, min(topY, botY)),
                        size = Size(bullW, h)
                    )
                    drawRect(
                        color = palette.bearRed.copy(alpha = 0.16f),
                        topLeft = Offset(leftEdge + bullW, min(topY, botY)),
                        size = Size(bearW, h)
                    )
                }
                if (state.volumeProfile.pocPrice > 0.0) {
                    val pocY = getY(state.volumeProfile.pocPrice)
                    drawLine(
                        color = palette.goldAccent.copy(alpha = 0.8f),
                        start = Offset(0f, pocY),
                        end = Offset(plotW, pocY),
                        strokeWidth = 1.6f,
                        pathEffect = dash
                    )
                    drawContext.canvas.nativeCanvas.drawText(pocLabel, 8f, pocY - 4f, overlayLabelPaint)
                }
            }

            // 4. Smart Money Concepts (Order Blocks, FVGs, Dealing Eq, ORB)
            if (state.showSmcOverlays) {
                state.marketStructure.orderBlocks.forEach { ob ->
                    val sx = (max(0, ob.startIndex - startIdx)) * colW
                    val topY = getY(ob.topPrice)
                    val botY = getY(ob.bottomPrice)
                    val obCol = if (ob.isBullish) palette.bullGreen else palette.bearRed
                    drawRect(
                        color = obCol.copy(alpha = if (ob.isMitigated) 0.07f else 0.16f),
                        topLeft = Offset(sx, min(topY, botY)),
                        size = Size((plotW - sx).coerceAtLeast(10f), max(abs(botY - topY), 4f))
                    )
                }
                state.marketStructure.fvgZones.filter { !it.isMitigated }.forEach { fvg ->
                    val sx = (max(0, fvg.startIndex - startIdx)) * colW
                    val topY = getY(fvg.topPrice)
                    val botY = getY(fvg.bottomPrice)
                    val fCol = if (fvg.isBullish) palette.accentCyan else palette.goldAccent
                    drawRect(
                        color = fCol.copy(alpha = 0.13f),
                        topLeft = Offset(sx, min(topY, botY)),
                        size = Size((plotW - sx).coerceAtLeast(8f), max(abs(botY - topY), 3f))
                    )
                }
                state.marketStructure.structureBreaks.forEach { brk ->
                    val x = getXForGlobalIndex(brk.index) ?: return@forEach
                    val y = getY(brk.levelPrice)
                    val col = if (brk.isBullish) palette.bullGreen else palette.bearRed
                    drawLine(
                        color = col,
                        start = Offset(max(0f, x - colW * 4f), y),
                        end = Offset(x, y),
                        strokeWidth = 1.8f,
                        pathEffect = dash
                    )
                    drawContext.canvas.nativeCanvas.drawText(brk.type, x - 14f, y - 5f, overlayLabelPaint)
                }
            }

            // 5. Automatic Support & Resistance Clusters
            if (state.showSrLevels) {
                state.srLevels.take(6).forEach { lvl ->
                    val y = getY(lvl.price)
                    if (y in 4f..(height - 4f)) {
                        val col = if (lvl.isSupport) palette.bullGreen.copy(alpha = 0.55f) else palette.bearRed.copy(alpha = 0.55f)
                        drawLine(
                            color = col,
                            start = Offset(0f, y),
                            end = Offset(plotW, y),
                            strokeWidth = 1.3f,
                            pathEffect = dash
                        )
                    }
                }
            }

            // 6. Ichimoku Cloud & Channel Bands
            state.indicatorSeries.ichimoku?.let { ichi ->
                val fillPath = Path()
                for (localIdx in visCandles.indices) {
                    val gIdx = startIdx + localIdx
                    val x = localIdx * colW + colW / 2f
                    val yA = getY(ichi.senkouSpanA.getOrElse(gIdx) { visCandles[localIdx].close })
                    if (localIdx == 0) fillPath.moveTo(x, yA) else fillPath.lineTo(x, yA)
                }
                for (localIdx in visCandles.indices.reversed()) {
                    val gIdx = startIdx + localIdx
                    val x = localIdx * colW + colW / 2f
                    val yB = getY(ichi.senkouSpanB.getOrElse(gIdx) { visCandles[localIdx].close })
                    fillPath.lineTo(x, yB)
                }
                fillPath.close()
                drawPath(fillPath, color = palette.bullGreen.copy(alpha = 0.12f))
            }

            state.indicatorSeries.bands.forEach { (_, bandTriple) ->
                val upperPath = Path()
                val lowerPath = Path()
                val midPath = Path()
                visCandles.forEachIndexed { localIdx, c ->
                    val gIdx = startIdx + localIdx
                    val x = localIdx * colW + colW / 2f
                    val uY = getY(bandTriple.first.getOrElse(gIdx) { c.high })
                    val mY = getY(bandTriple.second.getOrElse(gIdx) { c.close })
                    val lY = getY(bandTriple.third.getOrElse(gIdx) { c.low })
                    if (localIdx == 0) {
                        upperPath.moveTo(x, uY); midPath.moveTo(x, mY); lowerPath.moveTo(x, lY)
                    } else {
                        upperPath.lineTo(x, uY); midPath.lineTo(x, mY); lowerPath.lineTo(x, lY)
                    }
                }
                drawPath(upperPath, color = palette.neutralBlue.copy(alpha = 0.7f), style = Stroke(1.6f))
                drawPath(midPath, color = palette.neutralBlue.copy(alpha = 0.45f), style = Stroke(1.2f, pathEffect = dash))
                drawPath(lowerPath, color = palette.neutralBlue.copy(alpha = 0.7f), style = Stroke(1.6f))
            }

            // 7. Moving Average / VWAP Line Overlays
            state.indicatorConfigs.filter { it.enabled && it.type.isOverlay }.forEach { cfg ->
                val series = state.indicatorSeries.overlayLines[cfg.id] ?: return@forEach
                val linePath = Path()
                visCandles.forEachIndexed { localIdx, c ->
                    val gIdx = startIdx + localIdx
                    val x = localIdx * colW + colW / 2f
                    val y = getY(series.getOrElse(gIdx) { c.close })
                    if (localIdx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(linePath, color = Color(cfg.colorHex).copy(alpha = 0.9f), style = Stroke(2.2f))
            }

            // Supertrend & PSAR
            if (state.indicatorSeries.supertrendSeries.isNotEmpty()) {
                for (localIdx in 1 until visCandles.size) {
                    val gIdx = startIdx + localIdx
                    val prev = state.indicatorSeries.supertrendSeries.getOrNull(gIdx - 1) ?: continue
                    val curr = state.indicatorSeries.supertrendSeries.getOrNull(gIdx) ?: continue
                    val x0 = (localIdx - 1) * colW + colW / 2f
                    val x1 = localIdx * colW + colW / 2f
                    drawLine(
                        color = if (curr.second) palette.bullGreen else palette.bearRed,
                        start = Offset(x0, getY(prev.first)),
                        end = Offset(x1, getY(curr.first)),
                        strokeWidth = 2.2f
                    )
                }
            }
            if (state.indicatorSeries.psarSeries.isNotEmpty()) {
                visCandles.forEachIndexed { localIdx, c ->
                    val sarVal = state.indicatorSeries.psarSeries.getOrNull(startIdx + localIdx) ?: return@forEachIndexed
                    val x = localIdx * colW + colW / 2f
                    val y = getY(sarVal)
                    drawCircle(
                        color = if (sarVal <= c.close) palette.bullGreen else palette.bearRed,
                        radius = max(colW * 0.16f, 2.2f),
                        center = Offset(x, y)
                    )
                }
            }

            // 8. Primary Price Series (All 7 Chart Types!)
            when (state.chartType) {
                ChartType.CANDLESTICK, ChartType.HOLLOW_CANDLESTICK, ChartType.HEIKIN_ASHI -> {
                    visCandles.forEachIndexed { localIdx, c ->
                        val x = localIdx * colW + colW / 2f
                        val isBull = c.close >= c.open
                        val col = if (isBull) palette.bullGreen else palette.bearRed
                        val oY = getY(c.open)
                        val cY = getY(c.close)
                        val hY = getY(c.high)
                        val lY = getY(c.low)

                        if (activeVisIdx == localIdx) {
                            drawRect(
                                color = palette.accentCyan.copy(alpha = 0.14f),
                                topLeft = Offset(localIdx * colW, 0f),
                                size = Size(colW, height)
                            )
                        }

                        drawLine(col, Offset(x, hY), Offset(x, lY), strokeWidth = if (activeVisIdx == localIdx) 2.5f else 1.5f)
                        val topY = min(oY, cY)
                        val boxH = max(abs(oY - cY), 2.5f)
                        val isHollow = state.chartType == ChartType.HOLLOW_CANDLESTICK && isBull
                        drawRect(
                            color = col,
                            topLeft = Offset(x - bodyW / 2f, topY),
                            size = Size(bodyW, boxH),
                            style = if (isHollow) Stroke(width = 2.0f) else androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }
                }
                ChartType.OHLC_BARS -> {
                    visCandles.forEachIndexed { localIdx, c ->
                        val x = localIdx * colW + colW / 2f
                        val col = if (c.close >= c.open) palette.bullGreen else palette.bearRed
                        val oY = getY(c.open)
                        val cY = getY(c.close)
                        val hY = getY(c.high)
                        val lY = getY(c.low)
                        drawLine(col, Offset(x, hY), Offset(x, lY), strokeWidth = 2.0f)
                        drawLine(col, Offset(x - bodyW * 0.45f, oY), Offset(x, oY), strokeWidth = 2.0f)
                        drawLine(col, Offset(x, cY), Offset(x + bodyW * 0.45f, cY), strokeWidth = 2.0f)
                    }
                }
                ChartType.LINE, ChartType.AREA, ChartType.BASELINE -> {
                    val linePath = Path()
                    val areaPath = Path()
                    visCandles.forEachIndexed { localIdx, c ->
                        val x = localIdx * colW + colW / 2f
                        val y = getY(c.close)
                        if (localIdx == 0) {
                            linePath.moveTo(x, y)
                            areaPath.moveTo(x, height)
                            areaPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }
                        if (localIdx == visCandles.lastIndex) {
                            areaPath.lineTo(x, height)
                            areaPath.close()
                        }
                    }
                    if (state.chartType == ChartType.AREA) {
                        drawPath(
                            areaPath,
                            brush = Brush.verticalGradient(
                                listOf(palette.accentCyan.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                    } else if (state.chartType == ChartType.BASELINE) {
                        val baseY = getY(baselineRef)
                        drawLine(palette.textMuted, Offset(0f, baseY), Offset(plotW, baseY), strokeWidth = 1.4f, pathEffect = dash)
                        drawPath(areaPath, color = palette.bullGreen.copy(alpha = 0.14f))
                    }
                    drawPath(linePath, color = palette.accentCyan, style = Stroke(2.6f))
                }
            }

            // 9. Candlestick Pattern Markers
            if (state.showPatternBadges) {
                state.detectedPatterns.forEach { pat ->
                    val x = getXForGlobalIndex(pat.candleIndex) ?: return@forEach
                    val c = candlesAll.getOrNull(pat.candleIndex) ?: return@forEach
                    val isBull = pat.sentiment == "BULLISH"
                    val isBear = pat.sentiment == "BEARISH"
                    val markerCol = when {
                        isBull -> palette.bullGreen
                        isBear -> palette.bearRed
                        else -> palette.goldAccent
                    }
                    val cy = if (isBull) getY(c.low) + 12f else getY(c.high) - 12f
                    drawCircle(color = markerCol, radius = 5f, center = Offset(x, cy.coerceIn(6f, height - 6f)))
                }
            }

            // 10. Risk Position Entry / SL / Targets Overlay
            if (state.showRiskOnChart) {
                val rp = state.riskPlan
                val eY = getY(rp.entryPrice)
                val slY = getY(rp.stopLossPrice)
                val t2Y = getY(rp.target2)
                drawLine(palette.accentCyan, Offset(0f, eY), Offset(plotW, eY), strokeWidth = 1.8f)
                drawLine(palette.bearRed, Offset(0f, slY), Offset(plotW, slY), strokeWidth = 1.8f, pathEffect = dash)
                drawLine(palette.bullGreen, Offset(0f, t2Y), Offset(plotW, t2Y), strokeWidth = 1.8f, pathEffect = dash)
            }

            // 11. Saved Vector Drawings (12 Tools!)
            if (!state.hideAllDrawings) {
                val toRender = state.drawings.filter { !it.isHidden }
                toRender.forEach { d ->
                    val col = Color(d.colorHex)
                    val effect = if (d.isDashed) dash else null
                    val p0 = d.points.getOrNull(0) ?: return@forEach
                    val x0 = getXForTimestamp(p0.timestamp)
                    val y0 = getY(p0.price)

                    when (d.toolType) {
                        DrawingToolType.HORIZONTAL_LINE -> {
                            drawLine(col, Offset(0f, y0), Offset(plotW, y0), strokeWidth = d.strokeWidth, pathEffect = effect)
                        }
                        DrawingToolType.VERTICAL_LINE -> {
                            drawLine(col, Offset(x0, 0f), Offset(x0, height), strokeWidth = d.strokeWidth, pathEffect = effect)
                        }
                        DrawingToolType.TEXT_NOTE -> {
                            drawCircle(col, 5f, Offset(x0, y0))
                            drawContext.canvas.nativeCanvas.drawText(d.text.ifBlank { defaultNoteLabel }, x0 + 8f, y0 - 4f, overlayLabelPaint)
                        }
                        DrawingToolType.TRENDLINE, DrawingToolType.ARROW_CALLOUT -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val x1 = getXForTimestamp(p1.timestamp)
                            val y1 = getY(p1.price)
                            drawLine(col, Offset(x0, y0), Offset(x1, y1), strokeWidth = d.strokeWidth, pathEffect = effect)
                            drawCircle(col, 4.5f, Offset(x0, y0))
                            drawCircle(col, 4.5f, Offset(x1, y1))
                        }
                        DrawingToolType.RAY -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val x1 = getXForTimestamp(p1.timestamp)
                            val y1 = getY(p1.price)
                            val dx = (x1 - x0).let { if (abs(it) < 1f) 1f else it }
                            val slope = (y1 - y0) / dx
                            val rayEndX = if (dx >= 0f) plotW else 0f
                            val rayEndY = y0 + slope * (rayEndX - x0)
                            drawLine(col, Offset(x0, y0), Offset(rayEndX, rayEndY), strokeWidth = d.strokeWidth, pathEffect = effect)
                        }
                        DrawingToolType.CHANNEL -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val x1 = getXForTimestamp(p1.timestamp)
                            val y1 = getY(p1.price)
                            val chOffset = 34f
                            drawLine(col, Offset(x0, y0), Offset(x1, y1), strokeWidth = d.strokeWidth)
                            drawLine(col, Offset(x0, y0 + chOffset), Offset(x1, y1 + chOffset), strokeWidth = d.strokeWidth, pathEffect = dash)
                        }
                        DrawingToolType.RECTANGLE_ZONE -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val x1 = getXForTimestamp(p1.timestamp)
                            val y1 = getY(p1.price)
                            val left = min(x0, x1)
                            val top = min(y0, y1)
                            val w = max(abs(x1 - x0), 6f)
                            val h = max(abs(y1 - y0), 6f)
                            drawRect(col.copy(alpha = 0.16f), Offset(left, top), Size(w, h))
                            drawRect(col, Offset(left, top), Size(w, h), style = Stroke(d.strokeWidth))
                        }
                        DrawingToolType.FIB_RETRACEMENT, DrawingToolType.FIB_EXTENSION -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val fibRatios = if (d.toolType == DrawingToolType.FIB_RETRACEMENT) {
                                listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
                            } else {
                                listOf(0.0, 0.618, 1.0, 1.272, 1.618, 2.0)
                            }
                            val x1 = getXForTimestamp(p1.timestamp)
                            val left = min(x0, x1).coerceAtLeast(0f)
                            val right = max(x0, x1).coerceAtMost(plotW)
                            val diff = p1.price - p0.price
                            fibRatios.forEach { ratio ->
                                val levelPrice = p0.price + diff * ratio
                                val fy = getY(levelPrice)
                                drawLine(col.copy(alpha = 0.75f), Offset(left, fy), Offset(right, fy), strokeWidth = 1.4f, pathEffect = dash)
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${String.format(Locale.US, "%.1f", ratio * 100)}%",
                                    right - 48f,
                                    fy - 3f,
                                    overlayLabelPaint
                                )
                            }
                        }
                        DrawingToolType.LONG_POSITION, DrawingToolType.SHORT_POSITION -> {
                            val p1 = d.points.getOrNull(1) ?: return@forEach
                            val x1 = getXForTimestamp(p1.timestamp)
                            val left = min(x0, x1)
                            val w = max(abs(x1 - x0), 40f)
                            val y1 = getY(p1.price)
                            val profitTop = min(y0, y1)
                            val profitH = max(abs(y1 - y0), 12f)
                            val riskTop = if (y1 < y0) y0 else y0 - profitH * 0.5f
                            val isLong = d.toolType == DrawingToolType.LONG_POSITION
                            val rewardCol = if (isLong) palette.bullGreen else palette.bearRed
                            val riskCol = if (isLong) palette.bearRed else palette.bullGreen
                            drawRect(rewardCol.copy(alpha = 0.2f), Offset(left, profitTop), Size(w, profitH))
                            drawRect(riskCol.copy(alpha = 0.18f), Offset(left, riskTop), Size(w, profitH * 0.5f))
                        }
                        DrawingToolType.NONE -> Unit
                    }
                }
                // Anchor preview dot while placing 2-point drawing
                state.pendingAnchor?.let { pa ->
                    drawCircle(
                        color = palette.goldAccent,
                        radius = 7f,
                        center = Offset(getXForTimestamp(pa.timestamp), getY(pa.price))
                    )
                }
            }

            // 12. Current Price Dotted Line + Axis Pill
            val latest = visCandles.last()
            val curY = getY(latest.close)
            drawLine(
                color = palette.accentCyan.copy(alpha = 0.85f),
                start = Offset(0f, curY),
                end = Offset(plotW, curY),
                strokeWidth = 1.4f,
                pathEffect = dash
            )
            drawRoundRect(
                color = palette.accentCyan,
                topLeft = Offset(plotW + 2f, curY - 12f),
                size = Size(rightAxisMarginPx - 4f, 24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            val curLabel = if (latest.close >= 1000.0) String.format(Locale.US, "%.0f", latest.close) else String.format(Locale.US, "%.1f", latest.close)
            drawContext.canvas.nativeCanvas.drawText(curLabel, plotW + 6f, curY + 7f, tagPaint)

            // 13. Synchronized Crosshair
            activeVisIdx?.let { vIdx ->
                val selX = vIdx * colW + colW / 2f
                val selY = getY(visCandles[vIdx].close)
                drawLine(palette.accentCyan, Offset(selX, 0f), Offset(selX, height), strokeWidth = 1.5f, pathEffect = dash)
                drawLine(palette.accentCyan, Offset(0f, selY), Offset(plotW, selY), strokeWidth = 1.5f, pathEffect = dash)
                drawCircle(Color.White, 5.5f, Offset(selX, selY))
                drawCircle(palette.accentCyan, 3.5f, Offset(selX, selY))
            }
        }

        // Crosshair OHLCV Popup Overlay
        AnimatedVisibility(
            visible = activeVisIdx != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if ((activeVisIdx ?: 0) < visCandles.size / 2) Alignment.TopEnd else Alignment.TopStart)
                .padding(top = 6.dp, start = 6.dp, end = if ((activeVisIdx ?: 0) < visCandles.size / 2) 56.dp else 6.dp)
        ) {
            activeVisIdx?.let { vIdx ->
                val c = visCandles[vIdx]
                val bull = c.close >= c.open
                val acc = if (bull) palette.bullGreen else palette.bearRed
                Column(
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 198.dp)
                        .shadow(8.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.popupBackground)
                        .border(1.dp, acc.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("ohlc_popup_overlay")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = popupTimeFmt.format(Date(c.timestamp)),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.goldAccent,
                            style = TabularNumericStyle
                        )
                        IconButton(
                            onClick = { onSelectVisibleIndex(null) },
                            modifier = Modifier.minimumInteractiveComponentSize().size(22.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_dismiss_ohlc), tint = palette.textMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                    OhlcLine(stringResource(R.string.label_open_short), c.open, palette.textPrimary)
                    OhlcLine(stringResource(R.string.label_high_short), c.high, palette.bullGreen)
                    OhlcLine(stringResource(R.string.label_low_short), c.low, palette.bearRed)
                    OhlcLine(stringResource(R.string.label_close_short), c.close, acc)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.chart_vol_format, c.volume.toString()),
                        fontSize = 9.sp,
                        color = palette.textSecondary,
                        style = TabularNumericStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun OhlcLine(label: String, value: Double, color: Color) {
    val palette = LocalChartPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 9.5.sp, color = palette.textSecondary, style = TabularNumericStyle)
        Text(
            String.format(Locale.US, "%,.2f", value),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            style = TabularNumericStyle
        )
    }
}
