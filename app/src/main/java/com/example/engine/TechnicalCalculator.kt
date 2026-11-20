package com.example.engine

import com.example.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object TechnicalCalculator {

    fun calculateEma(prices: List<Double>, period: Int): Double {
        return calculateEmaSeries(prices, period).lastOrNull() ?: 0.0
    }

    /**
     * Computes a full EMA series in O(n) time so charts can plot accurate rolling EMA curves.
     */
    fun calculateEmaSeries(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        if (period <= 1) return prices
        val multiplier = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(prices.size)

        if (prices.size < period) {
            var runningAvg = prices[0]
            result.add(runningAvg)
            for (i in 1 until prices.size) {
                runningAvg = (prices[i] - runningAvg) * multiplier + runningAvg
                result.add(runningAvg)
            }
            return result
        }

        var sum = 0.0
        for (i in 0 until period) {
            sum += prices[i]
            result.add(sum / (i + 1))
        }
        var ema = sum / period
        result[period - 1] = ema
        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            result.add(ema)
        }
        return result
    }

    fun calculateRsi(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period + 1) return 50.0
        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = candles[i].close - candles[i - 1].close
            if (change > 0) gains += change else losses += abs(change)
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        for (i in (period + 1) until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgGain == 0.0 && avgLoss == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /**
     * Single-pass O(n) MACD(12, 26, 9) calculation.
     */
    fun calculateMacd(candles: List<Candle>): Triple<Double, Double, Double> {
        val closes = candles.map { it.close }
        if (closes.size < 26) return Triple(0.0, 0.0, 0.0)

        val ema12Series = calculateEmaSeries(closes, 12)
        val ema26Series = calculateEmaSeries(closes, 26)

        val macdSeries = List(closes.size) { i -> ema12Series[i] - ema26Series[i] }
        val signalSeries = calculateEmaSeries(macdSeries, 9)
        val macdLine = macdSeries.last()
        val signalLine = signalSeries.last()
        val histogram = macdLine - signalLine
        return Triple(macdLine, signalLine, histogram)
    }

    fun calculateVwap(candles: List<Candle>): Double {
        if (candles.isEmpty()) return 0.0
        var cumulativeTpVol = 0.0
        var cumulativeVol = 0.0
        for (c in candles) {
            val tp = (c.high + c.low + c.close) / 3.0
            cumulativeTpVol += tp * c.volume
            cumulativeVol += c.volume
        }
        return if (cumulativeVol > 0) cumulativeTpVol / cumulativeVol else candles.map { (it.high + it.low + it.close) / 3.0 }.average()
    }

    fun calculateBollingerBands(candles: List<Candle>, period: Int = 20, multiplier: Double = 2.0): Triple<Double, Double, Double> {
        val closes = candles.map { it.close }
        if (closes.isEmpty()) return Triple(0.0, 0.0, 0.0)
        if (closes.size < period) {
            val avg = closes.average()
            return Triple(avg + 50, avg, avg - 50)
        }
        val recent = closes.takeLast(period)
        val sma = recent.average()
        val variance = recent.map { (it - sma) * (it - sma) }.average()
        val stdDev = sqrt(variance)
        return Triple(sma + multiplier * stdDev, sma, sma - multiplier * stdDev)
    }

    fun calculateAtr(candles: List<Candle>, period: Int = 14): Double {
        if (candles.isEmpty()) return 0.0
        if (candles.size < 2) return max(0.0, candles.first().high - candles.first().low)
        val p = max(1, period)
        val tr = DoubleArray(candles.size)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prevClose = candles[i - 1].close
            tr[i] = max(current.high - current.low, max(abs(current.high - prevClose), abs(current.low - prevClose)))
        }
        var rma = tr[0]
        for (i in 1 until candles.size) {
            rma = if (i < p) {
                (0..i).map { tr[it] }.average()
            } else {
                ((rma * (p - 1)) + tr[i]) / p
            }
        }
        return rma
    }

    /**
     * Stateful trailing-band Supertrend calculation over the candle series.
     */
    fun calculateSupertrend(candles: List<Candle>, period: Int = 10, multiplier: Double = 3.0): Pair<Double, Boolean> {
        if (candles.isEmpty()) return Pair(0.0, true)
        if (candles.size < 2) {
            val c = candles.last()
            return Pair((c.high + c.low) / 2.0, c.close >= c.open)
        }

        val trValues = ArrayList<Double>(candles.size)
        trValues.add(candles[0].high - candles[0].low)
        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prevClose = candles[i - 1].close
            trValues.add(max(curr.high - curr.low, max(abs(curr.high - prevClose), abs(curr.low - prevClose))))
        }

        var finalUpper = 0.0
        var finalLower = 0.0
        var isBullish = true

        for (i in candles.indices) {
            val start = max(0, i - period + 1)
            val atr = trValues.subList(start, i + 1).average()
            val hl2 = (candles[i].high + candles[i].low) / 2.0
            val basicUpper = hl2 + multiplier * atr
            val basicLower = hl2 - multiplier * atr

            if (i == 0) {
                finalUpper = basicUpper
                finalLower = basicLower
                isBullish = candles[i].close >= hl2
                continue
            }

            val prevClose = candles[i - 1].close
            finalUpper = if (basicUpper < finalUpper || prevClose > finalUpper) basicUpper else finalUpper
            finalLower = if (basicLower > finalLower || prevClose < finalLower) basicLower else finalLower

            isBullish = when {
                isBullish && candles[i].close < finalLower -> false
                !isBullish && candles[i].close > finalUpper -> true
                else -> isBullish
            }
        }

        return Pair(if (isBullish) finalLower else finalUpper, isBullish)
    }

    fun calculateAdx(candles: List<Candle>, period: Int = 14): Triple<Double, Double, Double> {
        if (candles.size < period + 2) return Triple(0.0, 0.0, 0.0)
        var plusDmSum = 0.0
        var minusDmSum = 0.0
        var trSum = 0.0

        for (i in (candles.size - period) until candles.size) {
            val current = candles[i]
            val prev = candles[i - 1]
            val upMove = current.high - prev.high
            val downMove = prev.low - current.low

            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr = max(current.high - current.low, max(abs(current.high - prev.close), abs(current.low - prev.close)))

            plusDmSum += plusDm
            minusDmSum += minusDm
            trSum += tr
        }

        if (trSum == 0.0) trSum = 1.0
        val diPlus = (plusDmSum / trSum) * 100.0
        val diMinus = (minusDmSum / trSum) * 100.0
        val sumDi = diPlus + diMinus
        val dx = if (sumDi > 0) (abs(diPlus - diMinus) / sumDi) * 100.0 else 25.0
        return Triple(dx, diPlus, diMinus)
    }

    fun detectCandlestickPattern(candles: List<Candle>): String {
        if (candles.size < 2) return "Neutral Consolidation"
        val c = candles.last()
        val prev = candles[candles.size - 2]
        val body = abs(c.close - c.open)
        val range = c.high - c.low
        val isGreen = c.close >= c.open
        val lowerWick = if (isGreen) c.open - c.low else c.close - c.low
        val upperWick = if (isGreen) c.high - c.close else c.high - c.open

        return when {
            lowerWick >= 2.0 * body && upperWick <= body * 0.3 && range > 20 -> "Bullish Hammer / Pin Bar"
            upperWick >= 2.0 * body && lowerWick <= body * 0.3 && range > 20 -> "Bearish Shooting Star"
            isGreen && prev.close < prev.open && c.close > prev.open && c.open < prev.close -> "Bullish Engulfing"
            !isGreen && prev.close > prev.open && c.close < prev.open && c.open > prev.close -> "Bearish Engulfing"
            body / (if (range > 0) range else 1.0) > 0.85 -> if (isGreen) "Bullish Marubozu Impulse" else "Bearish Marubozu Impulse"
            body <= range * 0.1 -> "Doji / Indecision"
            else -> if (isGreen) "Bullish Continuation" else "Bearish Continuation"
        }
    }
}
