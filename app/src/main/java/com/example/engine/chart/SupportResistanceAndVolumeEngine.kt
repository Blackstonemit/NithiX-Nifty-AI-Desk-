package com.example.engine.chart

import com.example.engine.TechnicalCalculator
import com.example.model.Candle
import com.example.model.MarketStructureAnalysis
import com.example.model.SrSource
import com.example.model.SupportResistanceLevel
import com.example.model.Timeframe
import com.example.model.VolumeProfileBin
import com.example.model.VolumeProfileSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Computes automatic clustered Support/Resistance zones, PDH/PDL, PWH/PWL,
 * Classic & Camarilla Pivot Points, Dynamic MAs, and Volume Profile (VPVR: POC/VAH/VAL/RVOL).
 */
object SupportResistanceEngine {

    fun detectLevels(
        candles: List<Candle>,
        structure: MarketStructureAnalysis
    ): List<SupportResistanceLevel> {
        if (candles.size < 5) return emptyList()
        val lastPrice = candles.last().close
        val atr = IndicatorCalculationEngine.atrSeries(candles, 14).lastOrNull()?.coerceAtLeast(lastPrice * 0.001)
            ?: (lastPrice * 0.005)
        val clusterTol = atr * 0.45

        val levels = mutableListOf<SupportResistanceLevel>()

        // 1. Swing High / Low clustering
        val rawCandidates = structure.swingPoints.map { it.price }
        val clusters = mutableListOf<MutableList<Double>>()
        for (p in rawCandidates.sorted()) {
            val match = clusters.firstOrNull { c -> abs(c.average() - p) <= clusterTol }
            if (match != null) match.add(p) else clusters.add(mutableListOf(p))
        }
        clusters.forEachIndexed { idx, pts ->
            val mid = pts.average()
            val touchScore = (40 + pts.size * 18).coerceAtMost(96)
            val isSup = mid <= lastPrice
            levels.add(
                SupportResistanceLevel(
                    id = "swing_$idx",
                    price = mid,
                    zoneLow = pts.min() - atr * 0.1,
                    zoneHigh = pts.max() + atr * 0.1,
                    label = if (isSup) "Swing Sup (${pts.size}x)" else "Swing Res (${pts.size}x)",
                    touchCount = pts.size,
                    strengthScore = touchScore,
                    source = SrSource.SWING,
                    isSupport = isSup
                )
            )
        }

        // 2. Previous Day High/Low & Pivot Points (grouped by Asia/Kolkata trading days)
        val byDay = candles.groupBy { TimeframeAggregationEngine.dateKeyIst(it.timestamp) }
        val dayKeys = byDay.keys.toList()
        val prevDayCandles = if (dayKeys.size >= 2) byDay[dayKeys[dayKeys.size - 2]].orEmpty() else emptyList()
        if (prevDayCandles.isNotEmpty()) {
            val pdh = prevDayCandles.maxOf { it.high }
            val pdl = prevDayCandles.minOf { it.low }
            val pdc = prevDayCandles.last().close
            val range = max(pdh - pdl, 0.01)

            levels.add(
                SupportResistanceLevel("pdh", pdh, pdh - atr * 0.08, pdh + atr * 0.08, "PDH", 2, 88, SrSource.PDH_PDL, pdh <= lastPrice)
            )
            levels.add(
                SupportResistanceLevel("pdl", pdl, pdl - atr * 0.08, pdl + atr * 0.08, "PDL", 2, 88, SrSource.PDH_PDL, pdl <= lastPrice)
            )

            // Classic Floor Pivots
            val pivot = (pdh + pdl + pdc) / 3.0
            val r1 = 2.0 * pivot - pdl
            val s1 = 2.0 * pivot - pdh
            levels.add(SupportResistanceLevel("pp", pivot, pivot, pivot, "Classic Pivot", 1, 76, SrSource.PIVOT_CLASSIC, pivot <= lastPrice))
            levels.add(SupportResistanceLevel("r1", r1, r1, r1, "Pivot R1", 1, 74, SrSource.PIVOT_CLASSIC, r1 <= lastPrice))
            levels.add(SupportResistanceLevel("s1", s1, s1, s1, "Pivot S1", 1, 74, SrSource.PIVOT_CLASSIC, s1 <= lastPrice))

            // Camarilla H3/L3
            val camH3 = pdc + range * 1.1 / 4.0
            val camL3 = pdc - range * 1.1 / 4.0
            levels.add(SupportResistanceLevel("cam_h3", camH3, camH3, camH3, "Camarilla H3", 1, 72, SrSource.PIVOT_CAMARILLA, camH3 <= lastPrice))
            levels.add(SupportResistanceLevel("cam_l3", camL3, camL3, camL3, "Camarilla L3", 1, 72, SrSource.PIVOT_CAMARILLA, camL3 <= lastPrice))
        }

        // 3. Multi-day rolling PWH / PWL approximation
        if (candles.size >= 20) {
            val prevBlock = candles.dropLast(min(5, candles.size / 4)).takeLast(min(40, candles.size))
            if (prevBlock.isNotEmpty()) {
                val pwh = prevBlock.maxOf { it.high }
                val pwl = prevBlock.minOf { it.low }
                levels.add(SupportResistanceLevel("pwh", pwh, pwh, pwh, "Prior Swing High", 2, 84, SrSource.PWH_PWL, pwh <= lastPrice))
                levels.add(SupportResistanceLevel("pwl", pwl, pwl, pwl, "Prior Swing Low", 2, 84, SrSource.PWH_PWL, pwl <= lastPrice))
            }
        }

        // 4. Dynamic Moving Average S&R (EMA50 / EMA200)
        val closes = candles.map { it.close }
        if (closes.size >= 50) {
            val ema50 = TechnicalCalculator.calculateEma(closes, 50)
            levels.add(SupportResistanceLevel("dyn_ema50", ema50, ema50, ema50, "Dynamic EMA50", 1, 70, SrSource.DYNAMIC_MA, ema50 <= lastPrice))
        }
        if (closes.size >= 100) {
            val ema200 = TechnicalCalculator.calculateEma(closes, min(200, closes.size))
            levels.add(SupportResistanceLevel("dyn_ema200", ema200, ema200, ema200, "Dynamic EMA200", 1, 75, SrSource.DYNAMIC_MA, ema200 <= lastPrice))
        }

        return levels
            .distinctBy { "${it.label}_${String.format(Locale.US, "%.1f", it.price)}" }
            .sortedBy { abs(it.price - lastPrice) }
            .take(14)
    }
}

