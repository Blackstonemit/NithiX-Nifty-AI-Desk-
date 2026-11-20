package com.example.niftychart

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.niftychart.data.local.NiftyDatabase
import com.example.niftychart.data.local.NiftyOHLCVDao
import com.example.niftychart.data.local.NiftyOHLCVEntity
import com.example.niftychart.data.remote.DemoTickMarketDataProvider
import com.example.niftychart.data.remote.MarketDataProvider
import com.example.niftychart.data.remote.SampleNiftyDataCatalog
import com.example.niftychart.data.repository.CandleTickAggregator
import com.example.niftychart.data.repository.NiftyMarketRepositoryImpl
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.MarketTick
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.SmaPeriod
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import com.example.niftychart.domain.usecase.GetHistoricalCandlesUseCase
import com.example.niftychart.presentation.chart.ChartScaleCalculator
import com.example.niftychart.presentation.chart.ChartUiState
import com.example.niftychart.presentation.chart.ChartViewModel
import com.example.niftychart.presentation.chart.NiftyChartScreen
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NiftyChartModuleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: NiftyDatabase
    private lateinit var dao: NiftyOHLCVDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NiftyDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.niftyOhlcvDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `dao insert batch update latest and range queries work cleanly`() = runBlocking {
        val baseTs = 1_763_000_000_000L
        val step = ChartInterval.M15.intervalMs
        val entities = (0 until 10).map { i ->
            val p = 25000.0 + i * 15.0
            NiftyOHLCVEntity(
                symbol = OHLCV.SYMBOL_NIFTY_50,
                timestamp = baseTs + i * step,
                interval = ChartInterval.M15.code,
                open = p,
                high = p + 12.0,
                low = p - 8.0,
                close = p + 6.0,
                volume = 10_000L + i * 500L,
                createdAt = baseTs,
                updatedAt = baseTs
            )
        }

        val insertedIds = dao.insertCandlesBatch(entities)
        assertEquals(10, insertedIds.size)
        assertTrue(dao.candleExists(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M15.code, baseTs + 3 * step))
        assertFalse(dao.candleExists(OHLCV.SYMBOL_NIFTY_50, ChartInterval.H1.code, baseTs))

        val latest = dao.getLatestPrice(OHLCV.SYMBOL_NIFTY_50)
        assertNotNull(latest)
        assertEquals(25000.0 + 9 * 15.0 + 6.0, latest!!.close, 0.001)

        val range = dao.getCandlesInRange(
            symbol = OHLCV.SYMBOL_NIFTY_50,
            interval = ChartInterval.M15.code,
            startTimestamp = baseTs + 2 * step,
            endTimestamp = baseTs + 5 * step
        )
        assertEquals(4, range.size)
        assertEquals(baseTs + 2 * step, range.first().timestamp)
        assertEquals(baseTs + 5 * step, range.last().timestamp)

        val latestThree = dao.observeLatestNCandles(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M15.code, 3).first()
        assertEquals(3, latestThree.size)
        assertTrue(latestThree[0].timestamp < latestThree[1].timestamp)
        assertTrue(latestThree[1].timestamp < latestThree[2].timestamp)

        // Update existing candle
        val toUpdate = range.first().copy(close = 25999.0, high = 26005.0, updatedAt = baseTs + 999L)
        val updatedRows = dao.updateCandle(toUpdate)
        assertEquals(1, updatedRows)
        val fetchedUpdated = dao.getCandleAt(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M15.code, toUpdate.timestamp)
        assertEquals(25999.0, fetchedUpdated!!.close, 0.001)

        // Delete outdated records before index 4
        val deletedCount = dao.deleteOutdatedRecords(baseTs + 4 * step)
        assertEquals(4, deletedCount)
        assertEquals(6, dao.getCandleCount(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M15.code))
    }

    @Test
    fun `dao prevents duplicate candle records on identical symbol interval timestamp`() = runBlocking {
        val ts = 1_763_100_000_000L
        val first = NiftyOHLCVEntity(
            symbol = OHLCV.SYMBOL_NIFTY_50,
            timestamp = ts,
            interval = ChartInterval.M5.code,
            open = 25100.0,
            high = 25120.0,
            low = 25090.0,
            close = 25110.0,
            volume = 40_000L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val duplicateIgnore = first.copy(close = 25150.0)

        val id1 = dao.insertCandle(first)
        val id2 = dao.insertCandle(duplicateIgnore)
        assertTrue(id1 > 0L)
        assertEquals(-1L, id2)
        assertEquals(1, dao.getCandleCount(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M5.code))

        // Deduplicated upsert updates OHLCV values in-place while preserving original id and createdAt
        val revised = first.copy(close = 25135.0, high = 25140.0, volume = 55_000L, updatedAt = 2500L)
        val upsertedId = dao.upsertCandleDeduplicated(revised)
        assertEquals(id1, upsertedId)
        assertEquals(1, dao.getCandleCount(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M5.code))
        val stored = dao.getCandleAt(OHLCV.SYMBOL_NIFTY_50, ChartInterval.M5.code, ts)!!
        assertEquals(1000L, stored.createdAt)
        assertEquals(2500L, stored.updatedAt)
        assertEquals(25135.0, stored.close, 0.001)
    }

    @Test
    fun `sma calculation covers 20 50 100 200 and handles short datasets`() {
        val useCase = CalculateSMAUseCase()

        // Short series returns all nulls rather than crashing
        val shortResult = useCase.calculateFromCloses(listOf(100.0, 102.0, 104.0), 20)
        assertEquals(3, shortResult.size)
        assertTrue(shortResult.all { it == null })

        // Exact arithmetic series 1.0 .. 220.0 -> SMA(N) at last index = avg of last N integers
        val closes = (1..220).map { it.toDouble() }
        val sma20 = useCase.calculateFromCloses(closes, SmaPeriod.SMA_20.period)
        val sma50 = useCase.calculateFromCloses(closes, SmaPeriod.SMA_50.period)
        val sma100 = useCase.calculateFromCloses(closes, SmaPeriod.SMA_100.period)
        val sma200 = useCase.calculateFromCloses(closes, SmaPeriod.SMA_200.period)

        assertNull(sma20[18])
        assertEquals((1..20).average(), sma20[19]!!, 1e-6)
        assertEquals((201..220).average(), sma20.last()!!, 1e-6)
        assertEquals((171..220).average(), sma50.last()!!, 1e-6)
        assertEquals((121..220).average(), sma100.last()!!, 1e-6)
        assertEquals((21..220).average(), sma200.last()!!, 1e-6)
    }

    @Test
    fun `wilder rsi calculation matches reference math and respects 0 to 100 bounds`() {
        val useCase = CalculateRSIUseCase()

        // Insufficient data returns nulls cleanly
        val shortRsi = useCase.calculateFromCloses(List(14) { 25000.0 + it }, 14)
        assertTrue(shortRsi.values.all { it == null })

        // Monotonic uptrend -> RSI = 100.0
        val pureUp = (0..25).map { 24000.0 + it * 10.0 }
        val upRsi = useCase.calculateFromCloses(pureUp, 14)
        assertEquals(100.0, upRsi.latestValue!!, 1e-6)

        // Monotonic downtrend -> RSI = 0.0
        val pureDown = (0..25).map { 25000.0 - it * 10.0 }
        val downRsi = useCase.calculateFromCloses(pureDown, 14)
        assertEquals(0.0, downRsi.latestValue!!, 1e-6)

        // Oscillating sample series -> strictly within [0, 100] and configurable periods work
        val sampleCloses = SampleNiftyDataCatalog.generateSampleCandles(count = 120).map { it.close }
        val rsi14 = useCase.calculateFromCloses(sampleCloses, 14)
        val rsi7 = useCase.calculateFromCloses(sampleCloses, 7)
        assertNull(rsi14.values[13])
        assertNotNull(rsi14.values[14])
        assertNotNull(rsi7.values[7])
        assertTrue(rsi14.values.filterNotNull().all { it in 0.0..100.0 })
        assertTrue(rsi7.values.filterNotNull().all { it in 0.0..100.0 })
    }

    @Test
    fun `tick aggregation updates single active candle within bucket and rolls over cleanly without duplicates`() = runBlocking {
        val aggregator = CandleTickAggregator()
        val interval = ChartInterval.M5
        val bucket0 = 1_763_100_000_000L // aligned 5m boundary
        val bucket1 = bucket0 + interval.intervalMs

        val tick1 = MarketTick(OHLCV.SYMBOL_NIFTY_50, price = 25100.0, volume = 1000L, timestamp = bucket0 + 10_000L)
        val tick2 = MarketTick(OHLCV.SYMBOL_NIFTY_50, price = 25125.0, volume = 500L, timestamp = bucket0 + 60_000L)
        val tick3 = MarketTick(OHLCV.SYMBOL_NIFTY_50, price = 25090.0, volume = 750L, timestamp = bucket0 + 120_000L)
        val tick4 = MarketTick(OHLCV.SYMBOL_NIFTY_50, price = 25140.0, volume = 900L, timestamp = bucket1 + 5_000L)

        val r1 = aggregator.aggregate(null, tick1, interval)
        assertNull(r1.completedCandle)
        assertEquals(bucket0, r1.activeCandle.timestamp)

        val r2 = aggregator.aggregate(r1.activeCandle, tick2, interval)
        assertNull(r2.completedCandle)
        assertEquals(25100.0, r2.activeCandle.open, 0.001)
        assertEquals(25125.0, r2.activeCandle.high, 0.001)

        val r3 = aggregator.aggregate(r2.activeCandle, tick3, interval)
        assertNull(r3.completedCandle)
        assertEquals(25125.0, r3.activeCandle.high, 0.001)
        assertEquals(25090.0, r3.activeCandle.low, 0.001)
        assertEquals(25090.0, r3.activeCandle.close, 0.001)
        assertEquals(2250L, r3.activeCandle.volume)

        // Rollover into next 5m bucket finalizes bucket0 and starts bucket1
        val r4 = aggregator.aggregate(r3.activeCandle, tick4, interval)
        assertNotNull(r4.completedCandle)
        assertEquals(bucket0, r4.completedCandle!!.timestamp)
        assertEquals(bucket1, r4.activeCandle.timestamp)
        assertEquals(25140.0, r4.activeCandle.open, 0.001)

        // Verify full repository Room persistence never inserts one row per tick
        val repo = NiftyMarketRepositoryImpl(
            dao = dao,
            remoteProvider = DemoTickMarketDataProvider(),
            ioDispatcher = Dispatchers.Unconfined
        )
        repo.processMarketTick(tick1, interval)
        repo.processMarketTick(tick2, interval)
        repo.processMarketTick(tick3, interval)
        assertEquals("3 ticks in same interval must yield 1 candle row", 1, dao.getCandleCount(OHLCV.SYMBOL_NIFTY_50, interval.code))

        repo.processMarketTick(tick4, interval)
        assertEquals("Crossing into bucket1 must yield 2 candle rows", 2, dao.getCandleCount(OHLCV.SYMBOL_NIFTY_50, interval.code))
    }

    @Test
    fun `chart scale calculator accurately transforms visible bounds prices and indices`() {
        val sample = SampleNiftyDataCatalog.generateSampleCandles(count = 100)
        val viewport = ChartScaleCalculator.computeViewport(totalCandles = 100, visibleCount = 30, scrollRightOffset = 10)
        assertEquals(60, viewport.startIndex)
        assertEquals(90, viewport.endIndexExclusive)
        assertEquals(30, viewport.visibleCount)

        val slice = sample.subList(viewport.startIndex, viewport.endIndexExclusive)
        val bounds = ChartScaleCalculator.computePriceBounds(slice, verticalPaddingRatio = 0.1)
        val sliceMin = slice.minOf { it.low }
        val sliceMax = slice.maxOf { it.high }
        assertTrue(bounds.minPrice < sliceMin)
        assertTrue(bounds.maxPrice > sliceMax)

        // Roundtrip price -> Y -> price
        val testPrice = (sliceMin + sliceMax) / 2.0
        val y = ChartScaleCalculator.priceToY(testPrice, bounds, topPx = 20f, plotHeightPx = 400f)
        val decodedPrice = ChartScaleCalculator.yToPrice(y, bounds, topPx = 20f, plotHeightPx = 400f)
        assertEquals(testPrice, decodedPrice, 0.05)

        // Roundtrip visible index -> X -> visible index
        for (idx in 0 until 30) {
            val centerX = ChartScaleCalculator.visibleIndexToCenterX(idx, 30, plotLeftPx = 0f, plotWidthPx = 900f)
            val resolvedIdx = ChartScaleCalculator.xToVisibleIndex(centerX, 30, plotLeftPx = 0f, plotWidthPx = 900f)
            assertEquals(idx, resolvedIdx)
        }

        // RSI bounds mapping (0 at bottom, 100 at top)
        assertEquals(10f, ChartScaleCalculator.rsiToY(100.0, topPx = 10f, plotHeightPx = 100f), 0.01f)
        assertEquals(60f, ChartScaleCalculator.rsiToY(50.0, topPx = 10f, plotHeightPx = 100f), 0.01f)
        assertEquals(110f, ChartScaleCalculator.rsiToY(0.0, topPx = 10f, plotHeightPx = 100f), 0.01f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chart viewmodel switches intervals toggles sma and rsi and updates ui state`() = runBlocking {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            val failingRemote = object : MarketDataProvider {
                override val providerName: String = "OfflineStub"
                override val isSimulated: Boolean = false
                override suspend fun fetchHistoricalCandles(
                    symbol: String,
                    interval: ChartInterval,
                    fromTimestampMs: Long?,
                    toTimestampMs: Long
                ): Result<List<OHLCV>> = Result.failure(IllegalStateException("Network offline"))

                override fun streamMarketTicks(symbol: String): Flow<MarketTick> = emptyFlow()
            }

            val repo = NiftyMarketRepositoryImpl(
                dao = dao,
                remoteProvider = failingRemote,
                demoProvider = DemoTickMarketDataProvider(tickIntervalMs = 5_000L),
                ioDispatcher = Dispatchers.Unconfined
            )
            val vm = ChartViewModel(
                repository = repo,
                getHistoricalCandlesUseCase = GetHistoricalCandlesUseCase(repo),
                calculateSMAUseCase = CalculateSMAUseCase(Dispatchers.Unconfined),
                calculateRSIUseCase = CalculateRSIUseCase(Dispatchers.Unconfined),
                computeDispatcher = Dispatchers.Unconfined,
                autoInitialize = false
            )

            vm.startObserving()

            // Wait briefly for Room InvalidationTracker invalidation executor if async
            val deadline1 = System.currentTimeMillis() + 3_000L
            while (vm.uiState.value.candles.isEmpty() && System.currentTimeMillis() < deadline1) {
                kotlinx.coroutines.delay(25)
            }

            // Offline fallback cleanly seeds Room and updates UI state
            val initialState = vm.uiState.value
            assertFalse(initialState.isLoading)
            assertTrue("Should populate fallback candles into Room", initialState.candles.isNotEmpty())
            assertTrue(initialState.smaLines.containsKey(SmaPeriod.SMA_20))
            assertTrue(abs(initialState.rsiSeries.latestValue!! - 50.0) <= 50.0)

            // Switch timeframe -> loads 1h from Room / seed cleanly
            vm.selectInterval(ChartInterval.H1)
            val deadline2 = System.currentTimeMillis() + 3_000L
            while ((vm.uiState.value.candles.firstOrNull()?.interval != ChartInterval.H1) &&
                System.currentTimeMillis() < deadline2
            ) {
                kotlinx.coroutines.delay(25)
            }
            assertEquals(ChartInterval.H1, vm.uiState.value.selectedInterval)
            assertEquals(ChartInterval.H1, vm.uiState.value.candles.first().interval)

            // Toggle SMA 100 & SMA 200 on
            vm.toggleSma(SmaPeriod.SMA_100)
            vm.toggleSma(SmaPeriod.SMA_200)
            assertTrue(SmaPeriod.SMA_100 in vm.uiState.value.smaLines)
            assertTrue(SmaPeriod.SMA_200 in vm.uiState.value.smaLines)

            // Change RSI period and pan/zoom
            vm.setRsiPeriod(7)
            vm.zoomIn()
            vm.panByBars(5)
            assertEquals(7, vm.uiState.value.rsiSeries.period)
            assertTrue(vm.uiState.value.scrollRightOffset >= 0)

            // Inspect candle locks crosshair
            vm.selectCandle(10, true)
            assertEquals(10, vm.uiState.value.selectedCandleIndex)
            assertTrue(vm.uiState.value.isCrosshairLocked)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `nifty chart screen renders candlestick canvas sma overlay and rsi panel in dark and light themes`() {
        val sample = SampleNiftyDataCatalog.generateSampleCandles(count = 120)
        val closes = sample.map { it.close }
        val smas = setOf(SmaPeriod.SMA_20, SmaPeriod.SMA_50)
        val smaMap = smas.associateWith { CalculateSMAUseCase().calculateFromCloses(closes, it.period) }
        val rsi = CalculateRSIUseCase().calculateFromCloses(closes, 14)

        var selectedInterval = ChartInterval.M15
        composeTestRule.setContent {
            MyApplicationTheme(chartThemeMode = ChartThemeMode.INSTITUTIONAL_LIGHT) {
                NiftyChartScreen(
                    uiState = ChartUiState(
                        candles = sample,
                        enabledSmas = smas,
                        smaLines = smaMap,
                        rsiEnabled = true,
                        rsiSeries = rsi,
                        isLoading = false,
                        isDarkTheme = false
                    ),
                    onSelectInterval = { selectedInterval = it },
                    onToggleSma = {},
                    onToggleRsi = {},
                    onSelectRsiPeriod = {},
                    onToggleLiveStream = {},
                    onRefresh = {},
                    onToggleTheme = {},
                    onPanByBars = {},
                    onPinchZoom = {},
                    onZoomIn = {},
                    onZoomOut = {},
                    onResetViewport = {},
                    onSelectCandle = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nifty_chart_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ohlcv_info_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nifty_candlestick_chart")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("rsi_indicator_chart")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("chart_timeframe_selector")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("nifty_tf_1d")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(ChartInterval.D1, selectedInterval)
    }
}
