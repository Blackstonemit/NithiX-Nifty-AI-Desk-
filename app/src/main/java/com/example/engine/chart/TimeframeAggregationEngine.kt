package com.example.engine.chart

import com.example.model.Candle
import com.example.model.Timeframe
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic NSE session, holiday, timeframe aggregation, and Heikin-Ashi transformation engine.
 * Enforces Asia/Kolkata (IST) session boundaries (09:15 - 15:30 IST) and never fabricates missing candles.
 */
object TimeframeAggregationEngine {

    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // Verified NSE Equity / F&O exchange holidays (YYYY-MM-DD in Asia/Kolkata)
    val nseHolidays: Set<String> = setOf(
        "2025-02-26", "2025-03-14", "2025-03-31", "2025-04-10", "2025-04-14",
        "2025-04-18", "2025-05-01", "2025-08-15", "2025-08-27", "2025-10-02",
        "2025-10-21", "2025-10-22", "2025-11-05", "2025-12-25",
        "2026-01-26", "2026-03-03", "2026-03-26", "2026-03-31", "2026-04-03",
        "2026-04-14", "2026-05-01", "2026-05-28", "2026-06-26", "2026-09-14",
        "2026-10-02", "2026-10-20", "2026-11-10", "2026-11-24", "2026-12-25"
    )

    fun dateKeyIst(epochMs: Long): String {
        val cal = Calendar.getInstance(istZone).apply { timeInMillis = epochMs }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun isNseTradingDay(epochMs: Long): Boolean {
        val cal = Calendar.getInstance(istZone).apply { timeInMillis = epochMs }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        return dateKeyIst(epochMs) !in nseHolidays
    }

    fun isWithinAfterHoursIst(epochMs: Long): Boolean {
        val cal = Calendar.getInstance(istZone).apply { timeInMillis = epochMs }
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return mins < (9 * 60 + 15) || mins >= (15 * 60 + 30)
    }

    fun detectHolidayOrSessionGaps(candles: List<Candle>, timeframe: Timeframe): Boolean {
        if (candles.size < 2 || timeframe.minutes >= 375) return false
        val expectedMs = timeframe.minutes * 60_000L
        for (i in 1 until candles.size) {
            val delta = candles[i].timestamp - candles[i - 1].timestamp
            if (delta > expectedMs * 4L) return true
        }
        return false
    }

    /**
     * Aggregates verified base candles into higher NSE-session-aligned timeframes (e.g., 5m->10m,
     * 15m->45m, 1h->2h/4h) without merging across day boundaries or inventing empty bars.
     */
    fun aggregateCandlesForTimeframe(
        sourceCandles: List<Candle>,
        targetTimeframe: Timeframe
    ): List<Candle> {
        val cleaned = sourceCandles
            .sortedBy { it.timestamp }
            .distinctBy { it.timestamp }
            .filter { it.open > 0.0 && it.high >= it.low && it.close > 0.0 }

        if (cleaned.isEmpty()) return emptyList()

        val bucketMinutes = when (targetTimeframe) {
            Timeframe.M10 -> 10
            Timeframe.M45 -> 45
            Timeframe.H2 -> 120
            Timeframe.H4 -> 240
            else -> return recomputeSessionVwap(cleaned, targetTimeframe)
        }

        val grouped = LinkedHashMap<String, MutableList<Candle>>()
        val cal = Calendar.getInstance(istZone)
        val marketOpenMinute = 9 * 60 + 15

        for (c in cleaned) {
            cal.timeInMillis = c.timestamp
            val dayKey = dateKeyIst(c.timestamp)
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val minutesFromOpen = max(0, minuteOfDay - marketOpenMinute)
            val bucketIndex = minutesFromOpen / bucketMinutes
            val key = "${dayKey}_$bucketIndex"
            grouped.getOrPut(key) { mutableListOf() }.add(c)
        }

        val aggregated = ArrayList<Candle>(grouped.size)
        for (group in grouped.values) {
            val first = group.first()
            val last = group.last()
            var high = first.high
            var low = first.low
            var vol = 0L
            for (g in group) {
                if (g.high > high) high = g.high
                if (g.low < low) low = g.low
                vol += max(0L, g.volume)
            }
            aggregated.add(
                Candle(
                    timestamp = first.timestamp,
                    open = first.open,
                    high = high,
                    low = low,
                    close = last.close,
                    volume = vol,
                    vwap = last.vwap
                )
            )
        }

        return recomputeSessionVwap(aggregated, targetTimeframe)
    }

    fun recomputeSessionVwap(candles: List<Candle>, timeframe: Timeframe): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        val isIntraday = timeframe.minutes < 375
        var lastDayKey = ""
        var cumTpVol = 0.0
        var cumVol = 0.0
        var fallbackSum = 0.0
        var sessionCount = 0

        return candles.map { c ->
            val dayKey = if (isIntraday) dateKeyIst(c.timestamp) else "ALL"
            if (dayKey != lastDayKey) {
                lastDayKey = dayKey
                cumTpVol = 0.0
                cumVol = 0.0
                fallbackSum = 0.0
                sessionCount = 0
            }
            val tp = (c.high + c.low + c.close) / 3.0
            sessionCount++
            val vwap = if (c.volume > 0L) {
                cumTpVol += tp * c.volume
                cumVol += c.volume
                cumTpVol / cumVol
            } else {
                fallbackSum += tp
                fallbackSum / sessionCount
            }
            c.copy(vwap = vwap)
        }
    }

