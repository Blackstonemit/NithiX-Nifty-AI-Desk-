package com.example.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Candle
import com.example.model.IndicatorConfig
import com.example.model.IndicatorType
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renders synchronized lower sub-chart oscillator panes (Volume, RSI, MACD, Stochastic, ADX,
 * OBV, MFI, CMF, CCI, Williams %R, ROC, ATR, BB %B) sharing exact X-axis alignment & crosshair.
 */
@Composable
fun OscillatorSubCharts(
    state: ChartWorkstationUiState,
    selectedVisibleIndex: Int?,
    paneHeight: Dp = 70.dp,
    modifier: Modifier = Modifier
) {
    val candlesAll = state.displayCandles.ifEmpty { state.candles }
    if (candlesAll.isEmpty()) return

    val activeOscillators = state.indicatorConfigs.filter { !it.type.isOverlay && it.enabled }
    if (activeOscillators.isEmpty()) return

    val visCount = state.visibleCandleCount.coerceIn(12, min(250, candlesAll.size))
    val endIdx = (candlesAll.size - state.scrollRightOffset).coerceIn(visCount, candlesAll.size)
    val startIdx = (endIdx - visCount).coerceAtLeast(0)
    val visCandles = candlesAll.subList(startIdx, endIdx)

    val palette = LocalChartPalette.current
    val density = LocalDensity.current
    val rightAxisMarginPx = remember(density) { with(density) { 54.dp.toPx() } }
    val axisPaint = remember(palette.axisLabelArgb) {
        android.graphics.Paint().apply {
            color = palette.axisLabelArgb
            textSize = 20f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        activeOscillators.take(4).forEach { cfg ->
            HorizontalDivider(color = palette.border.copy(alpha = 0.7f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))
            SingleOscillatorPane(
                cfg = cfg,
                state = state,
                visCandles = visCandles,
                startIdx = startIdx,
                selectedVisibleIndex = selectedVisibleIndex,
                paneHeight = paneHeight,
                rightAxisMarginPx = rightAxisMarginPx,
                axisPaint = axisPaint
            )
        }
    }
}

@Composable
private fun SingleOscillatorPane(
    cfg: IndicatorConfig,
    state: ChartWorkstationUiState,
    visCandles: List<Candle>,
    startIdx: Int,
    selectedVisibleIndex: Int?,
    paneHeight: Dp,
    rightAxisMarginPx: Float,
    axisPaint: android.graphics.Paint
) {
    val palette = LocalChartPalette.current
    val series = state.indicatorSeries
    val inspectLocal = selectedVisibleIndex?.coerceIn(0, visCandles.lastIndex) ?: visCandles.lastIndex
    val inspectGlobal = startIdx + inspectLocal

    val headerValueText = when (cfg.type) {
        IndicatorType.VOLUME -> {
            val v = visCandles[inspectLocal].volume
            val rvol = String.format(Locale.US, "%.2fx", state.volumeProfile.rvolRatio)
            if (v > 0L) {
                stringResource(R.string.osc_vol_rvol_format, formatCompactVol(v), rvol)
            } else {
                stringResource(R.string.chart_index_spot_no_vol)
            }
        }
        IndicatorType.RSI -> String.format(Locale.US, "%.1f", series.rsiSeries.getOrElse(inspectGlobal) { 50.0 })
        IndicatorType.MACD -> String.format(Locale.US, "H:%.2f", series.macdSeries.third.getOrElse(inspectGlobal) { 0.0 })
        IndicatorType.STOCHASTIC -> String.format(
            Locale.US,
            "K:%.1f D:%.1f",
            series.stochSeries.first.getOrElse(inspectGlobal) { 50.0 },
            series.stochSeries.second.getOrElse(inspectGlobal) { 50.0 }
        )
        IndicatorType.ADX -> String.format(Locale.US, "%.1f", series.adxSeries.first.getOrElse(inspectGlobal) { 20.0 })
        IndicatorType.CCI -> String.format(Locale.US, "%.1f", series.cciSeries.getOrElse(inspectGlobal) { 0.0 })
        IndicatorType.WILLIAMS_R -> String.format(Locale.US, "%.1f", series.williamsRSeries.getOrElse(inspectGlobal) { -50.0 })
        IndicatorType.ROC -> String.format(Locale.US, "%.2f%%", series.rocSeries.getOrElse(inspectGlobal) { 0.0 })
        IndicatorType.MFI -> String.format(Locale.US, "%.1f", series.mfiSeries.getOrElse(inspectGlobal) { 50.0 })
        IndicatorType.ATR -> String.format(Locale.US, "%.2f", series.atrSeries.getOrElse(inspectGlobal) { 0.0 })
        IndicatorType.BB_BANDWIDTH -> String.format(Locale.US, "%B: %.0f%%", series.bbBandwidthPctB.second.getOrElse(inspectGlobal) { 50.0 })
        IndicatorType.OBV -> formatCompactVol(series.obvSeries.getOrElse(inspectGlobal) { 0.0 }.toLong())
        IndicatorType.CMF -> String.format(Locale.US, "%.2f", series.cmfSeries.getOrElse(inspectGlobal) { 0.0 })
        else -> ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = cfg.displayTag(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            style = TabularNumericStyle
        )
        Text(
            text = headerValueText,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(cfg.colorHex),
            style = TabularNumericStyle
        )
    }

    val canvasTag = if (cfg.type == IndicatorType.VOLUME) "volume_bar_chart" else "oscillator_${cfg.type.name.lowercase()}"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(paneHeight)
            .background(palette.canvasBackground)
            .testTag(canvasTag)
    ) {
        val w = size.width
        val h = size.height
        val plotW = (w - rightAxisMarginPx).coerceAtLeast(1f)
        val colW = plotW / visCandles.size
        val barW = max(colW * 0.62f, 2.2f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)

        drawLine(palette.gridLine, Offset(0f, h * 0.5f), Offset(plotW, h * 0.5f), 1f, pathEffect = dash)
        drawLine(palette.border.copy(alpha = 0.6f), Offset(0f, h), Offset(plotW, h), 1f)

        when (cfg.type) {
            IndicatorType.VOLUME -> {
                val maxV = max(visCandles.maxOf { it.volume }, 1L)
                drawContext.canvas.nativeCanvas.drawText(
                    if (visCandles.maxOf { it.volume } > 0L) formatCompactVol(maxV) else "0",
                    plotW + 6f,
                    18f,
                    axisPaint
                )
                visCandles.forEachIndexed { localIdx, c ->
                    val x = localIdx * colW + colW / 2f
                    val col = if (c.close >= c.open) palette.bullGreen else palette.bearRed
                    val ratio = (c.volume.toFloat() / maxV.toFloat()).coerceIn(0f, 1f)
                    val bh = if (c.volume > 0L) max(h * ratio, 3f) else 2f
                    drawRect(
                        color = col.copy(alpha = if (selectedVisibleIndex == localIdx) 0.95f else 0.58f),
                        topLeft = Offset(x - barW / 2f, h - bh),
                        size = Size(barW, bh)
                    )
                }
                if (series.volumeMaSeries.isNotEmpty() && visCandles.maxOf { it.volume } > 0L) {
                    val maPath = Path()
                    visCandles.forEachIndexed { localIdx, _ ->
                        val v = series.volumeMaSeries.getOrElse(startIdx + localIdx) { 0.0 }
                        val y = h - (h * (v / maxV).toFloat().coerceIn(0f, 1f))
                        val x = localIdx * colW + colW / 2f
                        if (localIdx == 0) maPath.moveTo(x, y) else maPath.lineTo(x, y)
                    }
                    drawPath(maPath, palette.goldAccent, style = Stroke(1.6f))
                }
            }
            IndicatorType.MACD -> {
                val (line, sig, hist) = series.macdSeries
                val visHist = visCandles.indices.map { hist.getOrElse(startIdx + it) { 0.0 } }
                val visLine = visCandles.indices.map { line.getOrElse(startIdx + it) { 0.0 } }
                val visSig = visCandles.indices.map { sig.getOrElse(startIdx + it) { 0.0 } }
                val maxAbs = max(
                    max(visHist.maxOfOrNull { abs(it) } ?: 1.0, visLine.maxOfOrNull { abs(it) } ?: 1.0),
                    0.5
                )
                fun yFor(v: Double) = ((0.5 - (v / (2.0 * maxAbs))) * h).toFloat().coerceIn(2f, h - 2f)
                val zeroY = h * 0.5f
                visHist.forEachIndexed { localIdx, v ->
                    val vy = yFor(v)
                    val x = localIdx * colW + colW / 2f
                    val col = if (v >= 0.0) palette.bullGreen.copy(alpha = 0.6f) else palette.bearRed.copy(alpha = 0.6f)
                    drawRect(col, Offset(x - barW / 2f, min(zeroY, vy)), Size(barW, max(abs(vy - zeroY), 2f)))
                }
                drawSeriesLine(visLine, ::yFor, colW, palette.accentCyan)
                drawSeriesLine(visSig, ::yFor, colW, palette.goldAccent)
                drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", maxAbs), plotW + 6f, 16f, axisPaint)
            }
            IndicatorType.RSI, IndicatorType.MFI, IndicatorType.STOCHASTIC -> {
                val primary = when (cfg.type) {
                    IndicatorType.RSI -> series.rsiSeries
                    IndicatorType.MFI -> series.mfiSeries
                    else -> series.stochSeries.first
                }
                val secondary = if (cfg.type == IndicatorType.STOCHASTIC) series.stochSeries.second else emptyList()
                val ob = if (cfg.type == IndicatorType.RSI) 70.0 else 80.0
                val os = if (cfg.type == IndicatorType.RSI) 30.0 else 20.0
                fun yFor(v: Double) = (h - (v.coerceIn(0.0, 100.0) / 100.0) * h).toFloat()
                drawLine(palette.bearRed.copy(alpha = 0.4f), Offset(0f, yFor(ob)), Offset(plotW, yFor(ob)), strokeWidth = 1f, pathEffect = dash)
                drawLine(palette.bullGreen.copy(alpha = 0.4f), Offset(0f, yFor(os)), Offset(plotW, yFor(os)), strokeWidth = 1f, pathEffect = dash)
                val visP = visCandles.indices.map { primary.getOrElse(startIdx + it) { 50.0 } }
                drawSeriesLine(visP, ::yFor, colW, Color(cfg.colorHex))
                if (secondary.isNotEmpty()) {
                    val visS = visCandles.indices.map { secondary.getOrElse(startIdx + it) { 50.0 } }
                    drawSeriesLine(visS, ::yFor, colW, palette.bearRed)
                }
                drawContext.canvas.nativeCanvas.drawText("${ob.toInt()}", plotW + 6f, yFor(ob) + 6f, axisPaint)
                drawContext.canvas.nativeCanvas.drawText("${os.toInt()}", plotW + 6f, yFor(os) + 6f, axisPaint)
            }
            IndicatorType.ADX -> {
                val (adx, dp, dm) = series.adxSeries
                val visAdx = visCandles.indices.map { adx.getOrElse(startIdx + it) { 20.0 } }
                val visDp = visCandles.indices.map { dp.getOrElse(startIdx + it) { 20.0 } }
                val visDm = visCandles.indices.map { dm.getOrElse(startIdx + it) { 20.0 } }
                fun yFor(v: Double) = (h - (v.coerceIn(0.0, 65.0) / 65.0) * h).toFloat()
                drawSeriesLine(visAdx, ::yFor, colW, palette.goldAccent)
                drawSeriesLine(visDp, ::yFor, colW, palette.bullGreen)
                drawSeriesLine(visDm, ::yFor, colW, palette.bearRed)
                drawContext.canvas.nativeCanvas.drawText("25", plotW + 6f, yFor(25.0) + 5f, axisPaint)
            }
            else -> {
                val rawSeries = when (cfg.type) {
                    IndicatorType.CCI -> series.cciSeries
                    IndicatorType.WILLIAMS_R -> series.williamsRSeries
                    IndicatorType.ROC -> series.rocSeries
                    IndicatorType.ATR -> series.atrSeries
                    IndicatorType.BB_BANDWIDTH -> series.bbBandwidthPctB.second
                    IndicatorType.OBV -> series.obvSeries
                    IndicatorType.CMF -> series.cmfSeries
                    else -> emptyList()
                }
                val vis = visCandles.indices.map { rawSeries.getOrElse(startIdx + it) { 0.0 } }
                val minV = vis.minOrNull() ?: 0.0
                val maxV = vis.maxOrNull() ?: 1.0
                val span = max(maxV - minV, 1e-4)
                fun yFor(v: Double) = (h - ((v - minV) / span) * h).toFloat().coerceIn(2f, h - 2f)
                drawSeriesLine(vis, ::yFor, colW, Color(cfg.colorHex))
                drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", maxV), plotW + 6f, 16f, axisPaint)
            }
        }

        selectedVisibleIndex?.let { localIdx ->
            val selX = localIdx * colW + colW / 2f
            drawLine(palette.accentCyan.copy(alpha = 0.7f), Offset(selX, 0f), Offset(selX, h), 1.4f, pathEffect = dash)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeriesLine(
    values: List<Double>,
    yFor: (Double) -> Float,
    colW: Float,
    color: Color
) {
    if (values.isEmpty()) return
    val p = Path()
    values.forEachIndexed { idx, v ->
        val x = idx * colW + colW / 2f
        val y = yFor(v)
        if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, color = color, style = Stroke(width = 2.0f))
}

private fun formatCompactVol(volume: Long): String {
    val absV = abs(volume)
    return when {
        absV >= 1_000_000L -> String.format(Locale.US, "%.1fM", volume / 1_000_000.0)
        absV >= 1_000L -> String.format(Locale.US, "%.1fk", volume / 1_000.0)
        else -> "$volume"
    }
}
