package com.example.niftychart.domain.model

import com.example.model.Candle
import com.example.model.Timeframe

/**
 * Supported OHLCV storage and visualization intervals for NIFTY 50.
 */
enum class ChartInterval(
    val code: String,
    val displayLabel: String,
    val intervalMs: Long
) {
    M1("1m", "1m", 60_000L),
    M5("5m", "5m", 5 * 60_000L),
    M15("15m", "15m", 15 * 60_000L),
    M30("30m", "30m", 30 * 60_000L),
    H1("1h", "1h", 60 * 60_000L),
    D1("1d", "1D", 24 * 60 * 60_000L);

    fun toAppTimeframe(): Timeframe = when (this) {
        M1 -> Timeframe.M1
        M5 -> Timeframe.M5
        M15 -> Timeframe.M15
        M30 -> Timeframe.M30
        H1 -> Timeframe.H1
        D1 -> Timeframe.DAILY
    }

    companion object {
        fun fromCode(raw: String): ChartInterval {
            return entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) } ?: M15
        }

        fun fromTimeframe(timeframe: Timeframe): ChartInterval = when (timeframe) {
            Timeframe.M1, Timeframe.M3 -> M1
            Timeframe.M5, Timeframe.M10 -> M5
            Timeframe.M15 -> M15
            Timeframe.M30, Timeframe.M45 -> M30
            Timeframe.H1, Timeframe.H2, Timeframe.H4 -> H1
            Timeframe.DAILY, Timeframe.WEEKLY, Timeframe.MONTHLY -> D1
        }
    }
}

/**
 * Immutable domain model representing a single OHLCV candlestick bar.
 */
data class OHLCV(
    val id: Long = 0L,
    val symbol: String = SYMBOL_NIFTY_50,
    val timestamp: Long,
    val interval: ChartInterval,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val createdAt: Long = timestamp,
    val updatedAt: Long = timestamp
) {
    val isBullish: Boolean get() = close >= open
    val change: Double get() = close - open
    val changePct: Double get() = if (open != 0.0) ((close - open) / open) * 100.0 else 0.0

    fun isValid(): Boolean {
        return symbol.isNotBlank() &&
            timestamp > 0L &&
            open.isFinite() && open > 0.0 &&
            high.isFinite() && high > 0.0 &&
            low.isFinite() && low > 0.0 &&
            close.isFinite() && close > 0.0 &&
            high >= maxOf(open, close) &&
            low <= minOf(open, close) &&
            volume >= 0L
    }

    fun toLegacyCandle(): Candle = Candle(
        timestamp = timestamp,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        vwap = (high + low + close) / 3.0
    )

    companion object {
        const val SYMBOL_NIFTY_50 = "NIFTY 50"

        fun fromLegacyCandle(
            candle: Candle,
            symbol: String = SYMBOL_NIFTY_50,
            interval: ChartInterval,
            nowMs: Long = System.currentTimeMillis()
        ): OHLCV {
            val sanitizedHigh = maxOf(candle.high, candle.open, candle.close)
            val sanitizedLow = minOf(candle.low, candle.open, candle.close)
            return OHLCV(
                symbol = symbol,
                timestamp = candle.timestamp,
                interval = interval,
                open = candle.open,
                high = sanitizedHigh,
                low = sanitizedLow,
                close = candle.close,
                volume = candle.volume.coerceAtLeast(0L),
                createdAt = nowMs,
                updatedAt = nowMs
            )
        }
    }
}
