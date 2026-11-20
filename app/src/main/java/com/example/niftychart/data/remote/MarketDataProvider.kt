package com.example.niftychart.data.remote

import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import kotlinx.coroutines.flow.Flow

/**
 * Provider abstraction decoupling market data transport (Yahoo Finance Chart v8, INDstocks REST/WS,
 * licensed exchange feeds, or offline simulation) from the Room-backed repository layer.
 *
 * LICENSED REAL-TIME INTEGRATION HOOK:
 * To connect an authorized real-time NIFTY 50 vendor (e.g., NSE CM/F&O WebSocket feed, TrueData,
 * GlobalDatafeeds, Kite Connect Ticker, or INDstocks live feed):
 * 1. Implement [LicensedRealTimeMarketDataProvider] with your vendor WebSocket/REST client.
 * 2. Supply your API credentials via the Secrets panel (`BuildConfig`) or authenticated broker session.
 * 3. Inject that implementation into [HybridNiftyMarketDataSource] or directly via `RepositoryModule`.
 */
interface MarketDataProvider {
    val providerName: String
    val isSimulated: Boolean

    /**
     * Fetches historical OHLCV bars in ascending chronological order.
     * Supports incremental synchronization by filtering from [fromTimestampMs] when provided.
     */
    suspend fun fetchHistoricalCandles(
        symbol: String,
        interval: ChartInterval,
        fromTimestampMs: Long? = null,
        toTimestampMs: Long = System.currentTimeMillis()
    ): Result<List<OHLCV>>

    /**
     * Emits incoming price/volume ticks for real-time candlestick aggregation.
     */
    fun streamMarketTicks(symbol: String = OHLCV.SYMBOL_NIFTY_50): Flow<MarketTick>
}

/**
 * Marker interface identifying a production/licensed real-time NSE NIFTY 50 exchange or broker
 * data adapter. Implement this interface when plugging in a paid vendor WebSocket/REST client.
 */
interface LicensedRealTimeMarketDataProvider : MarketDataProvider {
    override val isSimulated: Boolean get() = false
}
