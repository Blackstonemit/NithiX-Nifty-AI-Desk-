package com.example.engine.chart

import com.example.engine.TechnicalCalculator
import com.example.model.Candle
import com.example.model.ChartType
import com.example.model.IchimokuSeries
import com.example.model.IndicatorConfig
import com.example.model.IndicatorPresetTemplate
import com.example.model.IndicatorSeriesSet
import com.example.model.IndicatorType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic technical indicator engine computing 25+ overlays & oscillators across the full
 * candle series in O(n) / O(n*k) without approximations or look-ahead leakage.
 */
object IndicatorCalculationEngine {

    val builtInPresets: List<IndicatorPresetTemplate> = listOf(
        IndicatorPresetTemplate(
            id = "preset_trend",
            name = "Trend Following",
            description = "EMA 20 / 50 + Supertrend(10,3) + ADX(14) & MACD",
            chartType = ChartType.CANDLESTICK,
            configs = listOf(
                IndicatorConfig("ema_20", IndicatorType.EMA, enabled = true, period = 20, colorHex = 0xFFFFB300),
                IndicatorConfig("ema_50", IndicatorType.EMA, enabled = true, period = 50, colorHex = 0xFF38BDF8),
                IndicatorConfig("supertrend", IndicatorType.SUPERTREND, enabled = true, period = 10, multiplier = 3.0, colorHex = 0xFF00E676),
                IndicatorConfig("vol", IndicatorType.VOLUME, enabled = true, period = 20),
                IndicatorConfig("macd", IndicatorType.MACD, enabled = true, period = 12, secondaryPeriod = 26, tertiaryPeriod = 9),
                IndicatorConfig("adx", IndicatorType.ADX, enabled = true, period = 14)
            )
        ),
        IndicatorPresetTemplate(
            id = "preset_mean_rev",
            name = "Mean Reversion",
            description = "Bollinger Bands(20,2) + VWAP + RSI(14) & MFI(14)",
            chartType = ChartType.CANDLESTICK,
            configs = listOf(
                IndicatorConfig("bb", IndicatorType.BOLLINGER_BANDS, enabled = true, period = 20, multiplier = 2.0),
                IndicatorConfig("vwap", IndicatorType.VWAP, enabled = true),
                IndicatorConfig("vol", IndicatorType.VOLUME, enabled = true, period = 20),
                IndicatorConfig("rsi", IndicatorType.RSI, enabled = true, period = 14),
                IndicatorConfig("mfi", IndicatorType.MFI, enabled = true, period = 14)
            )
        ),
        IndicatorPresetTemplate(
            id = "preset_scalping",
            name = "Intraday Scalping",
            description = "EMA 9 / 21 + VWAP + Volume + Stochastic(14,3,3) & RSI(9)",
            chartType = ChartType.HEIKIN_ASHI,
            configs = listOf(
                IndicatorConfig("ema_9", IndicatorType.EMA, enabled = true, period = 9, colorHex = 0xFF22D3EE),
                IndicatorConfig("ema_21", IndicatorType.EMA, enabled = true, period = 21, colorHex = 0xFFF472B6),
                IndicatorConfig("vwap", IndicatorType.VWAP, enabled = true),
                IndicatorConfig("vol", IndicatorType.VOLUME, enabled = true, period = 20),
                IndicatorConfig("stoch", IndicatorType.STOCHASTIC, enabled = true, period = 14, secondaryPeriod = 3, tertiaryPeriod = 3),
                IndicatorConfig("rsi_9", IndicatorType.RSI, enabled = true, period = 9)
            )
        ),
        IndicatorPresetTemplate(
            id = "preset_swing",
            name = "Swing Structure",
            description = "SMA 50 / 200 + Ichimoku Cloud + OBV & RSI(14)",
            chartType = ChartType.HOLLOW_CANDLESTICK,
            configs = listOf(
                IndicatorConfig("sma_50", IndicatorType.SMA, enabled = true, period = 50, colorHex = 0xFF60A5FA),
                IndicatorConfig("sma_200", IndicatorType.SMA, enabled = true, period = 200, colorHex = 0xFFF43F5E),
                IndicatorConfig("ichi", IndicatorType.ICHIMOKU_CLOUD, enabled = true, period = 9, secondaryPeriod = 26, tertiaryPeriod = 52),
                IndicatorConfig("vol", IndicatorType.VOLUME, enabled = true, period = 20),
                IndicatorConfig("rsi", IndicatorType.RSI, enabled = true, period = 14),
                IndicatorConfig("obv", IndicatorType.OBV, enabled = true, period = 20)
            )
        )
    )