    /**
     * Converts OHLC candles to deterministic Heikin-Ashi candles while preserving timestamp and volume.
     */
    fun toHeikinAshi(candles: List<Candle>): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        val result = ArrayList<Candle>(candles.size)
        var prevHaOpen = (candles[0].open + candles[0].close) / 2.0
        var prevHaClose = (candles[0].open + candles[0].high + candles[0].low + candles[0].close) / 4.0

        candles.forEachIndexed { i, c ->
            val haClose = (c.open + c.high + c.low + c.close) / 4.0
            val haOpen = if (i == 0) prevHaOpen else (prevHaOpen + prevHaClose) / 2.0
            val haHigh = max(c.high, max(haOpen, haClose))
            val haLow = min(c.low, min(haOpen, haClose))
            prevHaOpen = haOpen
            prevHaClose = haClose
            result.add(
                c.copy(
                    open = haOpen,
                    high = haHigh,
                    low = haLow,
                    close = haClose
                )
            )
        }
        return result
    }

    fun formatCandleCountdown(timeframe: Timeframe, nowEpochMs: Long): String {
        if (timeframe.minutes >= 375) {
            val cal = Calendar.getInstance(istZone).apply { timeInMillis = nowEpochMs }
            val currentMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val closeMin = 15 * 60 + 30
            if (!isNseTradingDay(nowEpochMs) || currentMin < 9 * 60 + 15 || currentMin >= closeMin) {
                return "CLOSED"
            }
            val remSec = ((closeMin - currentMin) * 60 - cal.get(Calendar.SECOND)).coerceAtLeast(0)
            val hrs = remSec / 3600
            val mins = (remSec % 3600) / 60
            val secs = remSec % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        }
        val cal = Calendar.getInstance(istZone).apply { timeInMillis = nowEpochMs }
        val currentMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (!isNseTradingDay(nowEpochMs) || currentMin < 9 * 60 + 15 || currentMin >= 15 * 60 + 30) {
            return "CLOSED"
        }
        val totalSecSinceOpen = ((currentMin - (9 * 60 + 15)) * 60) + cal.get(Calendar.SECOND)
        val periodSec = timeframe.minutes * 60
        val remSec = periodSec - (totalSecSinceOpen % periodSec)
        val mins = remSec / 60
        val secs = remSec % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
