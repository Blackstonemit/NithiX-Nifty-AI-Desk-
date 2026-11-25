package com.example.niftychart.data.repository

import com.example.niftychart.data.local.NiftyOHLCVDao
import com.example.niftychart.data.local.NiftyOHLCVEntity
import com.example.niftychart.data.remote.DemoTickMarketDataProvider
import com.example.niftychart.data.remote.HybridNiftyMarketDataSource
import com.example.niftychart.data.remote.MarketDataProvider
import com.example.niftychart.data.remote.SampleNiftyDataCatalog
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.FeedConnectionStatus
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SyncSummary
import com.example.niftychart.domain.repository.NiftyMarketRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic tick-to-candle aggregator.
 * Aligns ticks onto [ChartInterval] bucket timestamps and tracks both the completed candle (when a
 * rollover boundary is crossed) and the updated active candle without creating duplicate rows.
 */
class CandleTickAggregator {

    data class AggregationOutcome(
        val completedCandle: OHLCV? = null,
        val activeCandle: OHLCV
    )

    fun aggregate(
        currentCandle: OHLCV?,
        tick: MarketTick,
        interval: ChartInterval
    ): AggregationOutcome {
        require(tick.isValid()) { "Invalid MarketTick: $tick" }
        val bucketTs = alignTimestampToInterval(tick.timestamp, interval)
        val now = tick.timestamp

        if (currentCandle == null || currentCandle.symbol != tick.symbol || currentCandle.interval != interval) {
            return AggregationOutcome(
                completedCandle = null,
                activeCandle = OHLCV(
                    symbol = tick.symbol,
                    timestamp = bucketTs,
                    interval = interval,
                    open = tick.price,
                    high = tick.price,
                    low = tick.price,
                    close = tick.price,
                    volume = tick.volume,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        return when {
            bucketTs == currentCandle.timestamp -> {
                val updated = currentCandle.copy(
                    high = max(currentCandle.high, tick.price),
                    low = min(currentCandle.low, tick.price),
                    close = tick.price,
                    volume = currentCandle.volume + tick.volume,
                    updatedAt = now
                )
                AggregationOutcome(completedCandle = null, activeCandle = updated)
            }
            bucketTs > currentCandle.timestamp -> {
                val finalized = currentCandle.copy(updatedAt = now)
                val next = OHLCV(
                    symbol = tick.symbol,
                    timestamp = bucketTs,
                    interval = interval,
                    open = tick.price,
                    high = tick.price,
                    low = tick.price,
                    close = tick.price,
                    volume = tick.volume,
                    createdAt = now,
                    updatedAt = now
                )
                AggregationOutcome(completedCandle = finalized, activeCandle = next)
            }
            else -> {
                // Out-of-order stale tick predating active bucket: keep current active candle intact
                AggregationOutcome(completedCandle = null, activeCandle = currentCandle)
            }
        }
    }

    companion object {
        fun alignTimestampToInterval(timestampMs: Long, interval: ChartInterval): Long {
            val step = interval.intervalMs
            return (timestampMs / step) * step
        }
    }
}

/**
 * Offline-first repository implementation coordinating Room SQLite storage via [NiftyOHLCVDao],
 * remote/simulated [MarketDataProvider], thread-safe synchronization, and tick aggregation.
 */
class NiftyMarketRepositoryImpl(
    private val dao: NiftyOHLCVDao,
    private val remoteProvider: MarketDataProvider,
    private val demoProvider: MarketDataProvider = DemoTickMarketDataProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NiftyMarketRepository {

    private val writeMutex = Mutex()
    private val aggregator = CandleTickAggregator()
    private val activeCandles = HashMap<Pair<String, ChartInterval>, OHLCV>()

    private val _connectionStatus = MutableStateFlow(FeedConnectionStatus.OFFLINE)
    override val connectionStatus: Flow<FeedConnectionStatus> = _connectionStatus

    override fun observeLatestCandles(
        symbol: String,
        interval: ChartInterval,
        limit: Int
    ): Flow<List<OHLCV>> = dao.observeLatestNCandles(symbol, interval.code, limit.coerceAtLeast(1))
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    override fun observeCandlesByInterval(
        symbol: String,
        interval: ChartInterval
    ): Flow<List<OHLCV>> = dao.observeCandlesByInterval(symbol, interval.code)
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    override fun observeHistoricalRange(
        symbol: String,
        interval: ChartInterval,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<OHLCV>> = dao.observeCandlesInRange(symbol, interval.code, startTimestamp, endTimestamp)
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    override fun observeLatestPrice(symbol: String): Flow<OHLCV?> = dao.observeLatestPrice(symbol)
        .map { it?.toDomain() }
        .flowOn(ioDispatcher)

    override suspend fun getLatestPrice(symbol: String): OHLCV? = withContext(ioDispatcher) {
        dao.getLatestPrice(symbol)?.toDomain()
    }

    override suspend fun syncHistoricalCandles(
        symbol: String,
        interval: ChartInterval,
        forceRefresh: Boolean
    ): Result<SyncSummary> = withContext(ioDispatcher) {
        val latestStoredTs = if (forceRefresh) null else dao.getLatestTimestamp(symbol, interval.code)
        val remoteResult = remoteProvider.fetchHistoricalCandles(
            symbol = symbol,
            interval = interval,
            fromTimestampMs = latestStoredTs
        )

        remoteResult.getOrNull()?.let { raw ->
            val fetched = raw.filter { it.isValid() }
            val changedCount = writeMutex.withLock {
                dao.upsertCandlesDeduplicated(fetched.map(NiftyOHLCVEntity::fromDomain))
            }
            val totalCount = dao.getCandleCount(symbol, interval.code)
            _connectionStatus.value = if (remoteProvider.isSimulated) {
                FeedConnectionStatus.SIMULATED
            } else {
                FeedConnectionStatus.LIVE
            }
            return@withContext Result.success(
                SyncSummary(
                    symbol = symbol,
                    interval = interval,
                    insertedCount = changedCount,
                    totalStoredCount = totalCount,
                    sourceLabel = remoteProvider.providerName,
                    isOfflineFallback = false
                )
            )
        }

        // Network failure: preserve existing offline Room cache; bootstrap sample data only if empty
        val existingCount = dao.getCandleCount(symbol, interval.code)
        if (existingCount == 0) {
            val fallbackProvider = (remoteProvider as? HybridNiftyMarketDataSource)?.fallbackDemoProvider() ?: demoProvider
            val seededCount = (fallbackProvider.fetchHistoricalCandles(symbol, interval).getOrNull()
                ?: SampleNiftyDataCatalog.generateSampleCandles(symbol, interval))
                .let { demoCandles ->
                    writeMutex.withLock {
                        dao.upsertCandlesDeduplicated(demoCandles.map(NiftyOHLCVEntity::fromDomain))
                    }
                }
            val total = dao.getCandleCount(symbol, interval.code)
            _connectionStatus.value = FeedConnectionStatus.SIMULATED
            return@withContext Result.success(
                SyncSummary(
                    symbol = symbol,
                    interval = interval,
                    insertedCount = seededCount,
                    totalStoredCount = total,
                    sourceLabel = "${fallbackProvider.providerName} (Offline Seed)",
                    isOfflineFallback = true
                )
            )
        }

        _connectionStatus.value = FeedConnectionStatus.OFFLINE
        Result.success(
            SyncSummary(
                symbol = symbol,
                interval = interval,
                insertedCount = 0,
                totalStoredCount = existingCount,
                sourceLabel = "Room Offline Cache (${remoteResult.exceptionOrNull()?.message ?: "Offline"})",
                isOfflineFallback = true
            )
        )
    }

    override suspend fun upsertCandles(candles: List<OHLCV>) {
        withContext(ioDispatcher) {
            candles.filter { it.isValid() }.takeIf { it.isNotEmpty() }?.let { valid ->
                writeMutex.withLock {
                    dao.upsertCandlesDeduplicated(valid.map(NiftyOHLCVEntity::fromDomain))
                }
            }
        }
    }

    override suspend fun processMarketTick(
        tick: MarketTick,
        interval: ChartInterval
    ): OHLCV? = withContext(ioDispatcher) {
        if (!tick.isValid()) return@withContext null
        val key = tick.symbol to interval

        writeMutex.withLock {
            val existingLatest = activeCandles[key]
                ?: dao.getLatestNCandles(tick.symbol, interval.code, 1).lastOrNull()?.toDomain()

            aggregator.aggregate(existingLatest, tick, interval).also { aggregated ->
                activeCandles[key] = aggregated.activeCandle
                // Persist completed bucket on interval rollover AND checkpoint current forming candle
                aggregated.completedCandle?.let { completed ->
                    dao.upsertCandleDeduplicated(NiftyOHLCVEntity.fromDomain(completed))
                }
                dao.upsertCandleDeduplicated(NiftyOHLCVEntity.fromDomain(aggregated.activeCandle))
            }.activeCandle
        }
    }

    override fun streamRealTimeCandles(
        symbol: String,
        interval: ChartInterval
    ): Flow<OHLCV?> = flow {
        var backoffMs = 300L
        var simulatedFallbackCandle: OHLCV? = null
        while (true) {
            var hadError = false
            val streamFlow = remoteProvider.streamMarketTicks(symbol)
                .catch {
                    hadError = true
                    _connectionStatus.value = FeedConnectionStatus.RECONNECTING
                }

            emitAll(
                streamFlow.map { tick ->
                    _connectionStatus.value = if (remoteProvider.isSimulated) {
                        FeedConnectionStatus.SIMULATED
                    } else {
                        FeedConnectionStatus.LIVE
                    }
                    backoffMs = 300L
                    processMarketTick(tick, interval)
                }
            )

            if (hadError) {
                // While remote stream recovers, emit bounded fallback ticks without overwriting live candle timestamps
                _connectionStatus.value = FeedConnectionStatus.SIMULATED
                emitAll(
                    demoProvider.streamMarketTicks(symbol)
                        .take(2)
                        .catch { _connectionStatus.value = FeedConnectionStatus.OFFLINE }
                        .map { tick ->
                            if (remoteProvider.isSimulated) {
                                processMarketTick(tick, interval)
                            } else if (!tick.isValid()) {
                                null
                            } else {
                                val outcome = aggregator.aggregate(simulatedFallbackCandle, tick, interval)
                                simulatedFallbackCandle = outcome.activeCandle
                                outcome.activeCandle
                            }
                        }
                )
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2L).coerceAtMost(15_000L)
        }
    }.flowOn(ioDispatcher)

    override suspend fun deleteOutdatedRecords(olderThanTimestamp: Long): Int = withContext(ioDispatcher) {
        writeMutex.withLock {
            dao.deleteOutdatedRecords(olderThanTimestamp)
        }
    }
}