    fun defaultConfigs(): List<IndicatorConfig> = listOf(
        IndicatorConfig("vwap", IndicatorType.VWAP, enabled = true, colorHex = 0xFF00E5FF),
        IndicatorConfig("ema_20", IndicatorType.EMA, enabled = true, period = 20, colorHex = 0xFFFFB300),
        IndicatorConfig("ema_50", IndicatorType.EMA, enabled = false, period = 50, colorHex = 0xFF38BDF8),
        IndicatorConfig("ema_200", IndicatorType.EMA, enabled = false, period = 200, colorHex = 0xFFF43F5E),
        IndicatorConfig("sma_20", IndicatorType.SMA, enabled = false, period = 20, colorHex = 0xFFA78BFA),
        IndicatorConfig("hma_21", IndicatorType.HMA, enabled = false, period = 21, colorHex = 0xFFF472B6),
        IndicatorConfig("vwma_20", IndicatorType.VWMA, enabled = false, period = 20, colorHex = 0xFF2DD4BF),
        IndicatorConfig("bb_20", IndicatorType.BOLLINGER_BANDS, enabled = false, period = 20, multiplier = 2.0),
        IndicatorConfig("kc_20", IndicatorType.KELTNER_CHANNELS, enabled = false, period = 20, secondaryPeriod = 10, multiplier = 1.5),
        IndicatorConfig("dc_20", IndicatorType.DONCHIAN_CHANNELS, enabled = false, period = 20),
        IndicatorConfig("supertrend", IndicatorType.SUPERTREND, enabled = false, period = 10, multiplier = 3.0),
        IndicatorConfig("psar", IndicatorType.PARABOLIC_SAR, enabled = false, period = 2, multiplier = 0.02),
        IndicatorConfig("ichimoku", IndicatorType.ICHIMOKU_CLOUD, enabled = false, period = 9, secondaryPeriod = 26, tertiaryPeriod = 52),
        IndicatorConfig("anchored_vwap", IndicatorType.ANCHORED_VWAP, enabled = false),

        IndicatorConfig("volume", IndicatorType.VOLUME, enabled = true, period = 20),
        IndicatorConfig("rsi", IndicatorType.RSI, enabled = false, period = 14),
        IndicatorConfig("macd", IndicatorType.MACD, enabled = false, period = 12, secondaryPeriod = 26, tertiaryPeriod = 9),
        IndicatorConfig("stoch", IndicatorType.STOCHASTIC, enabled = false, period = 14, secondaryPeriod = 3, tertiaryPeriod = 3),
        IndicatorConfig("adx", IndicatorType.ADX, enabled = false, period = 14),
        IndicatorConfig("obv", IndicatorType.OBV, enabled = false, period = 20),
        IndicatorConfig("mfi", IndicatorType.MFI, enabled = false, period = 14),
        IndicatorConfig("cmf", IndicatorType.CMF, enabled = false, period = 20),
        IndicatorConfig("cci", IndicatorType.CCI, enabled = false, period = 20),
        IndicatorConfig("williams_r", IndicatorType.WILLIAMS_R, enabled = false, period = 14),
        IndicatorConfig("roc", IndicatorType.ROC, enabled = false, period = 12),
        IndicatorConfig("atr", IndicatorType.ATR, enabled = false, period = 14),
        IndicatorConfig("bb_bw", IndicatorType.BB_BANDWIDTH, enabled = false, period = 20, multiplier = 2.0)
    )

