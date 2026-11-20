package com.example.niftychart.data.remote

import com.example.data.MarketChartDataRepository
import com.example.model.DateRangePreset
import com.example.model.MarketSymbol
import com.example.model.NseWatchlistCatalog
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic NIFTY 50 sample OHLCV generator and catalog used for Compose Previews,
 * unit testing, and immediate offline bootstrapping prior to first remote sync.
 */
object SampleNiftyDataCatalog {

    fun generateSampleCandles(
        symbol: String = OHLCV.SYMBOL_NIFTY_50,
        interval: ChartInterval = ChartInterval.M15,
        count: Int = 240,
        anchorTimestampMs: Long = 1_763_100_000_000L,
        basePrice: Double = 24820.0
    ): List<OHLCV> {
        val safeCount = count.coerceIn(10, 600)
        val stepMs = interval.intervalMs
        val alignedEnd = (anchorTimestampMs / stepMs) * stepMs
        val startMs = alignedEnd - (safeCount - 1) * stepMs

        val out = ArrayList<OHLCV>(safeCount)
        var running = basePrice
        for (i in 0 until safeCount) {
            val ts = startMs + i * stepMs
            val trend = i * 2.15
            val primaryWave = sin(i / 6.5) * 48.0
            val secondaryWave = cos(i / 3.2) * 19.0
            val close = (basePrice + trend + primaryWave + secondaryWave).coerceAtLeast(100.0)
            val open = running
            val spread = 14.0 + (i % 7) * 4.5 + abs(close - open) * 0.35
            val high = max(open, close) + spread * 0.6
            val low = min(open, close) - spread * 0.5
            val volume = 42_000L + (i % 13) * 8_400L + (abs(close - open) * 320).toLong()

            out.add(
                OHLCV(
                    symbol = symbol,
                    timestamp = ts,
                    interval = interval,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    createdAt = ts,
                    updatedAt = ts
                )
            )
            running = close
        }
        return out
    }
}

/**
 * Offline demonstration provider producing realistic NIFTY 50 historical candles and simulated
 * tick updates. Used when offline, in Compose previews, or until a licensed feed is connected.
 */
class DemoTickMarketDataProvider(
    private val tickIntervalMs: Long = 2_000L,
    private val initialAnchorPrice: Double = 25144.30
) : MarketDataProvider {

    override val providerName: String = "Demo Tick Simulator (Offline Reference)"
    override val isSimulated: Boolean = true

    override suspend fun fetchHistoricalCandles(
        symbol: String,
        interval: ChartInterval,
        fromTimestampMs: Long?,
        toTimestampMs: Long
    ): Result<List<OHLCV>> {
        val bars = SampleNiftyDataCatalog.generateSampleCandles(
            symbol = symbol,
            interval = interval,
            count = 240,
            anchorTimestampMs = toTimestampMs,
            basePrice = initialAnchorPrice - 420.0
        )
        val filtered = if (fromTimestampMs != null) {
            bars.filter { it.timestamp > fromTimestampMs }
        } else {
            bars
        }
        return Result.success(filtered)
    }

    override fun streamMarketTicks(symbol: String): Flow<MarketTick> = flow {
        var price = initialAnchorPrice
        var step = 0
        while (true) {
            step++
            val wave = sin(step * 0.55) * 4.2 + cos(step * 0.21) * 2.4
            price = (price + wave).coerceAtLeast(1000.0)
            val vol = 1_250L + ((step * 317L) % 2_400L)
            emit(
                MarketTick(
                    symbol = symbol,
                    price = price,
                    volume = vol,
                    timestamp = System.currentTimeMillis(),
                    source = providerName
                )
            )
            delay(tickIntervalMs)
        }
    }
}

/**
 * Production data source coordinating live market feeds (INDstocks / Yahoo Finance Chart v8)
 * with retry backoff, graceful failure handling, and automatic fallback to [DemoTickMarketDataProvider].
 */
