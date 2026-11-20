package com.example.niftychart.domain.model

/**
 * Supported Simple Moving Average (SMA) overlay periods.
 */
enum class SmaPeriod(
    val period: Int,
    val label: String,
    val colorArgb: Long
) {
    SMA_20(20, "SMA 20", 0xFFFFB300),
    SMA_50(50, "SMA 50", 0xFF00E5FF),
    SMA_100(100, "SMA 100", 0xFFB388FF),
    SMA_200(200, "SMA 200", 0xFFFF7043)
}

/**
 * Output from Wilder's RSI calculation engine.
 */
data class RsiSeries(
    val period: Int = 14,
    val values: List<Double?> = emptyList(),
    val overboughtLevel: Double = 70.0,
    val centerLevel: Double = 50.0,
    val oversoldLevel: Double = 30.0
) {
    val latestValue: Double?
        get() = values.lastOrNull { it != null }
}

/**
 * Combined computed indicator bundle aligned index-for-index with the candle list.
 */
data class IndicatorSnapshot(
    val smaLines: Map<SmaPeriod, List<Double?>> = emptyMap(),
    val rsiSeries: RsiSeries = RsiSeries()
)