object VolumeAnalysisEngine {

    /**
     * Computes Visible-Range Volume Profile (POC, 70% Value Area VAH/VAL), RVOL, and
     * candle-range delta approximation. Clearly flags when volume is approximated or index spot is 0-vol.
     */
    fun computeVolumeProfile(
        visibleCandles: List<Candle>,
        allCandles: List<Candle> = visibleCandles,
        binCount: Int = 24
    ): VolumeProfileSummary {
        if (visibleCandles.isEmpty()) {
            return VolumeProfileSummary(emptyList(), 0.0, 0.0, 0.0, 1.0, 50.0, isApproximated = true, hasVolumeData = false)
        }

        val minLow = visibleCandles.minOf { it.low }
        val maxHigh = visibleCandles.maxOf { it.high }
        val priceSpan = max(maxHigh - minLow, 0.01)
        val step = priceSpan / binCount

        val rawTotalVol = visibleCandles.sumOf { max(0L, it.volume) }
        val hasVol = rawTotalVol > 0L

        val bullVols = LongArray(binCount)
        val bearVols = LongArray(binCount)
        var upVolSum = 0L
        var downVolSum = 0L

        for (c in visibleCandles) {
            // For zero cash volume index feeds (e.g., NIFTY spot), distribute relative price activity safely
            val v = if (hasVol) max(0L, c.volume) else ((abs(c.close - c.open) + (c.high - c.low) * 0.25) * 100.0).toLong().coerceAtLeast(1L)
            val isBull = c.close >= c.open
            if (isBull) upVolSum += v else downVolSum += v

            val tp = (c.high + c.low + c.close) / 3.0
            val idx = ((tp - minLow) / step).toInt().coerceIn(0, binCount - 1)
            if (isBull) bullVols[idx] += v else bearVols[idx] += v
        }

        val bins = (0 until binCount).map { i ->
            VolumeProfileBin(
                lowPrice = minLow + i * step,
                highPrice = minLow + (i + 1) * step,
                totalVolume = bullVols[i] + bearVols[i],
                bullVolume = bullVols[i],
                bearVolume = bearVols[i]
            )
        }

        val pocIndex = bins.indices.maxByOrNull { bins[it].totalVolume } ?: 0
        val pocPrice = (bins[pocIndex].lowPrice + bins[pocIndex].highPrice) / 2.0

        val totalProfileVol = bins.sumOf { it.totalVolume }.coerceAtLeast(1L)
        val targetVaVol = (totalProfileVol * 0.70).toLong()
        var vaAccum = bins[pocIndex].totalVolume
        var loIdx = pocIndex
        var hiIdx = pocIndex

        while (vaAccum < targetVaVol && (loIdx > 0 || hiIdx < bins.lastIndex)) {
            val leftVol = if (loIdx > 0) bins[loIdx - 1].totalVolume else -1L
            val rightVol = if (hiIdx < bins.lastIndex) bins[hiIdx + 1].totalVolume else -1L
            if (rightVol >= leftVol && hiIdx < bins.lastIndex) {
                hiIdx++
                vaAccum += bins[hiIdx].totalVolume
            } else if (loIdx > 0) {
                loIdx--
                vaAccum += bins[loIdx].totalVolume
            } else break
        }

        val valPrice = bins[loIdx].lowPrice
        val vahPrice = bins[hiIdx].highPrice

        val recentVols = allCandles.takeLast(21)
        val lastVol = recentVols.lastOrNull()?.volume?.toDouble() ?: 0.0
        val avgVol20 = if (recentVols.size > 1) recentVols.dropLast(1).map { it.volume.toDouble() }.average() else lastVol
        val rvol = if (hasVol && avgVol20 > 0.0) (lastVol / avgVol20) else 1.0

        val totalDeltaVol = max(1L, upVolSum + downVolSum)
        val upPct = (upVolSum.toDouble() / totalDeltaVol.toDouble()) * 100.0

        return VolumeProfileSummary(
            bins = bins,
            pocPrice = pocPrice,
            vahPrice = vahPrice,
            valPrice = valPrice,
            rvolRatio = rvol,
            upVolumePct = upPct,
            isApproximated = true, // Candle OHLC approximation cleanly labeled
            hasVolumeData = hasVol
        )
    }
}
