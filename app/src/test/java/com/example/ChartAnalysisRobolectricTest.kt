package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.NiftyMarketRepository
import com.example.data.local.ChartWorkstationDatabase
import com.example.data.local.InMemoryChartSettingsStore
import com.example.data.local.RoomChartSettingsRepository
import com.example.data.local.SavedChartLayout
import com.example.engine.chart.IndicatorCalculationEngine
import com.example.engine.chart.MarketStructureEngine
import com.example.engine.chart.PatternDetectionEngine
import com.example.engine.chart.SignalRiskAndBacktestEngine
import com.example.engine.chart.SupportResistanceEngine
import com.example.engine.chart.TimeframeAggregationEngine
import com.example.engine.chart.VolumeAnalysisEngine
import com.example.model.BacktestConfig
import com.example.model.BacktestStrategyId
import com.example.model.Candle
import com.example.model.CandlestickPatternId
import com.example.model.ChartAnchorPoint
import com.example.model.ChartDrawing
import com.example.model.ChartType
import com.example.model.DrawingToolType
import com.example.model.IndicatorConfig
import com.example.model.IndicatorType
import com.example.model.LlmProviderConfig
import com.example.model.LlmProviderType
import com.example.model.MarketBias
import com.example.model.MarketSymbol
import com.example.model.PriceScaleMode
import com.example.model.Timeframe
import com.example.network.LiveMarketDataClient
import com.example.network.NseMcpClient
import com.example.ui.components.SettingsLlmAndThemeSection
import com.example.ui.screens.ChartWorkstationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChartWorkstationCoordinator
import com.example.ui.viewmodel.NiftyDeskViewModel
import com.example.ui.viewmodel.WorkstationInspectorTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChartAnalysisRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fastOfflineClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(200, TimeUnit.MILLISECONDS)
        .build()

    private fun createOfflineViewModel(): NiftyDeskViewModel {
        val client = fastOfflineClient()
        val repo = NiftyMarketRepository(
            liveMarketClient = LiveMarketDataClient(client, "http://127.0.0.1:1", "http://127.0.0.1:1"),
            nseMcpClient = NseMcpClient(client, "http://127.0.0.1:1", "http://127.0.0.1:1")
        )
        return NiftyDeskViewModel(
            repository = repo,
            chartSettingsStore = InMemoryChartSettingsStore()
        )
    }

    private fun syntheticTrendingCandles(count: Int = 80, step: Double = 6.0): List<Candle> {
        val baseTs = 1_740_000_000_000L
        var price = 24000.0
        return (0 until count).map { i ->
            val open = price
            val delta = if (i % 5 == 0) -step * 0.4 else step
            val close = open + delta
            val high = maxOf(open, close) + 12.0
            val low = minOf(open, close) - 10.0
            price = close
            Candle(
                timestamp = baseTs + i * 15 * 60_000L,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = 120_000L + (i % 7) * 18_000L,
                vwap = (open + close) / 2.0
            )
        }
    }

    @Test
    fun testAllQuantitativeIndicatorsComputeDeterministically() {
        val candles = syntheticTrendingCandles(count = 120, step = 8.0)
        val allTypes = IndicatorType.entries.mapIndexed { idx, type ->
            IndicatorConfig(
                id = "ind_${type.name.lowercase()}_$idx",
                type = type,
                enabled = true,
                period = type.defaultPeriod,
                secondaryPeriod = type.defaultSecondaryPeriod,
                tertiaryPeriod = type.defaultTertiaryPeriod,
                multiplier = type.defaultMultiplier
            )
        }

        val result = IndicatorCalculationEngine.computeAll(candles, allTypes)

        assertTrue("EMA overlay computed", result.overlayLines.values.any { it.size == candles.size })
        assertTrue("Bands (BB/KC/DC) computed", result.bands.size >= 3)
        assertNotNull("Ichimoku computed", result.ichimoku)
        assertEquals(candles.size, result.rsiSeries.size)
        assertTrue("RSI stays bounded [0,100]", result.rsiSeries.all { it in 0.0..100.0 })
        assertTrue("Bullish uptrend yields elevated RSI", result.rsiSeries.last() > 55.0)
        assertEquals(candles.size, result.macdSeries.first.size)
        assertEquals(candles.size, result.stochSeries.first.size)
        assertTrue("Stochastic %K bounded", result.stochSeries.first.all { it in 0.0..100.0 })
        assertEquals(candles.size, result.adxSeries.first.size)
        assertEquals(candles.size, result.obvSeries.size)
        assertEquals(candles.size, result.mfiSeries.size)
        assertTrue("MFI bounded [0,100]", result.mfiSeries.all { it in 0.0..100.0 })
        assertEquals(candles.size, result.cmfSeries.size)
        assertTrue("CMF bounded [-1,1]", result.cmfSeries.all { it in -1.0..1.0 })
        assertEquals(candles.size, result.cciSeries.size)
        assertEquals(candles.size, result.williamsRSeries.size)
        assertTrue("Williams %R bounded [-100,0]", result.williamsRSeries.all { it in -100.0..0.0 })
        assertEquals(candles.size, result.rocSeries.size)
        assertEquals(candles.size, result.atrSeries.size)
        assertTrue("ATR positive", result.atrSeries.last() > 0.0)
        assertEquals(candles.size, result.supertrendSeries.size)
        assertTrue("Supertrend bullish in strong uptrend", result.supertrendSeries.last().second)
    }

    @Test
    fun testCandlestickPatternRecognitionEngineDetectsKeyFormations() {
        val baseTs = 1_740_000_000_000L
        val candles = mutableListOf<Candle>()
        // 1. Establish downtrend
        var p = 24800.0
        for (i in 0 until 20) {
            candles.add(Candle(baseTs + i * 60_000L, p, p + 5.0, p - 25.0, p - 20.0, 50_000L, p - 10.0))
            p -= 20.0
        }
        // 2. Bullish Engulfing setup
        candles.add(Candle(baseTs + 20 * 60_000L, 24400.0, 24405.0, 24360.0, 24370.0, 60_000L, 24380.0))
        candles.add(Candle(baseTs + 21 * 60_000L, 24360.0, 24455.0, 24355.0, 24445.0, 140_000L, 24405.0))

        // 3. Morning Star setup after drop
        candles.add(Candle(baseTs + 22 * 60_000L, 24440.0, 24445.0, 24310.0, 24320.0, 90_000L, 24360.0))
        candles.add(Candle(baseTs + 23 * 60_000L, 24315.0, 24325.0, 24305.0, 24317.0, 40_000L, 24316.0))
        candles.add(Candle(baseTs + 24 * 60_000L, 24325.0, 24435.0, 24320.0, 24425.0, 150_000L, 24390.0))

        // 4. Three White Soldiers
        candles.add(Candle(baseTs + 25 * 60_000L, 24425.0, 24495.0, 24420.0, 24490.0, 120_000L, 24465.0))
        candles.add(Candle(baseTs + 26 * 60_000L, 24490.0, 24565.0, 24485.0, 24560.0, 130_000L, 24535.0))
        candles.add(Candle(baseTs + 27 * 60_000L, 24560.0, 24635.0, 24555.0, 24630.0, 145_000L, 24605.0))

        // 5. Bearish Engulfing
        candles.add(Candle(baseTs + 28 * 60_000L, 24630.0, 24665.0, 24625.0, 24660.0, 90_000L, 24650.0))
        candles.add(Candle(baseTs + 29 * 60_000L, 24670.0, 24675.0, 24580.0, 24585.0, 180_000L, 24610.0))

        val patterns = PatternDetectionEngine.detectAllPatterns(candles)
        val ids = patterns.map { it.patternId }.toSet()

        assertTrue("Detects Bullish Engulfing", CandlestickPatternId.BULLISH_ENGULFING in ids)
        assertTrue("Detects Morning Star", CandlestickPatternId.MORNING_STAR in ids)
        assertTrue("Detects Three White Soldiers", CandlestickPatternId.THREE_WHITE_SOLDIERS in ids)
        assertTrue("Detects Bearish Engulfing", CandlestickPatternId.BEARISH_ENGULFING in ids)
    }

    @Test
    fun testSmcSrVpvrMtfRiskAndZeroLookAheadBacktesting() {
        val candles = syntheticTrendingCandles(count = 140, step = 12.0)
        val swingCandles = (0 until 120).map { i ->
            val wave = kotlin.math.sin(i / 4.0) * 220.0
            val close = 24500.0 + i * 5.0 + wave
            Candle(
                timestamp = 1_740_000_000_000L + i * 3600_000L,
                open = close - 10.0,
                high = close + 28.0,
                low = close - 24.0,
                close = close,
                volume = 90_000L,
                vwap = close
            )
        }
        val structure = MarketStructureEngine.analyze(swingCandles, Timeframe.M15)
        assertTrue("Detects swing points", structure.swingPoints.isNotEmpty())
        assertTrue("Dealing range high > low", structure.dealingRangeHigh > structure.dealingRangeLow)

        val sr = SupportResistanceEngine.detectLevels(swingCandles, structure)
        assertTrue("Detects clustered S&R levels", sr.isNotEmpty())

        val vpvr = VolumeAnalysisEngine.computeVolumeProfile(candles.takeLast(60), candles)
        assertTrue("Computes POC price", vpvr.pocPrice > 0.0)
        assertTrue("VAH >= VAL", vpvr.vahPrice >= vpvr.valPrice)

        val mtfMap = mapOf(
            Timeframe.DAILY to candles,
            Timeframe.H4 to candles,
            Timeframe.H1 to candles,
            Timeframe.M15 to candles
        )
        val mtf = SignalRiskAndBacktestEngine.evaluateMtfConfluence(mtfMap)
        assertEquals(4, mtf.rows.size)
        assertEquals(MarketBias.BULLISH, mtf.overallBias)

        // MTF divergence test when M15 flips strongly bearish
        val bearCandles = syntheticTrendingCandles(count = 60, step = -15.0)
        val divMtf = SignalRiskAndBacktestEngine.evaluateMtfConfluence(
            mapOf(Timeframe.DAILY to candles, Timeframe.M15 to bearCandles)
        )
        assertNotNull("Detects higher vs lower TF divergence", divMtf.divergenceAlert)

        val signal = SignalRiskAndBacktestEngine.evaluateTechnicalSignal(candles, structure, sr, vpvr)
        assertTrue("Signal bounded -100..100", signal.score in -100..100)
        assertTrue("Signal has reasons", signal.reasons.isNotEmpty())

        val risk = SignalRiskAndBacktestEngine.calculateRiskPosition(
            capital = 200_000.0,
            riskPct = 1.5,
            entryPrice = 25000.0,
            customStopLoss = 24900.0,
            atr14 = 50.0,
            isLong = true,
            rewardRiskRatio = 2.0
        )
        assertEquals(3000.0, risk.riskAmountInr, 0.01)
        assertEquals(30, risk.recommendedShares) // 3000 / 100
        assertEquals(25200.0, risk.target2, 0.01) // 1:2 RR

        // Backtest check with EMA crossover and costs
        val backtestCandles = (0 until 120).map { i ->
            val wave = kotlin.math.sin(i / 6.0) * 180.0
            val close = 24500.0 + i * 5.0 + wave
            Candle(
                timestamp = 1_740_000_000_000L + i * 3600_000L,
                open = close - 8.0,
                high = close + 22.0,
                low = close - 20.0,
                close = close,
                volume = 90_000L,
                vwap = close
            )
        }
        val bt = SignalRiskAndBacktestEngine.runBacktest(
            candles = backtestCandles,
            config = BacktestConfig(
                strategyId = BacktestStrategyId.EMA_CROSSOVER,
                initialCapital = 200_000.0,
                slippagePct = 0.03,
                brokeragePerOrder = 20.0
            )
        )
        assertTrue("Generates trades across oscillating waves", bt.trades.isNotEmpty())
        assertTrue("Equity curve covers test bars", bt.equityCurve.isNotEmpty())
        assertTrue("Max drawdown non-negative", bt.maxDrawdownPct >= 0.0)
    }

    @Test
    fun testIstAggregationHolidayGuardAndHeikinAshi() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(ist).apply {
            set(2026, Calendar.JANUARY, 26, 10, 0, 0) // Republic Day NSE Holiday
        }
        assertFalse("Republic Day is not an NSE trading day", TimeframeAggregationEngine.isNseTradingDay(cal.timeInMillis))

        // Build 6 consecutive 5m bars starting 09:15 IST -> must aggregate to 3 clean 10m bars
        val dayCal = Calendar.getInstance(ist).apply {
            set(2026, Calendar.FEBRUARY, 16, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val base5m = (0 until 6).map { i ->
            Candle(
                timestamp = dayCal.timeInMillis + i * 5 * 60_000L,
                open = 25000.0 + i * 10.0,
                high = 25015.0 + i * 10.0,
                low = 24995.0 + i * 10.0,
                close = 25010.0 + i * 10.0,
                volume = 10_000L,
                vwap = 25005.0 + i * 10.0
            )
        }
        val agg10m = TimeframeAggregationEngine.aggregateCandlesForTimeframe(base5m, Timeframe.M10)
        assertEquals(3, agg10m.size)
        assertEquals(20_000L, agg10m.first().volume)
        assertEquals(base5m[0].open, agg10m[0].open, 0.001)
        assertEquals(base5m[1].close, agg10m[0].close, 0.001)

        val ha = TimeframeAggregationEngine.toHeikinAshi(base5m)
        assertEquals(base5m.size, ha.size)
        assertEquals((base5m[0].open + base5m[0].high + base5m[0].low + base5m[0].close) / 4.0, ha[0].close, 0.001)
    }

    @Test
    fun testRoomPersistenceRoundTripInRobolectric() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChartWorkstationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomChartSettingsRepository(db)
            repo.ensureSeeded()

            val watchlist = repo.observeWatchlist().first()
            assertTrue("Seeded NSE watchlist contains NIFTY 50 & RELIANCE", watchlist.any { it.symbol == "NIFTY 50" } && watchlist.any { it.symbol == "RELIANCE" })

            val customSym = MarketSymbol("TATAMOTORS", "Tata Motors", "TATAMOTORS.NS")
            repo.addWatchlistSymbol(customSym)
            assertTrue(repo.observeWatchlist().first().any { it.symbol == "TATAMOTORS" })

            val layout = SavedChartLayout(
                id = "custom_1",
                name = "Scalp Pro",
                themeMode = ChartThemeMode.INSTITUTIONAL_LIGHT,
                chartType = ChartType.HEIKIN_ASHI,
                scaleMode = PriceScaleMode.LOGARITHMIC,
                indicators = IndicatorCalculationEngine.defaultConfigs()
            )
            repo.saveLayout(layout)
            assertTrue(repo.observeLayouts().first().any { it.id == "custom_1" && it.themeMode == ChartThemeMode.INSTITUTIONAL_LIGHT })

            val drawing = ChartDrawing(
                id = "draw_1",
                symbol = "NIFTY 50",
                timeframe = "15m",
                toolType = DrawingToolType.HORIZONTAL_LINE,
                points = listOf(ChartAnchorPoint(1_740_000_000_000L, 25100.0)),
                colorHex = 0xFF00E5FF
            )
            repo.saveDrawing(drawing)
            assertEquals(1, repo.observeDrawings("NIFTY 50", "15m").first().size)

            repo.saveLlmConfig(
                LlmProviderConfig(
                    id = LlmProviderType.OLLAMA.name,
                    providerType = LlmProviderType.OLLAMA,
                    baseUrl = "http://127.0.0.1:11434",
                    modelName = "llama3.1:8b",
                    isActive = true
                )
            )
            val llms = repo.observeLlmConfigs().first()
            assertEquals(LlmProviderType.OLLAMA.name, llms.first { it.isActive }.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun testDashboardExpandToWorkstationAndThemeInspectorFlows() {
        val vm = createOfflineViewModel()

        composeTestRule.setContent {
            val chartState by vm.chartState.collectAsState()
            MyApplicationTheme(chartThemeMode = chartState.themeMode) {
                if (chartState.isFullScreenWorkstation) {
                    ChartWorkstationScreen(
                        state = chartState,
                        coordinator = vm.chartCoordinator,
                        onClose = { vm.chartCoordinator.openFullScreenWorkstation(false) },
                        onOpenLlmSettings = {}
                    )
                } else {
                    DashboardScreen(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("expand_workstation_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_workstation_screen").assertIsDisplayed()

        // Switch Chart Style & Theme inside Workstation
        composeTestRule.onNodeWithTag("workstation_chart_type_heikin_ashi").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ChartType.HEIKIN_ASHI, vm.chartState.value.chartType)

        composeTestRule.onNodeWithTag("workstation_theme_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ChartThemeMode.INSTITUTIONAL_LIGHT, vm.chartState.value.themeMode)

        // Verify all 7 Inspector Tabs open cleanly
        WorkstationInspectorTab.entries.forEach { tab ->
            composeTestRule.onNodeWithTag("workstation_tab_${tab.name.lowercase()}").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals(tab, vm.chartState.value.activeInspectorTab)
        }

        // Verify Back to Dashboard
        composeTestRule.onNodeWithTag("workstation_back_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(vm.chartState.value.isFullScreenWorkstation)
    }

    @Test
    fun testSettingsLlmAndThemeSectionSwitchesThemeAndProviders() {
        val store = InMemoryChartSettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val coordinator = ChartWorkstationCoordinator(scope = scope, settingsStore = store)

        composeTestRule.setContent {
            val state by coordinator.state.collectAsState()
            MyApplicationTheme(chartThemeMode = state.themeMode) {
                SettingsLlmAndThemeSection(
                    themeMode = state.themeMode,
                    llmConfigs = state.llmConfigs,
                    isTestingLlm = state.isTestingLlm,
                    llmTestStatus = state.llmTestStatus,
                    onSelectTheme = { coordinator.setThemeMode(it) },
                    onSelectActiveProvider = { coordinator.selectLlmProvider(it) },
                    onSaveConfig = { coordinator.saveLlmConfig(it) },
                    onTestConnection = { coordinator.testLlmConnection(it) }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_llm_and_theme_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_option_institutional_light").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ChartThemeMode.INSTITUTIONAL_LIGHT, coordinator.state.value.themeMode)
    }
}
