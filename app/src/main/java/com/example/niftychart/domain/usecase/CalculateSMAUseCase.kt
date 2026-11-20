package com.example.niftychart.domain.usecase

import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SmaPeriod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure domain Simple Moving Average (SMA) calculation engine supporting
 * SMA 20, SMA 50, SMA 100, and SMA 200 using an O(N) sliding sum.
 */
class CalculateSMAUseCase(
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    fun calculate(candles: List<OHLCV>, period: Int): List<Double?> {
        if (period <= 0 || candles.isEmpty()) return emptyList()
        val closes = candles.map { it.close }
        return calculateFromCloses(closes, period)
    }

    fun calculateFromCloses(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.isEmpty()) return emptyList()
        if (closes.size < period) {
            return List(closes.size) { null }
        }

        val result = MutableList<Double?>(closes.size) { null }
        var windowSum = 0.0
        var invalidInWindow = 0

        for (i in closes.indices) {
            val value = closes[i]
            if (value.isFinite()) {
                windowSum += value
            } else {
                invalidInWindow++
            }

            if (i >= period) {
                val outgoing = closes[i - period]
                if (outgoing.isFinite()) {
                    windowSum -= outgoing
                } else {
                    invalidInWindow--
                }
            }

            if (i >= period - 1 && invalidInWindow == 0) {
                result[i] = windowSum / period
            }
        }
        return result
    }

    suspend fun calculateEnabled(
        candles: List<OHLCV>,
        enabledPeriods: Set<SmaPeriod>
    ): Map<SmaPeriod, List<Double?>> = withContext(computeDispatcher) {
        if (candles.isEmpty() || enabledPeriods.isEmpty()) return@withContext emptyMap()
        val closes = candles.map { it.close }
        enabledPeriods.associateWith { sma ->
            calculateFromCloses(closes, sma.period)
        }
    }
}
