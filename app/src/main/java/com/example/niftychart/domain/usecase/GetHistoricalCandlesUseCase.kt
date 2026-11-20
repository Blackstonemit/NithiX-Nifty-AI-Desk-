package com.example.niftychart.domain.usecase

import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SyncSummary
import com.example.niftychart.domain.repository.NiftyMarketRepository
import kotlinx.coroutines.flow.Flow

/**
 * Coordinates reactive offline-first observation of Room-persisted OHLCV candles
 * and triggers incremental historical synchronization with the remote provider.
 */
class GetHistoricalCandlesUseCase(
    private val repository: NiftyMarketRepository
) {

    fun observe(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        limit: Int = 300
    ): Flow<List<OHLCV>> = repository.observeLatestCandles(
        symbol = symbol,
        interval = interval,
        limit = limit
    )

    fun observeRange(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<OHLCV>> = repository.observeHistoricalRange(
        symbol = symbol,
        interval = interval,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp
    )

    suspend fun sync(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval,
        forceRefresh: Boolean = false
    ): Result<SyncSummary> = repository.syncHistoricalCandles(
        symbol = symbol,
        interval = interval,
        forceRefresh = forceRefresh
    )
}
