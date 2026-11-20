package com.example.niftychart.domain.repository

import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.FeedConnectionStatus
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SyncSummary
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for offline-first NIFTY 50 OHLCV storage, incremental synchronization,
 * and tick-to-candle streaming updates.
 */
interface NiftyMarketRepository {

    val connectionStatus: Flow<FeedConnectionStatus>

    fun observeLatestCandles(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        limit: Int = 300
    ): Flow<List<OHLCV>>

    fun observeCandlesByInterval(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval
    ): Flow<List<OHLCV>>

    fun observeHistoricalRange(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<OHLCV>>

    fun observeLatestPrice(symbol: String = OHLCV.SYMBOL_NIFTY_50): Flow<OHLCV?>

    suspend fun getLatestPrice(symbol: String = OHLCV.SYMBOL_NIFTY_50): OHLCV?

    suspend fun syncHistoricalCandles(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        forceRefresh: Boolean = false
    ): Result<SyncSummary>

    suspend fun upsertCandles(candles: List<OHLCV>)

    suspend fun processMarketTick(
        tick: MarketTick,
        interval: ChartInterval
    ): OHLCV?

    fun streamRealTimeCandles(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval
    ): Flow<OHLCV?>

    suspend fun deleteOutdatedRecords(olderThanTimestamp: Long): Int
}
