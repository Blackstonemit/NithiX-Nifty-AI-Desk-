package com.example.engine.chart

import com.example.model.Candle
import com.example.model.LiquiditySweepEvent
import com.example.model.MarketStructureAnalysis
import com.example.model.OrbLevels
import com.example.model.SmartMoneyZone
import com.example.model.StructureBreakEvent
import com.example.model.SwingPoint
import com.example.model.Timeframe
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Market Structure & Smart Money Concepts (SMC) engine:
 * - Swing High / Swing Low (HH, HL, LH, LL)
 * - Break of Structure (BOS) & Change of Character (CHoCH)
 * - Bullish / Bearish Order Blocks (with mitigation check)
 * - Fair Value Gaps / FVG (with mitigation check)
 * - Buy-side & Sell-side Liquidity Sweeps
 * - Premium / Discount / Equilibrium Dealing Range
 * - 15m & 30m Opening Range Breakout (ORB)
 */
object MarketStructureEngine {

    fun analyze(
        candles: List<Candle>,
        timeframe: Timeframe,
        pivotRadius: Int = 3
    ): MarketStructureAnalysis {
        if (candles.size < 10) {
            val high = candles.maxOfOrNull { it.high } ?: 0.0
            val low = candles.minOfOrNull { it.low } ?: 0.0
            return MarketStructureAnalysis(
                swingPoints = emptyList(),
                structureBreaks = emptyList(),
                orderBlocks = emptyList(),
                fvgZones = emptyList(),
                liquiditySweeps = emptyList(),
                dealingRangeHigh = high,
                dealingRangeLow = low,
                equilibriumPrice = (high + low) / 2.0,
                currentZoneLabel = "Equilibrium",
                orb15m = null,
                orb30m = null
            )
        }

        val rawSwings = mutableListOf<SwingPoint>()
        var lastSwingHighPrice: Double? = null
        var lastSwingLowPrice: Double? = null

        for (i in pivotRadius until candles.size - pivotRadius) {
            val c = candles[i]
            var isHigh = true
            var isLow = true
            for (k in 1..pivotRadius) {
                if (candles[i - k].high >= c.high || candles[i + k].high > c.high) isHigh = false
                if (candles[i - k].low <= c.low || candles[i + k].low < c.low) isLow = false
            }
            if (isHigh) {
                val tag = when {
                    lastSwingHighPrice == null -> "SH"
                    c.high > lastSwingHighPrice -> "HH"
                    else -> "LH"
                }
                lastSwingHighPrice = c.high
                rawSwings.add(SwingPoint(i, c.timestamp, c.high, true, tag))
            }
            if (isLow) {
                val tag = when {
                    lastSwingLowPrice == null -> "SL"
                    c.low > lastSwingLowPrice -> "HL"
                    else -> "LL"
                }
                lastSwingLowPrice = c.low
                rawSwings.add(SwingPoint(i, c.timestamp, c.low, false, tag))
            }
        }

        // BOS & CHoCH detection using confirmed swings prior to candle i
        val structureBreaks = mutableListOf<StructureBreakEvent>()
        val liquiditySweeps = mutableListOf<LiquiditySweepEvent>()
        var currentTrendBullish: Boolean? = null
        val highs = rawSwings.filter { it.isHigh }
        val lows = rawSwings.filter { !it.isHigh }

        highs.forEach { sh ->
            var broken = false
            for (i in (sh.index + pivotRadius + 1) until candles.size) {
                val c = candles[i]
                if (!broken && c.close > sh.price) {
                    val eventType = if (currentTrendBullish == false) "CHoCH" else "BOS"
                    currentTrendBullish = true
                    structureBreaks.add(StructureBreakEvent(i, c.timestamp, sh.price, eventType, isBullish = true))
                    broken = true
                } else if (c.high > sh.price && c.close < sh.price) {
                    liquiditySweeps.add(LiquiditySweepEvent(i, c.timestamp, sh.price, isBuySide = true))
                    break
                }
            }
        }

        rawSwings.filter { !it.isHigh }.forEach { sl ->
            var broken = false
            for (i in (sl.index + pivotRadius + 1) until candles.size) {
                val c = candles[i]
                if (!broken && c.close < sl.price) {
                    val eventType = if (currentTrendBullish == true) "CHoCH" else "BOS"
                    currentTrendBullish = false
                    structureBreaks.add(StructureBreakEvent(i, c.timestamp, sl.price, eventType, isBullish = false))
                    broken = true
                } else if (c.low < sl.price && c.close > sl.price) {
                    liquiditySweeps.add(LiquiditySweepEvent(i, c.timestamp, sl.price, isBuySide = false))
                    break
                }
            }
        }

        // Order Blocks anchored immediately preceding BOS/CHoCH displacements
        val atrSeries = IndicatorCalculationEngine.atrSeries(candles, 14)
        val orderBlocks = mutableListOf<SmartMoneyZone>()
        structureBreaks.takeLast(8).forEachIndexed { idx, brk ->
            val atr = atrSeries.getOrElse(brk.index) { 25.0 }
            val searchStart = max(0, brk.index - 8)
            var obIdx: Int? = null
            for (j in brk.index - 1 downTo searchStart) {
                val cand = candles[j]
                if (brk.isBullish && cand.close < cand.open) {
                    obIdx = j; break
                } else if (!brk.isBullish && cand.close > cand.open) {
                    obIdx = j; break
                }
            }
            if (obIdx != null) {
                val obCandle = candles[obIdx]
                val top = max(obCandle.open, obCandle.high)
                val bot = min(obCandle.low, obCandle.close)
                if (top - bot <= atr * 4.0) {
                    val mitigated = (brk.index + 1 until candles.size).any { k ->
                        if (brk.isBullish) candles[k].low <= top && candles[k].close < bot
                        else candles[k].high >= bot && candles[k].close > top
                    }
                    orderBlocks.add(
                        SmartMoneyZone(
                            id = "ob_${obIdx}_$idx",
                            type = "OB",
                            startIndex = obIdx,
                            endIndex = candles.lastIndex,
                            topPrice = top,
                            bottomPrice = bot,
                            isBullish = brk.isBullish,
                            isMitigated = mitigated
                        )
                    )
                }
            }
        }

        // 3-candle Fair Value Gaps (FVG) + mitigation check
        val fvgZones = mutableListOf<SmartMoneyZone>()
        for (i in 2 until candles.size) {
            val c0 = candles[i - 2]
            val c2 = candles[i]
            val minGap = max(atrSeries.getOrElse(i) { 10.0 } * 0.18, c2.close * 0.0005)
            if (c2.low - c0.high > minGap) {
                val bot = c0.high
                val top = c2.low
                val mitigated = (i + 1 until candles.size).any { k -> candles[k].low <= bot }
                fvgZones.add(
                    SmartMoneyZone("fvg_b_$i", "FVG", i - 1, candles.lastIndex, top, bot, true, mitigated)
                )
            } else if (c0.low - c2.high > minGap) {
                val top = c0.low
                val bot = c2.high
                val mitigated = (i + 1 until candles.size).any { k -> candles[k].high >= top }
                fvgZones.add(
                    SmartMoneyZone("fvg_s_$i", "FVG", i - 1, candles.lastIndex, top, bot, false, mitigated)
                )
            }
        }

        // Premium / Discount Dealing Range over last 50 bars
        val recentSlice = candles.takeLast(min(50, candles.size))
        val rangeHigh = recentSlice.maxOf { it.high }
        val rangeLow = recentSlice.minOf { it.low }
        val eq = (rangeHigh + rangeLow) / 2.0
        val lastClose = candles.last().close
        val span = max(rangeHigh - rangeLow, 0.01)
        val pctPos = ((lastClose - rangeLow) / span * 100.0).toInt().coerceIn(0, 100)
        val zoneLabel = when {
            lastClose > eq * 1.001 -> "Premium ($pctPos%)"
            lastClose < eq * 0.999 -> "Discount ($pctPos%)"
            else -> "Equilibrium ($pctPos%)"
        }

        val (orb15, orb30) = computeIntradayOrbs(candles, timeframe)

        return MarketStructureAnalysis(
            swingPoints = rawSwings.takeLast(14),
            structureBreaks = structureBreaks.sortedBy { it.index }.takeLast(8),
            orderBlocks = orderBlocks.takeLast(6),
            fvgZones = fvgZones.takeLast(6),
            liquiditySweeps = liquiditySweeps.sortedBy { it.index }.takeLast(6),
            dealingRangeHigh = rangeHigh,
            dealingRangeLow = rangeLow,
            equilibriumPrice = eq,
            currentZoneLabel = zoneLabel,
            orb15m = orb15,
            orb30m = orb30
        )
    }