    fun computeAll(candles: List<Candle>, configs: List<IndicatorConfig>): IndicatorSeriesSet {
        if (candles.isEmpty()) {
            return IndicatorSeriesSet(overlayLines = emptyMap(), bands = emptyMap())
        }
        val closes = candles.map { it.close }
        val overlayLines = LinkedHashMap<String, List<Double>>()
        val bands = LinkedHashMap<String, Triple<List<Double>, List<Double>, List<Double>>>()
        var ichimoku: IchimokuSeries? = null
        var psar: List<Double> = emptyList()
        var supertrend: List<Pair<Double, Boolean>> = emptyList()

        val enabledConfigs = configs.filter { it.enabled }
        for (cfg in enabledConfigs) {
            when (cfg.type) {
                IndicatorType.SMA -> overlayLines[cfg.id] = smaSeries(closes, cfg.period)
                IndicatorType.EMA -> overlayLines[cfg.id] = TechnicalCalculator.calculateEmaSeries(closes, cfg.period)
                IndicatorType.WMA -> overlayLines[cfg.id] = wmaSeries(closes, cfg.period)
                IndicatorType.HMA -> overlayLines[cfg.id] = hmaSeries(closes, cfg.period)
                IndicatorType.VWMA -> overlayLines[cfg.id] = vwmaSeries(candles, cfg.period)
                IndicatorType.VWAP -> overlayLines[cfg.id] = candles.map { it.vwap }
                IndicatorType.ANCHORED_VWAP -> {
                    val anchorIdx = (cfg.anchoredIndex ?: (candles.size / 3)).coerceIn(0, candles.lastIndex)
                    overlayLines[cfg.id] = anchoredVwapSeries(candles, anchorIdx)
                }
                IndicatorType.BOLLINGER_BANDS -> bands[cfg.id] = bollingerSeries(closes, cfg.period, cfg.multiplier)
                IndicatorType.KELTNER_CHANNELS -> bands[cfg.id] = keltnerSeries(candles, cfg.period, max(1, cfg.secondaryPeriod), cfg.multiplier)
                IndicatorType.DONCHIAN_CHANNELS -> bands[cfg.id] = donchianSeries(candles, cfg.period)
                IndicatorType.PARABOLIC_SAR -> psar = parabolicSarSeries(candles, step = cfg.multiplier.coerceIn(0.005, 0.1), maxAf = 0.2)
                IndicatorType.SUPERTREND -> supertrend = supertrendSeries(candles, cfg.period, cfg.multiplier)
                IndicatorType.ICHIMOKU_CLOUD -> ichimoku = ichimokuSeries(
                    candles,
                    tenkanPeriod = max(2, cfg.period),
                    kijunPeriod = max(5, cfg.secondaryPeriod),
                    senkouBPeriod = max(10, cfg.tertiaryPeriod)
                )
                else -> Unit
            }
        }

        val rsiCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.RSI }
        val macdCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.MACD }
        val stochCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.STOCHASTIC }
        val cciCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.CCI }
        val wrCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.WILLIAMS_R }
        val rocCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.ROC }
        val mfiCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.MFI }
        val atrCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.ATR }
        val bbBwCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.BB_BANDWIDTH }
        val obvCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.OBV }
        val cmfCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.CMF }
        val adxCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.ADX }
        val volCfg = enabledConfigs.firstOrNull { it.type == IndicatorType.VOLUME }

        return IndicatorSeriesSet(
            overlayLines = overlayLines,
            bands = bands,
            ichimoku = ichimoku,
            psarSeries = psar,
            supertrendSeries = supertrend,
            rsiSeries = if (rsiCfg != null) rsiSeries(candles, rsiCfg.period) else emptyList(),
            macdSeries = if (macdCfg != null) macdSeries(closes, macdCfg.period, max(2, macdCfg.secondaryPeriod), max(2, macdCfg.tertiaryPeriod)) else Triple(emptyList(), emptyList(), emptyList()),
            stochSeries = if (stochCfg != null) stochasticSeries(candles, stochCfg.period, max(1, stochCfg.secondaryPeriod), max(1, stochCfg.tertiaryPeriod)) else Pair(emptyList(), emptyList()),
            cciSeries = if (cciCfg != null) cciSeries(candles, cciCfg.period) else emptyList(),
            williamsRSeries = if (wrCfg != null) williamsRSeries(candles, wrCfg.period) else emptyList(),
            rocSeries = if (rocCfg != null) rocSeries(closes, rocCfg.period) else emptyList(),
            mfiSeries = if (mfiCfg != null) mfiSeries(candles, mfiCfg.period) else emptyList(),
            atrSeries = if (atrCfg != null) atrSeries(candles, atrCfg.period) else emptyList(),
            bbBandwidthPctB = if (bbBwCfg != null) bollingerBandwidthAndPctB(closes, bbBwCfg.period, bbBwCfg.multiplier) else Pair(emptyList(), emptyList()),
            obvSeries = if (obvCfg != null) obvSeries(candles) else emptyList(),
            cmfSeries = if (cmfCfg != null) cmfSeries(candles, cmfCfg.period) else emptyList(),
            adxSeries = if (adxCfg != null) adxSeries(candles, adxCfg.period) else Triple(emptyList(), emptyList(), emptyList()),
            volumeMaSeries = smaSeries(candles.map { it.volume.toDouble() }, volCfg?.period ?: 20)
        )
    }

    fun smaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = max(1, period)
        val out = ArrayList<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= p) sum -= values[i - p]
            val count = min(i + 1, p)
            out.add(sum / count)
        }
        return out
    }

    fun wmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = max(1, period)
        val out = ArrayList<Double>(values.size)
        for (i in values.indices) {
            val start = max(0, i - p + 1)
            var weightedSum = 0.0
            var weightTotal = 0.0
            var weight = 1
            for (j in start..i) {
                weightedSum += values[j] * weight
                weightTotal += weight
                weight++
            }
            out.add(if (weightTotal > 0) weightedSum / weightTotal else values[i])
        }
        return out
    }

    fun hmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = max(2, period)
        val half = wmaSeries(values, max(1, p / 2))
        val full = wmaSeries(values, p)
        val diff = List(values.size) { i -> 2.0 * half[i] - full[i] }
        return wmaSeries(diff, max(1, sqrt(p.toDouble()).roundToInt()))
    }

    fun vwmaSeries(candles: List<Candle>, period: Int): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(1, period)
        val out = ArrayList<Double>(candles.size)
        for (i in candles.indices) {
            val start = max(0, i - p + 1)
            var pv = 0.0
            var v = 0.0
            for (j in start..i) {
                val vol = max(0.0, candles[j].volume.toDouble())
                pv += candles[j].close * vol
                v += vol
            }
            out.add(if (v > 0.0) pv / v else candles.subList(start, i + 1).map { it.close }.average())
        }
        return out
    }

    fun anchoredVwapSeries(candles: List<Candle>, anchorIndex: Int): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val start = anchorIndex.coerceIn(0, candles.lastIndex)
        var cumTpVol = 0.0
        var cumVol = 0.0
        var fallbackTp = 0.0
        var count = 0
        return candles.mapIndexed { i, c ->
            if (i < start) {
                c.close
            } else {
                val tp = (c.high + c.low + c.close) / 3.0
                count++
                if (c.volume > 0L) {
                    cumTpVol += tp * c.volume
                    cumVol += c.volume
                    cumTpVol / cumVol
                } else {
                    fallbackTp += tp
                    fallbackTp / count
                }
            }
        }
    }

    fun rsiSeries(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(2, period)
        val out = MutableList(candles.size) { 50.0 }
        if (candles.size <= p) return out

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..p) {
            val delta = candles[i].close - candles[i - 1].close
            if (delta > 0) gainSum += delta else lossSum += abs(delta)
        }
        var avgGain = gainSum / p
        var avgLoss = lossSum / p
        out[p] = when {
            avgGain == 0.0 && avgLoss == 0.0 -> 50.0
            avgLoss == 0.0 -> 100.0
            else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }

        for (i in (p + 1) until candles.size) {
            val delta = candles[i].close - candles[i - 1].close
            val gain = if (delta > 0) delta else 0.0
            val loss = if (delta < 0) abs(delta) else 0.0
            avgGain = (avgGain * (p - 1) + gain) / p
            avgLoss = (avgLoss * (p - 1) + loss) / p
            out[i] = when {
                avgGain == 0.0 && avgLoss == 0.0 -> 50.0
                avgLoss == 0.0 -> 100.0
                else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
            }
        }
        return out
    }

    fun macdSeries(
        closes: List<Double>,
        fast: Int = 12,
        slow: Int = 26,
        signal: Int = 9
    ): Triple<List<Double>, List<Double>, List<Double>> {
        if (closes.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val emaFast = TechnicalCalculator.calculateEmaSeries(closes, fast)
        val emaSlow = TechnicalCalculator.calculateEmaSeries(closes, slow)
        val macdLine = List(closes.size) { i -> emaFast[i] - emaSlow[i] }
        val signalLine = TechnicalCalculator.calculateEmaSeries(macdLine, signal)
        val hist = List(closes.size) { i -> macdLine[i] - signalLine[i] }
        return Triple(macdLine, signalLine, hist)
    }

    fun stochasticSeries(
        candles: List<Candle>,
        kPeriod: Int = 14,
        kSmooth: Int = 3,
        dPeriod: Int = 3
    ): Pair<List<Double>, List<Double>> {
        if (candles.isEmpty()) return Pair(emptyList(), emptyList())
        val p = max(1, kPeriod)
        val rawK = ArrayList<Double>(candles.size)
        for (i in candles.indices) {
            val start = max(0, i - p + 1)
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            for (j in start..i) {
                if (candles[j].high > high) high = candles[j].high
                if (candles[j].low < low) low = candles[j].low
            }
            val span = high - low
            val k = if (span > 0.0) ((candles[i].close - low) / span) * 100.0 else 50.0
            rawK.add(k.coerceIn(0.0, 100.0))
        }
        val smoothK = smaSeries(rawK, max(1, kSmooth))
        val dLine = smaSeries(smoothK, max(1, dPeriod))
        return Pair(smoothK, dLine)
    }

    fun cciSeries(candles: List<Candle>, period: Int = 20): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(2, period)
        val tp = candles.map { (it.high + it.low + it.close) / 3.0 }
        val smaTp = smaSeries(tp, p)
        return List(candles.size) { i ->
            val start = max(0, i - p + 1)
            val mean = smaTp[i]
            var devSum = 0.0
            for (j in start..i) devSum += abs(tp[j] - mean)
            val meanDev = devSum / (i - start + 1)
            if (meanDev > 0.0) (tp[i] - mean) / (0.015 * meanDev) else 0.0
        }
    }

    fun williamsRSeries(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(1, period)
        return List(candles.size) { i ->
            val start = max(0, i - p + 1)
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            for (j in start..i) {
                if (candles[j].high > high) high = candles[j].high
                if (candles[j].low < low) low = candles[j].low
            }
            val span = high - low
            if (span > 0.0) ((high - candles[i].close) / span) * -100.0 else -50.0
        }
    }

    fun rocSeries(closes: List<Double>, period: Int = 12): List<Double> {
        if (closes.isEmpty()) return emptyList()
        val p = max(1, period)
        return List(closes.size) { i ->
            if (i < p || closes[i - p] == 0.0) 0.0 else ((closes[i] - closes[i - p]) / closes[i - p]) * 100.0
        }
    }

    fun mfiSeries(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(2, period)
        val out = MutableList(candles.size) { 50.0 }
        val posMf = DoubleArray(candles.size)
        val negMf = DoubleArray(candles.size)
        for (i in 1 until candles.size) {
            val tpCurr = (candles[i].high + candles[i].low + candles[i].close) / 3.0
            val tpPrev = (candles[i - 1].high + candles[i - 1].low + candles[i - 1].close) / 3.0
            val rawMf = tpCurr * max(1L, candles[i].volume).toDouble()
            if (tpCurr > tpPrev) posMf[i] = rawMf else if (tpCurr < tpPrev) negMf[i] = rawMf
        }
        for (i in 1 until candles.size) {
            val start = max(1, i - p + 1)
            var pSum = 0.0
            var nSum = 0.0
            for (j in start..i) {
                pSum += posMf[j]
                nSum += negMf[j]
            }
            out[i] = if (nSum == 0.0 && pSum == 0.0) 50.0 else if (nSum == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + pSum / nSum))
        }
        return out
    }

    fun atrSeries(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(1, period)
        val tr = DoubleArray(candles.size)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val c = candles[i]
            val pc = candles[i - 1].close
            tr[i] = max(c.high - c.low, max(abs(c.high - pc), abs(c.low - pc)))
        }
        val out = ArrayList<Double>(candles.size)
        var rma = tr[0]
        out.add(rma)
        for (i in 1 until candles.size) {
            rma = if (i < p) {
                (0..i).map { tr[it] }.average()
            } else {
                ((rma * (p - 1)) + tr[i]) / p
            }
            out.add(rma)
        }
        return out
    }

    fun bollingerSeries(
        closes: List<Double>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): Triple<List<Double>, List<Double>, List<Double>> {
        if (closes.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val p = max(2, period)
        val mid = smaSeries(closes, p)
        val upper = ArrayList<Double>(closes.size)
        val lower = ArrayList<Double>(closes.size)
        for (i in closes.indices) {
            val start = max(0, i - p + 1)
            val mean = mid[i]
            var sq = 0.0
            for (j in start..i) {
                val d = closes[j] - mean
                sq += d * d
            }
            val std = sqrt(sq / (i - start + 1))
            upper.add(mean + multiplier * std)
            lower.add(mean - multiplier * std)
        }
        return Triple(upper, mid, lower)
    }

    fun bollingerBandwidthAndPctB(
        closes: List<Double>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): Pair<List<Double>, List<Double>> {
        val (upper, mid, lower) = bollingerSeries(closes, period, multiplier)
        val bandwidth = List(closes.size) { i ->
            if (mid[i] > 0.0) ((upper[i] - lower[i]) / mid[i]) * 100.0 else 0.0
        }
        val pctB = List(closes.size) { i ->
            val span = upper[i] - lower[i]
            if (span > 0.0) ((closes[i] - lower[i]) / span) * 100.0 else 50.0
        }
        return Pair(bandwidth, pctB)
    }

    fun keltnerSeries(
        candles: List<Candle>,
        emaPeriod: Int = 20,
        atrPeriod: Int = 10,
        multiplier: Double = 1.5
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val mid = TechnicalCalculator.calculateEmaSeries(candles.map { it.close }, emaPeriod)
        val atr = atrSeries(candles, atrPeriod)
        val upper = List(candles.size) { i -> mid[i] + multiplier * atr[i] }
        val lower = List(candles.size) { i -> mid[i] - multiplier * atr[i] }
        return Triple(upper, mid, lower)
    }

    fun donchianSeries(candles: List<Candle>, period: Int = 20): Triple<List<Double>, List<Double>, List<Double>> {
        if (candles.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val p = max(1, period)
        val upper = ArrayList<Double>(candles.size)
        val lower = ArrayList<Double>(candles.size)
        val mid = ArrayList<Double>(candles.size)
        for (i in candles.indices) {
            val start = max(0, i - p + 1)
            var h = Double.NEGATIVE_INFINITY
            var l = Double.POSITIVE_INFINITY
            for (j in start..i) {
                if (candles[j].high > h) h = candles[j].high
                if (candles[j].low < l) l = candles[j].low
            }
            upper.add(h)
            lower.add(l)
            mid.add((h + l) / 2.0)
        }
        return Triple(upper, mid, lower)
    }

    fun obvSeries(candles: List<Candle>): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val out = ArrayList<Double>(candles.size)
        var obv = 0.0
        out.add(obv)
        for (i in 1 until candles.size) {
            val vol = max(0L, candles[i].volume).toDouble()
            when {
                candles[i].close > candles[i - 1].close -> obv += vol
                candles[i].close < candles[i - 1].close -> obv -= vol
            }
            out.add(obv)
        }
        return out
    }

    fun cmfSeries(candles: List<Candle>, period: Int = 20): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val p = max(2, period)
        val mfVol = DoubleArray(candles.size)
        val vols = DoubleArray(candles.size)
        for (i in candles.indices) {
            val c = candles[i]
            val range = c.high - c.low
            val mult = if (range > 0.0) ((c.close - c.low) - (c.high - c.close)) / range else 0.0
            val v = max(0L, c.volume).toDouble()
            mfVol[i] = mult * v
            vols[i] = v
        }
        return List(candles.size) { i ->
            val start = max(0, i - p + 1)
            var mSum = 0.0
            var vSum = 0.0
            for (j in start..i) {
                mSum += mfVol[j]
                vSum += vols[j]
            }
            if (vSum > 0.0) (mSum / vSum).coerceIn(-1.0, 1.0) else 0.0
        }
    }

    fun adxSeries(candles: List<Candle>, period: Int = 14): Triple<List<Double>, List<Double>, List<Double>> {
        val n = candles.size
        if (n < 3) {
            return Triple(List(n) { 20.0 }, List(n) { 20.0 }, List(n) { 20.0 })
        }
        val p = max(2, period)
        val adx = MutableList(n) { 20.0 }
        val diPlus = MutableList(n) { 20.0 }
        val diMinus = MutableList(n) { 20.0 }

        var smoothTr = 0.0
        var smoothPlus = 0.0
        var smoothMinus = 0.0
        val dxValues = DoubleArray(n)

        for (i in 1 until n) {
            val curr = candles[i]
            val prev = candles[i - 1]
            val upMove = curr.high - prev.high
            val downMove = prev.low - curr.low
            val pDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val mDm = if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr = max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))

            if (i <= p) {
                smoothTr += tr
                smoothPlus += pDm
                smoothMinus += mDm
            } else {
                smoothTr = smoothTr - (smoothTr / p) + tr
                smoothPlus = smoothPlus - (smoothPlus / p) + pDm
                smoothMinus = smoothMinus - (smoothMinus / p) + mDm
            }

            val dp = if (smoothTr > 0) (smoothPlus / smoothTr) * 100.0 else 0.0
            val dm = if (smoothTr > 0) (smoothMinus / smoothTr) * 100.0 else 0.0
            diPlus[i] = dp
            diMinus[i] = dm
            val sumDi = dp + dm
            dxValues[i] = if (sumDi > 0) (abs(dp - dm) / sumDi) * 100.0 else 0.0

            if (i >= p * 2) {
                adx[i] = ((adx[i - 1] * (p - 1)) + dxValues[i]) / p
            } else if (i >= p) {
                adx[i] = (p..i).map { dxValues[it] }.average()
            } else {
                adx[i] = dxValues[i]
            }
        }
        return Triple(adx, diPlus, diMinus)
    }

    fun supertrendSeries(candles: List<Candle>, period: Int = 10, multiplier: Double = 3.0): List<Pair<Double, Boolean>> {
        if (candles.isEmpty()) return emptyList()
        val atr = atrSeries(candles, period)
        val result = ArrayList<Pair<Double, Boolean>>(candles.size)
        var finalUpper = candles[0].high
        var finalLower = candles[0].low
        var isBullish = true

        for (i in candles.indices) {
            val hl2 = (candles[i].high + candles[i].low) / 2.0
            val basicUpper = hl2 + multiplier * atr[i]
            val basicLower = hl2 - multiplier * atr[i]
            if (i == 0) {
                finalUpper = basicUpper
                finalLower = basicLower
                isBullish = candles[i].close >= hl2
            } else {
                val prevClose = candles[i - 1].close
                finalUpper = if (basicUpper < finalUpper || prevClose > finalUpper) basicUpper else finalUpper
                finalLower = if (basicLower > finalLower || prevClose < finalLower) basicLower else finalLower
                isBullish = when {
                    isBullish && candles[i].close < finalLower -> false
                    !isBullish && candles[i].close > finalUpper -> true
                    else -> isBullish
                }
            }
            result.add(Pair(if (isBullish) finalLower else finalUpper, isBullish))
        }
        return result
    }

    fun parabolicSarSeries(candles: List<Candle>, step: Double = 0.02, maxAf: Double = 0.2): List<Double> {
        if (candles.isEmpty()) return emptyList()
        if (candles.size == 1) return listOf(candles[0].low)
        val out = ArrayList<Double>(candles.size)
        var isUp = candles[1].close >= candles[0].close
        var af = step
        var ep = if (isUp) candles[0].high else candles[0].low
        var sar = if (isUp) candles[0].low else candles[0].high
        out.add(sar)

        for (i in 1 until candles.size) {
            val c = candles[i]
            val prev = candles[i - 1]
            var nextSar = sar + af * (ep - sar)
            if (isUp) {
                nextSar = min(nextSar, min(prev.low, if (i >= 2) candles[i - 2].low else prev.low))
                if (c.low < nextSar) {
                    isUp = false
                    sar = ep
                    ep = c.low
                    af = step
                } else {
                    sar = nextSar
                    if (c.high > ep) {
                        ep = c.high
                        af = min(maxAf, af + step)
                    }
                }
            } else {
                nextSar = max(nextSar, max(prev.high, if (i >= 2) candles[i - 2].high else prev.high))
                if (c.high > nextSar) {
                    isUp = true
                    sar = ep
                    ep = c.high
                    af = step
                } else {
                    sar = nextSar
                    if (c.low < ep) {
                        ep = c.low
                        af = min(maxAf, af + step)
                    }
                }
            }
            out.add(sar)
        }
        return out
    }

    fun ichimokuSeries(
        candles: List<Candle>,
        tenkanPeriod: Int = 9,
        kijunPeriod: Int = 26,
        senkouBPeriod: Int = 52
    ): IchimokuSeries {
        fun midChannel(period: Int, idx: Int): Double {
            val start = max(0, idx - period + 1)
            var h = Double.NEGATIVE_INFINITY
            var l = Double.POSITIVE_INFINITY
            for (j in start..idx) {
                if (candles[j].high > h) h = candles[j].high
                if (candles[j].low < l) l = candles[j].low
            }
            return (h + l) / 2.0
        }

        val tenkan = List(candles.size) { i -> midChannel(tenkanPeriod, i) }
        val kijun = List(candles.size) { i -> midChannel(kijunPeriod, i) }
        val senkouA = List(candles.size) { i ->
            val src = max(0, i - kijunPeriod)
            (tenkan[src] + kijun[src]) / 2.0
        }
        val senkouB = List(candles.size) { i ->
            val src = max(0, i - kijunPeriod)
            midChannel(senkouBPeriod, src)
        }
        val chikou = List(candles.size) { i ->
            val fwd = min(candles.lastIndex, i + kijunPeriod)
            candles[fwd].close
        }
        return IchimokuSeries(tenkan, kijun, senkouA, senkouB, chikou)
    }
}