class HybridNiftyMarketDataSource(
    private val chartDataRepo: MarketChartDataRepository = MarketChartDataRepository(),
    private val fallbackProvider: MarketDataProvider = DemoTickMarketDataProvider(),
    private val indstocksTokenProvider: () -> String = { "" },
    private val licensedProvider: LicensedRealTimeMarketDataProvider? = null
) : MarketDataProvider {

    override val providerName: String
        get() = licensedProvider?.providerName ?: "Yahoo Finance / INDstocks Live + Offline Fallback"

    override val isSimulated: Boolean
        get() = false

    override suspend fun fetchHistoricalCandles(
        symbol: String,
        interval: ChartInterval,
        fromTimestampMs: Long?,
        toTimestampMs: Long
    ): Result<List<OHLCV>> {
        // 1. Prefer injected licensed real-time NSE provider if configured
        licensedProvider?.let { licensed ->
            val licensedResult = licensed.fetchHistoricalCandles(symbol, interval, fromTimestampMs, toTimestampMs)
            if (licensedResult.isSuccess && licensedResult.getOrNull().orEmpty().isNotEmpty()) {
                return licensedResult
            }
        }

        // 2. Query live INDstocks / Yahoo Finance feed
        val marketSymbol = resolveMarketSymbol(symbol)
        val dateRange = when (interval) {
            ChartInterval.M1 -> DateRangePreset.D5
            ChartInterval.M5 -> DateRangePreset.D5
            ChartInterval.M15 -> DateRangePreset.M1
            ChartInterval.M30 -> DateRangePreset.M1
            ChartInterval.H1 -> DateRangePreset.M3
            ChartInterval.D1 -> DateRangePreset.Y1
        }

        val liveRes = runCatching {
            chartDataRepo.fetchCandlesForSymbol(
                symbol = marketSymbol,
                timeframe = interval.toAppTimeframe(),
                dateRange = dateRange,
                indstocksToken = indstocksTokenProvider(),
                isAdjusted = true
            )
        }.getOrNull()

        if (liveRes != null && liveRes.candles.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val domainCandles = liveRes.candles.map { legacy ->
                OHLCV.fromLegacyCandle(
                    candle = legacy,
                    symbol = symbol,
                    interval = interval,
                    nowMs = now
                )
            }.filter { candle ->
                candle.isValid() && (fromTimestampMs == null || candle.timestamp >= fromTimestampMs)
            }
            if (domainCandles.isNotEmpty()) {
                return Result.success(domainCandles)
            }
        }

        // 3. Network unavailable or empty -> return failure so repository keeps Room cache or seeds demo
        return Result.failure(
            IllegalStateException(liveRes?.errorMessage ?: "Live market feed unavailable for $symbol (${interval.code})")
        )
    }

    override fun streamMarketTicks(symbol: String): Flow<MarketTick> {
        licensedProvider?.let { licensed ->
            return licensed.streamMarketTicks(symbol).retryWhen { _, attempt ->
                if (attempt < 5) {
                    delay((attempt + 1) * 1_500L)
                    true
                } else {
                    false
                }
            }
        }

        val marketSymbol = resolveMarketSymbol(symbol)
        return flow {
            var lastEmittedPrice = 0.0
            while (true) {
                val snap = runCatching {
                    chartDataRepo.fetchCandlesForSymbol(
                        symbol = marketSymbol,
                        timeframe = com.example.model.Timeframe.M1,
                        dateRange = DateRangePreset.D1,
                        indstocksToken = indstocksTokenProvider()
                    )
                }.getOrNull()

                val latest = snap?.candles?.lastOrNull()
                if (latest != null && latest.close > 0.0) {
                    val price = if (snap.spotPrice > 0.0) snap.spotPrice else latest.close
                    lastEmittedPrice = price
                    emit(
                        MarketTick(
                            symbol = symbol,
                            price = price,
                            volume = latest.volume.coerceAtLeast(1L),
                            timestamp = System.currentTimeMillis(),
                            source = snap.freshness.sourceLabel
                        )
                    )
                } else if (lastEmittedPrice > 0.0) {
                    // Emit heartbeat price when briefly disconnected without throwing away state
                    emit(
                        MarketTick(
                            symbol = symbol,
                            price = lastEmittedPrice,
                            volume = 0L,
                            timestamp = System.currentTimeMillis(),
                            source = "Cached Heartbeat"
                        )
                    )
                } else {
                    throw IllegalStateException("Stream feed unreachable")
                }
                delay(5_000L)
            }
        }.retryWhen { _, attempt ->
            if (attempt < 3) {
                delay(1_000L * (attempt + 1))
                true
            } else {
                false
            }
        }
    }

    fun fallbackDemoProvider(): MarketDataProvider = fallbackProvider

    private fun resolveMarketSymbol(symbol: String): MarketSymbol {
        return NseWatchlistCatalog.defaultSymbols.firstOrNull {
            it.symbol.equals(symbol, ignoreCase = true)
        } ?: NseWatchlistCatalog.normalizeCustomNseSymbol(symbol)
    }
}