    private fun computeIntradayOrbs(candles: List<Candle>, timeframe: Timeframe): Pair<OrbLevels?, OrbLevels?> {
        if (candles.isEmpty() || timeframe.minutes >= 60) return Pair(null, null)
        val latestDay = TimeframeAggregationEngine.dateKeyIst(candles.last().timestamp)
        val dayCandles = candles.filter { TimeframeAggregationEngine.dateKeyIst(it.timestamp) == latestDay }
        if (dayCandles.isEmpty()) return Pair(null, null)

        val barsFor15 = max(1, (15 + timeframe.minutes - 1) / timeframe.minutes)
        val barsFor30 = max(1, (30 + timeframe.minutes - 1) / timeframe.minutes)

        fun buildOrb(bars: Int, mins: Int): OrbLevels {
            val opening = dayCandles.take(min(bars, dayCandles.size))
            val h = opening.maxOf { it.high }
            val l = opening.minOf { it.low }
            val close = candles.last().close
            val status = when {
                close > h -> "Bullish Breakout"
                close < l -> "Bearish Breakdown"
                else -> "Inside Range"
            }
            return OrbLevels(high = h, low = l, durationMinutes = mins, status = status)
        }

        return Pair(buildOrb(barsFor15, 15), buildOrb(barsFor30, 30))
    }
}
