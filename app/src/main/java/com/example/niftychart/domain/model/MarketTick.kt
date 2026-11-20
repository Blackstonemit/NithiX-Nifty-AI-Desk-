package com.example.niftychart.domain.model

/**
 * Single real-time or simulated market trade tick received from a MarketDataProvider.
 */
data class MarketTick(
    val symbol: String = OHLCV.SYMBOL_NIFTY_50,
    val price: Double,
    val volume: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Stream"
) {
    fun isValid(): Boolean = symbol.isNotBlank() && price.isFinite() && price > 0.0 && volume >= 0L && timestamp > 0L
}

enum class FeedConnectionStatus {
    LIVE,
    SIMULATED,
    RECONNECTING,
    OFFLINE
}

data class SyncSummary(
    val symbol: String,
    val interval: ChartInterval,
    val insertedCount: Int,
    val totalStoredCount: Int,
    val sourceLabel: String,
    val isOfflineFallback: Boolean,
    val syncedAtMs: Long = System.currentTimeMillis()
)
