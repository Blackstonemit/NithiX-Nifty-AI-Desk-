package com.example.niftychart.domain.usecase

import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.RsiSeries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure domain Relative Strength Index (RSI) engine using Wilder's smoothing method.
 * Default period is 14; values are strictly bounded between 0.0 and 100.0.
 */
class CalculateRSIUseCase(
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    fun calculate(candles: List<OHLCV>, period: Int = DEFAULT_RSI_PERIOD): RsiSeries {
        if (candles.isEmpty() || period < 1) {
            return RsiSeries(period = period.coerceAtLeast(1), values = emptyList())
        }
        return calculateFromCloses(candles.map { it.close }, period)
    }

    fun calculateFromCloses(closes: List<Double>, period: Int = DEFAULT_RSI_PERIOD): RsiSeries {
        val safePeriod = period.coerceIn(2, 100)
        if (closes.size <= safePeriod) {
            return RsiSeries(
                period = safePeriod,
                values = List(closes.size) { null }
            )
        }

        val values = MutableList<Double?>(closes.size) { null }

        // Initial Wilder averages computed over the first [1..safePeriod] deltas
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..safePeriod) {
            val prev = closes[i - 1]
            val curr = closes[i]
            if (!prev.isFinite() || !curr.isFinite()) {
                return RsiSeries(period = safePeriod, values = List(closes.size) { null })
            }
            val delta = curr - prev
            if (delta > 0.0) {
                gainSum += delta
            } else {
                lossSum += -delta
            }
        }

        var avgGain = gainSum / safePeriod
        var avgLoss = lossSum / safePeriod
        values[safePeriod] = computeRsiValue(avgGain, avgLoss)

        // Wilder's recursive smoothing for subsequent bars
        for (i in (safePeriod + 1) until closes.size) {
            val prev = closes[i - 1]
            val curr = closes[i]
            val delta = if (prev.isFinite() && curr.isFinite()) curr - prev else 0.0
            val gain = if (delta > 0.0) delta else 0.0
            val loss = if (delta < 0.0) -delta else 0.0

            avgGain = ((avgGain * (safePeriod - 1)) + gain) / safePeriod
            avgLoss = ((avgLoss * (safePeriod - 1)) + loss) / safePeriod
            values[i] = computeRsiValue(avgGain, avgLoss)
        }

        return RsiSeries(
            period = safePeriod,
            values = values
        )
    }

    suspend fun calculateAsync(candles: List<OHLCV>, period: Int = DEFAULT_RSI_PERIOD): RsiSeries =
        withContext(computeDispatcher) {
            calculate(candles, period)
        }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double {
        return when {
            avgLoss <= EPSILON && avgGain <= EPSILON -> 50.0
            avgLoss <= EPSILON -> 100.0
            avgGain <= EPSILON -> 0.0
            else -> {
                val rs = avgGain / avgLoss
                (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
            }
        }
    }

    companion object {
        const val DEFAULT_RSI_PERIOD = 14
        private const val EPSILON = 1e-12
    }
}
