package com.example.data

import com.example.engine.chart.TimeframeAggregationEngine
import com.example.model.Candle
import com.example.model.DataFreshnessInfo
import com.example.model.DateRangePreset
import com.example.model.MarketSymbol
import com.example.model.Timeframe
import com.example.network.IndstocksApiClient
import com.example.network.LiveMarketDataClient
import com.example.niftychart.data.local.NiftyOHLCVDao
import com.example.niftychart.data.local.NiftyOHLCVEntity
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class SymbolChartFetchResult(
    val symbol: MarketSymbol,
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val spotPrice: Double,
    val prevClose: Double,
    val freshness: DataFreshnessInfo,
    val errorMessage: String? = null
)

/**
 * Coordinates verified multi-symbol OHLCV fetching across INDstocks (when broker is connected
 * and scrip code is mapped) and Yahoo Finance Chart v8, persisting fetched candles into Room
 * ([NiftyOHLCVDao]) for offline-first resilience while never fabricating missing/holiday bars.
 */
class MarketChartDataRepository(
    private val liveMarketClient: LiveMarketDataClient = LiveMarketDataClient(),
    private val indstocksClient: IndstocksApiClient = IndstocksApiClient(),
    private val ohlcvDao: NiftyOHLCVDao? = null
) {
    suspend fun fetchCandlesForSymbol(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        dateRange: DateRangePreset,
        indstocksToken: String,
        isAdjusted: Boolean = true
    ): SymbolChartFetchResult = withContext(Dispatchers.IO) {
        val baseFetchTf = when (timeframe) {
            Timeframe.M10 -> Timeframe.M5
            Timeframe.M45 -> Timeframe.M15
            Timeframe.H2, Timeframe.H4 -> Timeframe.H1
            else -> timeframe
        }

        // Priority 1: INDstocks verified historical endpoint when token + scripCode are available
        val cleanToken = IndstocksApiClient.sanitizeToken(indstocksToken)
        val scripCode = symbol.indstocksScripCode?.takeIf { it.isNotBlank() }
        if (cleanToken.isNotBlank() && scripCode != null) {
            indstocksClient.fetchHistoricalCandles(
                accessToken = cleanToken,
                scripCode = scripCode,
                timeframe = baseFetchTf
            ).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { raw ->
                    TimeframeAggregationEngine.aggregateCandlesForTimeframe(raw, timeframe)
                        .takeIf { it.isNotEmpty() }
                        ?.also { persistCandlesToRoom(symbol.symbol, timeframe, it) }
                        ?.let { aggregated ->
                            val last = aggregated.last()
                            val hasGaps = TimeframeAggregationEngine.detectHolidayOrSessionGaps(aggregated, timeframe)
                            return@withContext SymbolChartFetchResult(
                                symbol = symbol,
                                timeframe = timeframe,
                                candles = aggregated,
                                spotPrice = last.close,
                                prevClose = aggregated.first().open,
                                freshness = DataFreshnessInfo(
                                    sourceLabel = "INDstocks Live (${symbol.symbol})",
                                    isRealTime = true,
                                    isAdjusted = isAdjusted,
                                    lastUpdatedEpochMs = last.timestamp,
                                    hasHolidayGaps = hasGaps,
                                    gapNote = if (hasGaps) "NSE session/holiday gaps preserved" else null
                                )
                            )
                        }
                }
        }

        // Priority 2: Yahoo Finance Chart v8 (supports both NIFTY indices & NSE stocks)
        val yahooRangeOverride = effectiveYahooRange(timeframe, dateRange)
        val yahooRes = liveMarketClient.fetchYahooChart(
            symbolEncoded = symbol.yahooTicker,
            timeframe = baseFetchTf,
            overrideRange = yahooRangeOverride,
            maxCandles = 320
        )

        yahooRes.getOrNull()
            ?.takeIf { it.candles.isNotEmpty() }
            ?.let { quote ->
                val aggregated = TimeframeAggregationEngine.aggregateCandlesForTimeframe(quote.candles, timeframe)
                    .also { persistCandlesToRoom(symbol.symbol, timeframe, it) }
                val hasGaps = TimeframeAggregationEngine.detectHolidayOrSessionGaps(aggregated, timeframe)
                return@withContext SymbolChartFetchResult(
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = aggregated,
                    spotPrice = quote.spotPrice,
                    prevClose = quote.prevClose,
                    freshness = DataFreshnessInfo(
                        sourceLabel = "Yahoo Finance (${symbol.symbol})",
                        isRealTime = MarketHoursEngine.getMarketSessionStatus(false).isExchangeOpen,
                        isAdjusted = isAdjusted,
                        lastUpdatedEpochMs = quote.marketTimestampSec * 1000L,
                        hasHolidayGaps = hasGaps,
                        gapNote = if (hasGaps) "Exchange holidays/gaps preserved (no fabricated bars)" else null
                    )
                )
            }

        // Priority 3: Offline-first Room SQLite OHLCV cache fallback
        val roomInterval = ChartInterval.fromTimeframe(timeframe)
        runCatching {
            ohlcvDao?.getLatestNCandles(symbol.symbol, roomInterval.code, 300).orEmpty()
        }.getOrDefault(emptyList())
            .takeIf { it.isNotEmpty() }
            ?.let { cachedEntities ->
                val cachedCandles = cachedEntities.map { it.toDomain().toLegacyCandle() }
                val last = cachedCandles.last()
                return@withContext SymbolChartFetchResult(
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = cachedCandles,
                    spotPrice = last.close,
                    prevClose = cachedCandles.first().open,
                    freshness = DataFreshnessInfo(
                        sourceLabel = "Room Offline Cache (${symbol.symbol})",
                        isRealTime = false,
                        isAdjusted = isAdjusted,
                        lastUpdatedEpochMs = last.timestamp,
                        hasHolidayGaps = false,
                        gapNote = "Showing persisted offline Room history"
                    )
                )
            }

        val err = yahooRes.exceptionOrNull()?.message ?: "Market feed unreachable for ${symbol.symbol}"
        SymbolChartFetchResult(
            symbol = symbol,
            timeframe = timeframe,
            candles = emptyList(),
            spotPrice = 0.0,
            prevClose = 0.0,
            freshness = DataFreshnessInfo(
                sourceLabel = "Unavailable (${symbol.symbol})",
                isRealTime = false,
                isAdjusted = isAdjusted,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                hasHolidayGaps = false,
                gapNote = err
            ),
            errorMessage = err
        )
    }

    private suspend fun persistCandlesToRoom(
        symbol: String,
        timeframe: Timeframe,
        candles: List<Candle>
    ) {
        val dao = ohlcvDao ?: return
        val interval = ChartInterval.fromTimeframe(timeframe)
        val now = System.currentTimeMillis()
        candles.mapNotNull { candle ->
            OHLCV.fromLegacyCandle(candle, symbol, interval, now)
                .takeIf { it.isValid() }
                ?.let(NiftyOHLCVEntity::fromDomain)
        }.takeIf { it.isNotEmpty() }?.let { entities ->
            runCatching { dao.upsertCandlesDeduplicated(entities) }
        }
    }

    suspend fun fetchMultiTimeframeCandles(
        symbol: MarketSymbol,
        indstocksToken: String,
        currentTf: Timeframe,
        currentCandles: List<Candle>
    ): Map<Timeframe, List<Candle>> = coroutineScope {
        val mtfTargets = listOf(Timeframe.DAILY, Timeframe.H1, Timeframe.M15)
        val results = mtfTargets.associateWith { tf ->
            async {
                if (tf == currentTf && currentCandles.isNotEmpty()) {
                    currentCandles
                } else {
                    fetchCandlesForSymbol(
                        symbol = symbol,
                        timeframe = tf,
                        dateRange = if (tf == Timeframe.DAILY) DateRangePreset.M6 else DateRangePreset.M1,
                        indstocksToken = indstocksToken
                    ).candles
                }
            }
        }

        LinkedHashMap<Timeframe, List<Candle>>().apply {
            results.forEach { (tf, def) ->
                def.await().takeIf { it.isNotEmpty() }?.let { put(tf, it) }
            }
            this[Timeframe.H1]?.let { h1List ->
                TimeframeAggregationEngine.aggregateCandlesForTimeframe(h1List, Timeframe.H4)
                    .takeIf { it.isNotEmpty() }
                    ?.let { put(Timeframe.H4, it) }
            }
        }
    }

    private fun effectiveYahooRange(timeframe: Timeframe, preset: DateRangePreset): String {
        return when {
            timeframe.minutes <= 3 && preset.lookbackDays > 5 -> "5d"
            timeframe.minutes <= 15 && preset.lookbackDays > 30 -> "1mo"
            timeframe.minutes <= 240 && preset.lookbackDays > 90 -> "3mo"
            else -> preset.yahooRange
        }
    }
}
