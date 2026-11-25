package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.TechnicalCalculator
import com.example.engine.chart.TimeframeAggregationEngine
import com.example.model.Candle
import com.example.model.ChartType
import com.example.model.Timeframe
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Upgraded Dashboard Preview Chart + Synchronized Secondary Volume Bar Chart.
 * Preserves backward-compatible signature & test tags while adding multi-symbol badge,
 * quick chart-style switching, deterministic Signal Rating pill, AI Analyst shortcut,
 * Institutional Light / Financial Dark theme adaptation, and Expand Workstation trigger.
 */
@Composable
fun CandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    vwapValue: Double,
    emaValue: Double,
    selectedTimeframe: Timeframe = Timeframe.M15,
    onSelectTimeframe: (Timeframe) -> Unit = {},
    isStreaming: Boolean = false,
    isMarketOpen: Boolean = false,
    workstationState: ChartWorkstationUiState? = null,
    onExpandWorkstation: (() -> Unit)? = null,
    onSelectChartType: ((ChartType) -> Unit)? = null,
    onOpenAiAnalyst: (() -> Unit)? = null,
    onOpenOhlcvTerminal: (() -> Unit)? = null
) {
    val activeCandles = workstationState?.candles?.ifEmpty { candles } ?: candles
    if (activeCandles.isEmpty()) return

    val activeChartType = workstationState?.chartType ?: ChartType.CANDLESTICK
    val plottedCandles = remember(activeCandles, activeChartType) {
        if (activeChartType == ChartType.HEIKIN_ASHI) {
            TimeframeAggregationEngine.toHeikinAshi(activeCandles)
        } else activeCandles
    }

    val palette = LocalChartPalette.current
    var selectedCandleIndex by remember(plottedCandles.size) { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val activeIndex = selectedCandleIndex?.coerceIn(0, plottedCandles.lastIndex)
    val summaryCandle = activeIndex?.let { plottedCandles[it] } ?: plottedCandles.last()

    val isMultiDayTf = selectedTimeframe == Timeframe.DAILY ||
        selectedTimeframe == Timeframe.WEEKLY ||
        selectedTimeframe == Timeframe.MONTHLY
    val timeFormat = remember(isMultiDayTf) {
        SimpleDateFormat(if (isMultiDayTf) "dd MMM" else "hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }
    val axisTimeFormat = remember(isMultiDayTf) {
        SimpleDateFormat(if (isMultiDayTf) "dd MMM" else "HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val summaryTimeStr = remember(summaryCandle.timestamp, timeFormat) {
        timeFormat.format(Date(summaryCandle.timestamp))
    }
    val summaryChange = summaryCandle.close - summaryCandle.open
    val summaryChangePct = if (summaryCandle.open > 0) (summaryChange / summaryCandle.open) * 100.0 else 0.0

    val ema20Series = remember(plottedCandles) {
        TechnicalCalculator.calculateEmaSeries(plottedCandles.map { it.close }, 20)
    }

    val axisTextPaint = remember(palette.axisLabelArgb) {
        android.graphics.Paint().apply {
            color = palette.axisLabelArgb
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val xAxisLabels = remember(plottedCandles, axisTimeFormat) {
        val last = plottedCandles.lastIndex
        val indices = listOf(0, last / 4, last / 2, (last * 3) / 4, last).distinct()
        indices.map { idx -> axisTimeFormat.format(Date(plottedCandles[idx].timestamp)) }
    }

    val density = LocalDensity.current
    val rightAxisMarginPx = remember(density) { with(density) { 48.dp.toPx() } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .testTag("candlestick_chart"),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Bar: Symbol Title, Technical Signal Pill, Expand Workstation Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val symbolLabel = workstationState?.activeSymbol?.symbol ?: stringResource(R.string.dashboard_nifty_50)
                    Text(
                        text = stringResource(R.string.chart_title_format, symbolLabel),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TabularNumericStyle
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    workstationState?.technicalSignal?.let { sig ->
                        val sigColor = when {
                            sig.score >= 20 -> palette.bullGreen
                            sig.score <= -20 -> palette.bearRed
                            else -> palette.goldAccent
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.surfaceHighlight)
                                .border(1.dp, sigColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chart_signal_rating_format, sig.rating, sig.score),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = sigColor,
                                maxLines = 1,
                                softWrap = false,
                                style = TabularNumericStyle
                            )
                        }
                    }

                    if (onOpenOhlcvTerminal != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.surfaceHighlight)
                                .border(1.dp, palette.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .clickable { onOpenOhlcvTerminal() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("open_ohlcv_terminal_button")
                        ) {
                            Text(
                                stringResource(R.string.chart_pill_sma_rsi),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accentCyan,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (onOpenAiAnalyst != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.goldAccentDim)
                                .border(1.dp, palette.goldAccent.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .clickable { onOpenAiAnalyst() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("dashboard_ai_analyst_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.cd_ai_analyst),
                                    tint = palette.goldAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    stringResource(R.string.chart_pill_ai),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.goldAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (onExpandWorkstation != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.accentCyan)
                                .clickable { onExpandWorkstation() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("expand_workstation_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.OpenInFull,
                                    contentDescription = stringResource(R.string.cd_expand_workstation),
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    stringResource(R.string.chart_pill_workstation),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.surfaceHighlight)
                                .border(1.dp, palette.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chart_pill_compose_canvas),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accentCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Quick 7-Style Switcher Strip when integrated with Workstation Coordinator
            if (onSelectChartType != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ChartType.entries.forEach { ct ->
                        val sel = ct == activeChartType
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (sel) palette.goldAccentDim else palette.surfaceVariant)
                                .border(1.dp, if (sel) palette.goldAccent else palette.border, RoundedCornerShape(5.dp))
                                .clickable { onSelectChartType(ct) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                ct.shortLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) palette.goldAccent else palette.textSecondary,
                                style = TabularNumericStyle
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timeframe Selector Ribbon (All 13 supported intervals)
            val tfScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tfScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) palette.accentCyan else palette.surfaceHighlight)
                            .border(1.dp, if (isSelected) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                            .clickable { onSelectTimeframe(tf) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("chart_tf_${tf.label}")
                    ) {
                        Text(
                            text = tf.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Latest / Active Candle Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isMultiDayTf) summaryTimeStr else stringResource(R.string.label_ist_time_format, summaryTimeStr),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.goldAccent,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val pricePrefix = stringResource(
                            when {
                                isMarketOpen -> R.string.chart_prefix_ltp
                                isStreaming -> R.string.chart_prefix_sim
                                else -> R.string.chart_prefix_close
                            }
                        )
                        Text(
                            text = "$pricePrefix ₹${String.format(Locale.US, "%,.2f", summaryCandle.close)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (summaryChange >= 0) palette.bullGreen else palette.bearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "${if (summaryChange >= 0) "+" else ""}${String.format(Locale.US, "%.2f", summaryChange)} (${String.format(Locale.US, "%.2f", summaryChangePct)}%)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (summaryChange >= 0) palette.bullGreen else palette.bearRed,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend Ribbon + Gesture Hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(palette.bullGreen))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = stringResource(R.string.chart_legend_bull), fontSize = 9.sp, color = palette.textSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(palette.bearRed))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = stringResource(R.string.chart_legend_bear), fontSize = 9.sp, color = palette.textSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(8.dp).height(2.dp).background(palette.accentCyan))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.chart_legend_vwap_format, vwapValue.toInt()),
                        fontSize = 9.sp,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.width(8.dp).height(2.dp).background(palette.goldAccent))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.chart_legend_ema20_format, emaValue.toInt()),
                        fontSize = 9.sp,
                        color = palette.goldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.chart_drag_for_ohlc),
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val dragModifier = Modifier
                .pointerInput(plottedCandles, rightAxisMarginPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val plotWidth = (size.width - rightAxisMarginPx).coerceAtLeast(1f)
                            val candleWidth = plotWidth / plottedCandles.size
                            val idx = (offset.x / candleWidth).toInt().coerceIn(0, plottedCandles.size - 1)
                            selectedCandleIndex = idx
                            isDragging = true
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val plotWidth = (size.width - rightAxisMarginPx).coerceAtLeast(1f)
                            val candleWidth = plotWidth / plottedCandles.size
                            val idx = (change.position.x / candleWidth).toInt().coerceIn(0, plottedCandles.size - 1)
                            selectedCandleIndex = idx
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
                .pointerInput(plottedCandles, rightAxisMarginPx) {
                    detectTapGestures { offset ->
                        val plotWidth = (size.width - rightAxisMarginPx).coerceAtLeast(1f)
                        val candleWidth = plotWidth / plottedCandles.size
                        val idx = (offset.x / candleWidth).toInt().coerceIn(0, plottedCandles.size - 1)
                        selectedCandleIndex = if (selectedCandleIndex == idx) null else idx
                    }
                }

            // Primary Price Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.canvasBackground)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("candlestick_price_canvas")
                        .then(dragModifier)
                ) {
                    val width = size.width
                    val height = size.height
                    val plotWidth = (width - rightAxisMarginPx).coerceAtLeast(1f)

                    val rawMin = plottedCandles.minOf { it.low }
                    val rawMax = plottedCandles.maxOf { it.high }
                    val pad = max((rawMax - rawMin) * 0.08, 4.0)
                    val minPrice = rawMin - pad
                    val maxPrice = rawMax + pad
                    val priceRange = max(maxPrice - minPrice, 1.0)

                    val candleWidth = plotWidth / plottedCandles.size
                    val bodyWidth = max(candleWidth * 0.62f, 2.5f)

                    fun getY(price: Double): Float {
                        return (height - ((price - minPrice) / priceRange * height)).toFloat()
                    }

                    val gridSteps = 4
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    for (i in 0..gridSteps) {
                        val gridY = (height / gridSteps) * i
                        drawLine(
                            color = palette.gridLine,
                            start = Offset(0f, gridY),
                            end = Offset(plotWidth, gridY),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                        val priceAtGrid = maxPrice - (i.toDouble() / gridSteps * priceRange)
                        val labelY = when (i) {
                            0 -> gridY + 18f
                            gridSteps -> gridY - 4f
                            else -> gridY + 7f
                        }
                        drawContext.canvas.nativeCanvas.drawText("${priceAtGrid.toInt()}", plotWidth + 8f, labelY, axisTextPaint)
                    }

                    // VWAP & EMA20 overlays
                    val vwapPath = Path()
                    val emaPath = Path()
                    plottedCandles.forEachIndexed { i, candle ->
                        val x = i * candleWidth + (candleWidth / 2f)
                        val vwapY = getY(candle.vwap).coerceIn(0f, height)
                        if (i == 0) vwapPath.moveTo(x, vwapY) else vwapPath.lineTo(x, vwapY)

                        val emaVal = ema20Series.getOrElse(i) { candle.close }
                        val emaY = getY(emaVal).coerceIn(0f, height)
                        if (i == 0) emaPath.moveTo(x, emaY) else emaPath.lineTo(x, emaY)
                    }
                    drawPath(vwapPath, color = palette.accentCyan.copy(alpha = 0.85f), style = Stroke(width = 2.2f))
                    drawPath(emaPath, color = palette.goldAccent.copy(alpha = 0.85f), style = Stroke(width = 1.8f))

                    when (activeChartType) {
                        ChartType.CANDLESTICK, ChartType.HOLLOW_CANDLESTICK, ChartType.HEIKIN_ASHI -> {
                            plottedCandles.forEachIndexed { i, candle ->
                                val x = i * candleWidth + (candleWidth / 2f)
                                val isBull = candle.close >= candle.open
                                val isSelectedCandle = activeIndex == i
                                val candleColor = if (isBull) palette.bullGreen else palette.bearRed

                                val openY = getY(candle.open)
                                val closeY = getY(candle.close)
                                val highY = getY(candle.high)
                                val lowY = getY(candle.low)

                                if (isSelectedCandle) {
                                    drawRect(
                                        color = palette.accentCyan.copy(alpha = 0.14f),
                                        topLeft = Offset(i * candleWidth, 0f),
                                        size = Size(candleWidth, height)
                                    )
                                }
                                drawLine(candleColor, Offset(x, highY), Offset(x, lowY), strokeWidth = if (isSelectedCandle) 2.5f else 1.5f)
                                val topBody = min(openY, closeY)
                                val bodyHeight = max(abs(openY - closeY), 2.5f)
                                val hollow = activeChartType == ChartType.HOLLOW_CANDLESTICK && isBull
                                drawRect(
                                    color = candleColor,
                                    topLeft = Offset(x - (bodyWidth / 2f), topBody),
                                    size = Size(bodyWidth, bodyHeight),
                                    style = if (hollow) Stroke(2.0f) else Fill
                                )
                            }
                        }
                        ChartType.OHLC_BARS -> {
                            plottedCandles.forEachIndexed { i, candle ->
                                val x = i * candleWidth + (candleWidth / 2f)
                                val col = if (candle.close >= candle.open) palette.bullGreen else palette.bearRed
                                drawLine(col, Offset(x, getY(candle.high)), Offset(x, getY(candle.low)), strokeWidth = 2f)
                                drawLine(col, Offset(x - bodyWidth * 0.45f, getY(candle.open)), Offset(x, getY(candle.open)), strokeWidth = 2f)
                                drawLine(col, Offset(x, getY(candle.close)), Offset(x + bodyWidth * 0.45f, getY(candle.close)), strokeWidth = 2f)
                            }
                        }
                        ChartType.LINE, ChartType.AREA, ChartType.BASELINE -> {
                            val linePath = Path()
                            val areaPath = Path()
                            val basePrice = plottedCandles.first().close
                            plottedCandles.forEachIndexed { i, c ->
                                val x = i * candleWidth + (candleWidth / 2f)
                                val y = getY(c.close)
                                if (i == 0) {
                                    linePath.moveTo(x, y)
                                    areaPath.moveTo(x, height)
                                    areaPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    areaPath.lineTo(x, y)
                                }
                                if (i == plottedCandles.lastIndex) {
                                    areaPath.lineTo(x, height)
                                    areaPath.close()
                                }
                            }
                            if (activeChartType == ChartType.AREA) {
                                drawPath(areaPath, Brush.verticalGradient(listOf(palette.accentCyan.copy(alpha = 0.35f), Color.Transparent)))
                            } else if (activeChartType == ChartType.BASELINE) {
                                val by = getY(basePrice)
                                drawLine(palette.textMuted, Offset(0f, by), Offset(plotWidth, by), strokeWidth = 1.4f, pathEffect = dashEffect)
                                drawPath(areaPath, palette.bullGreen.copy(alpha = 0.14f))
                            }
                            drawPath(linePath, palette.accentCyan, style = Stroke(2.4f))
                        }
                    }

                    activeIndex?.let { idx ->
                        val selX = idx * candleWidth + (candleWidth / 2f)
                        val selY = getY(plottedCandles[idx].close)
                        drawLine(palette.accentCyan.copy(alpha = if (isDragging) 0.9f else 0.65f), Offset(selX, 0f), Offset(selX, height), strokeWidth = 1.5f, pathEffect = dashEffect)
                        drawLine(palette.accentCyan.copy(alpha = if (isDragging) 0.9f else 0.65f), Offset(0f, selY), Offset(plotWidth, selY), strokeWidth = 1.5f, pathEffect = dashEffect)
                        drawCircle(Color.White, 5.5f, Offset(selX, selY))
                        drawCircle(palette.accentCyan, 3.5f, Offset(selX, selY))
                    }
                }

                // Floating OHLCV Popup Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeIndex != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                    modifier = Modifier
                        .align(if ((activeIndex ?: 0) < plottedCandles.size / 2) Alignment.TopEnd else Alignment.TopStart)
                        .padding(top = 4.dp, start = 4.dp, end = if ((activeIndex ?: 0) < plottedCandles.size / 2) 52.dp else 4.dp)
                ) {
                    activeIndex?.let { idx ->
                        OhlcPopupOverlay(
                            candle = plottedCandles[idx],
                            timeText = timeFormat.format(Date(plottedCandles[idx].timestamp)),
                            isMultiDay = isMultiDayTf,
                            onDismiss = { selectedCandleIndex = null }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = palette.border.copy(alpha = 0.7f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Secondary Synchronized Volume Bar Chart Area Header
            val maxVol = remember(plottedCandles) { plottedCandles.maxOf { it.volume } }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.chart_label_volume),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val volText = if (maxVol <= 0L) {
                    stringResource(R.string.chart_index_spot_no_vol)
                } else {
                    stringResource(R.string.chart_bar_vol_format, formatVolumeLabel(summaryCandle.volume))
                }
                Text(
                    text = volText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (summaryChange >= 0) palette.bullGreen else palette.bearRed,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Synchronized Volume Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .then(dragModifier)
                    .testTag("volume_bar_chart")
            ) {
                val width = size.width
                val height = size.height
                val plotWidth = (width - rightAxisMarginPx).coerceAtLeast(1f)
                val candleWidth = plotWidth / plottedCandles.size
                val barWidth = max(candleWidth * 0.62f, 2.5f)
                val maxVolume = max(plottedCandles.maxOf { it.volume }, 1L)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                drawLine(palette.gridLine, Offset(0f, 0f), Offset(plotWidth, 0f), strokeWidth = 1f, pathEffect = dashEffect)
                drawLine(palette.gridLine, Offset(0f, height * 0.5f), Offset(plotWidth, height * 0.5f), strokeWidth = 1f, pathEffect = dashEffect)
                drawLine(palette.border, Offset(0f, height), Offset(plotWidth, height), strokeWidth = 1f)

                val topVolLabel = if (plottedCandles.maxOf { it.volume } > 0L) formatVolumeLabel(maxVolume) else "0"
                val midVolLabel = if (plottedCandles.maxOf { it.volume } > 0L) formatVolumeLabel(maxVolume / 2) else "0"
                drawContext.canvas.nativeCanvas.drawText(topVolLabel, plotWidth + 8f, 18f, axisTextPaint)
                drawContext.canvas.nativeCanvas.drawText(midVolLabel, plotWidth + 8f, height * 0.5f + 7f, axisTextPaint)

                plottedCandles.forEachIndexed { i, candle ->
                    val x = i * candleWidth + (candleWidth / 2f)
                    val isBull = candle.close >= candle.open
                    val isSelected = activeIndex == i
                    val barColor = if (isBull) palette.bullGreen else palette.bearRed

                    if (isSelected) {
                        drawRect(palette.accentCyan.copy(alpha = 0.14f), Offset(i * candleWidth, 0f), Size(candleWidth, height))
                    }
                    val volRatio = (candle.volume.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
                    val barH = if (candle.volume > 0L) max(height * volRatio, 3f) else 2f
                    drawRect(
                        color = barColor.copy(alpha = if (isSelected) 0.95f else 0.58f),
                        topLeft = Offset(x - (barWidth / 2f), height - barH),
                        size = Size(barWidth, barH)
                    )
                }

                activeIndex?.let { idx ->
                    val selX = idx * candleWidth + (candleWidth / 2f)
                    drawLine(palette.accentCyan.copy(alpha = if (isDragging) 0.9f else 0.65f), Offset(selX, 0f), Offset(selX, height), strokeWidth = 1.5f, pathEffect = dashEffect)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 46.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xAxisLabels.forEach { label ->
                    Text(text = label, fontSize = 9.sp, color = palette.textMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun formatVolumeLabel(volume: Long): String {
    return when {
        volume >= 1_000_000L -> String.format(Locale.US, "%.1fM", volume / 1_000_000.0)
        volume >= 1_000L -> String.format(Locale.US, "%.1fk", volume / 1_000.0)
        else -> "$volume"
    }
}

@Composable
private fun OhlcPopupOverlay(
    candle: Candle,
    timeText: String,
    isMultiDay: Boolean,
    onDismiss: () -> Unit
) {
    val palette = LocalChartPalette.current
    val isBull = candle.close >= candle.open
    val accentColor = if (isBull) palette.bullGreen else palette.bearRed
    val delta = candle.close - candle.open
    val deltaPct = if (candle.open > 0) (delta / candle.open) * 100.0 else 0.0

    Column(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 196.dp)
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(palette.popupBackground)
            .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("ohlc_popup_overlay")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isMultiDay) timeText else stringResource(R.string.label_ist_time_format, timeText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.goldAccent,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_dismiss_ohlc),
                    tint = palette.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        OhlcValueRow(label = stringResource(R.string.label_open), value = String.format(Locale.US, "%,.2f", candle.open), valueColor = palette.textPrimary)
        OhlcValueRow(label = stringResource(R.string.label_high), value = String.format(Locale.US, "%,.2f", candle.high), valueColor = palette.bullGreen)
        OhlcValueRow(label = stringResource(R.string.label_low), value = String.format(Locale.US, "%,.2f", candle.low), valueColor = palette.bearRed)
        OhlcValueRow(label = stringResource(R.string.label_close), value = String.format(Locale.US, "%,.2f", candle.close), valueColor = accentColor)

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(palette.surfaceHighlight)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${if (delta >= 0) "+" else ""}${String.format(Locale.US, "%.1f", delta)} (${String.format(Locale.US, "%.2f", deltaPct)}%)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(R.string.chart_vol_format, formatVolumeLabel(candle.volume)),
                fontSize = 9.sp,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun OhlcValueRow(
    label: String,
    value: String,
    valueColor: Color
) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 9.5.sp, color = palette.textSecondary, fontFamily = FontFamily.Monospace)
        Text(text = value, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
    }
}
