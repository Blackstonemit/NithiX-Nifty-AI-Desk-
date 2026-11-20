package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MultiLlmProviderManager
import com.example.data.MarketChartDataRepository
import com.example.data.local.BacktestRunEntity
import com.example.data.local.ChartSettingsStore
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
import com.example.model.AiChartAnalysisUiState
import com.example.model.BacktestConfig
import com.example.model.BacktestResult
import com.example.model.BacktestStrategyId
import com.example.model.Candle
import com.example.model.ChartAnchorPoint
import com.example.model.ChartDrawing
import com.example.model.ChartType
import com.example.model.DataFreshnessInfo
import com.example.model.DateRangePreset
import com.example.model.DetectedCandlestickPattern
import com.example.model.DrawingToolType
import com.example.model.IndicatorConfig
import com.example.model.IndicatorPresetTemplate
import com.example.model.IndicatorSeriesSet
import com.example.model.LlmProviderConfig
import com.example.model.LlmProviderType
import com.example.model.MarketBias
import com.example.model.MarketStructureAnalysis
import com.example.model.MarketSymbol
import com.example.model.MtfConfluenceSummary
import com.example.model.NseWatchlistCatalog
import com.example.model.PriceScaleMode
import com.example.model.RiskPositionPlan
import com.example.model.SupportResistanceLevel
import com.example.model.TechnicalSignalSummary
import com.example.model.Timeframe
import com.example.model.VolumeProfileSummary
import com.example.ui.theme.ChartThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class WorkstationInspectorTab(val label: String) {
    INDICATORS("Indicators"),
    STRUCTURE("SMC & Patterns"),
    SR_VOLUME("S/R & VPVR"),
    MTF_SIGNAL("MTF & Signals"),
    RISK("Risk Sizing"),
    BACKTEST("Backtest"),
    AI_ANALYST("AI Analyst")
}

data class ChartWorkstationUiState(
    val activeSymbol: MarketSymbol = NseWatchlistCatalog.defaultSymbols.first(),
    val watchlist: List<MarketSymbol> = NseWatchlistCatalog.defaultSymbols,
    val timeframe: Timeframe = Timeframe.M15,
    val dateRange: DateRangePreset = DateRangePreset.D5,
    val chartType: ChartType = ChartType.CANDLESTICK,
    val scaleMode: PriceScaleMode = PriceScaleMode.LINEAR,
    val themeMode: ChartThemeMode = ChartThemeMode.FINANCIAL_DARK,
    val isFullScreenWorkstation: Boolean = false,
    val isRoomOhlcvTerminalOpen: Boolean = false,
    val activeInspectorTab: WorkstationInspectorTab? = null,
    val isLoadingCandles: Boolean = false,
    val candles: List<Candle> = emptyList(),
    val displayCandles: List<Candle> = emptyList(),
    val dataFreshness: DataFreshnessInfo = DataFreshnessInfo("Verified Market Reference", isRealTime = false),
    val showGrid: Boolean = true,
    val showCountdown: Boolean = true,
    val showAfterHours: Boolean = false,
    val showVolumeProfile: Boolean = true,
    val showSmcOverlays: Boolean = false,
    val showSrLevels: Boolean = true,
    val showPatternBadges: Boolean = true,
    val isAdjustedPrices: Boolean = true,
    val magnetEnabled: Boolean = true,
    val hideAllDrawings: Boolean = false,
    val visibleCandleCount: Int = 55,
    val scrollRightOffset: Int = 0,
    val yZoomFactor: Float = 1.0f,
    val indicatorConfigs: List<IndicatorConfig> = IndicatorCalculationEngine.defaultConfigs(),
    val indicatorSeries: IndicatorSeriesSet = IndicatorSeriesSet(emptyMap(), emptyMap()),
    val detectedPatterns: List<DetectedCandlestickPattern> = emptyList(),
    val marketStructure: MarketStructureAnalysis = MarketStructureEngine.analyze(emptyList(), Timeframe.M15),
    val srLevels: List<SupportResistanceLevel> = emptyList(),
    val volumeProfile: VolumeProfileSummary = VolumeAnalysisEngine.computeVolumeProfile(emptyList()),
    val mtfConfluence: MtfConfluenceSummary = MtfConfluenceSummary(emptyList(), 50, MarketBias.NEUTRAL, null),
    val technicalSignal: TechnicalSignalSummary = TechnicalSignalSummary(0, "Neutral", 0, 0, 0, 0, emptyList(), 0.0),
    val drawings: List<ChartDrawing> = emptyList(),
    val activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    val pendingAnchor: ChartAnchorPoint? = null,
    val activeDrawingColor: Long = 0xFF00E5FF,
    val riskCapital: Double = 200000.0,
    val riskPct: Double = 1.5,
    val rewardRiskRatio: Double = 2.0,
    val customStopLoss: Double? = null,
    val showRiskOnChart: Boolean = false,
    val riskPlan: RiskPositionPlan = SignalRiskAndBacktestEngine.calculateRiskPosition(200000.0, 1.5, 25000.0, null, 50.0, true, 2.0),
    val backtestConfig: BacktestConfig = BacktestConfig(),
    val backtestResult: BacktestResult? = null,
    val savedLayouts: List<SavedChartLayout> = emptyList(),
    val savedBacktestRuns: List<BacktestRunEntity> = emptyList(),
    val llmConfigs: List<LlmProviderConfig> = LlmProviderType.entries.map {
        LlmProviderConfig(id = it.name, providerType = it, baseUrl = it.defaultUrl, modelName = it.defaultModel, isActive = it == LlmProviderType.GEMINI_FIREBASE)
    },
    val llmTestStatus: String? = null,
    val isTestingLlm: Boolean = false,
    val aiAnalysisState: AiChartAnalysisUiState = AiChartAnalysisUiState.Idle,
    val statusBanner: String? = null
) {
    val activeLlmConfig: LlmProviderConfig
        get() = llmConfigs.firstOrNull { it.isActive } ?: llmConfigs.first()
}

class ChartWorkstationCoordinator(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val settingsStore: ChartSettingsStore,
    private val marketDataRepo: MarketChartDataRepository = MarketChartDataRepository(),
    private val llmManager: MultiLlmProviderManager = MultiLlmProviderManager(),
    private val indstocksTokenProvider: () -> String = { "" }
) {
    private val _state = MutableStateFlow(ChartWorkstationUiState())
    val state: StateFlow<ChartWorkstationUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<ChartDrawing>()
    private val redoStack = ArrayDeque<ChartDrawing>()
    private var drawingsJob: Job? = null
    private var aiJob: Job? = null
    private var recomputeJob: Job? = null
    private var fetchJob: Job? = null

    init {
        scope.launch {
            settingsStore.ensureSeeded()
            launch { settingsStore.observeWatchlist().collectLatest { w -> _state.update { it.copy(watchlist = w) } } }
            launch { settingsStore.observeLayouts().collectLatest { l -> _state.update { it.copy(savedLayouts = l) } } }
            launch { settingsStore.observeLlmConfigs().collectLatest { c -> _state.update { it.copy(llmConfigs = c) } } }
            launch { settingsStore.observeBacktestRuns().collectLatest { b -> _state.update { it.copy(savedBacktestRuns = b) } } }
        }
        observeDrawingsForActiveKey()
    }

    private fun observeDrawingsForActiveKey() {
        drawingsJob?.cancel()
        val sym = _state.value.activeSymbol.symbol
        val tf = _state.value.timeframe.label
        drawingsJob = scope.launch {
            settingsStore.observeDrawings(sym, tf).collectLatest { list ->
                _state.update { it.copy(drawings = list) }
            }
        }
    }

    fun syncFromDashboardCandles(candles: List<Candle>, timeframe: Timeframe, dataSourceLabel: String, isLive: Boolean) {
        if (_state.value.activeSymbol.symbol != "NIFTY 50" && _state.value.candles.isNotEmpty()) return
        if (candles.isEmpty()) return
        _state.update { cur ->
            cur.copy(
                timeframe = timeframe,
                candles = candles,
                dataFreshness = cur.dataFreshness.copy(
                    sourceLabel = dataSourceLabel,
                    isRealTime = isLive,
                    lastUpdatedEpochMs = candles.last().timestamp
                )
            )
        }
        recomputeAllAnalytics()
    }

    fun openFullScreenWorkstation(open: Boolean) {
        _state.update {
            it.copy(
                isFullScreenWorkstation = open,
                activeInspectorTab = if (!open) null else it.activeInspectorTab
            )
        }
        if (open && _state.value.mtfConfluence.rows.isEmpty()) {
            refreshMtfConfluence()
        }
    }

    fun openRoomOhlcvTerminal(open: Boolean) {
        _state.update { it.copy(isRoomOhlcvTerminalOpen = open) }
    }

    fun selectInspectorTab(tab: WorkstationInspectorTab?) {
        _state.update { cur ->
            cur.copy(activeInspectorTab = if (cur.activeInspectorTab == tab) null else tab)
        }
        if (tab == WorkstationInspectorTab.MTF_SIGNAL) refreshMtfConfluence()
    }

    fun selectSymbol(symbol: MarketSymbol) {
        if (symbol.symbol == _state.value.activeSymbol.symbol) return
        _state.update {
            it.copy(
                activeSymbol = symbol,
                pendingAnchor = null,
                scrollRightOffset = 0,
                aiAnalysisState = AiChartAnalysisUiState.Idle,
                backtestResult = null
            )
        }
        observeDrawingsForActiveKey()
        fetchActiveSymbolCandles()
    }

    fun addCustomSymbol(rawSymbol: String) {
        val clean = rawSymbol.trim()
        if (clean.isBlank()) return
        val normalized = NseWatchlistCatalog.normalizeCustomNseSymbol(clean)
        scope.launch {
            settingsStore.addWatchlistSymbol(normalized)
        }
        selectSymbol(normalized)
    }

    fun removeWatchlistSymbol(symbol: String) {
        scope.launch {
            settingsStore.removeWatchlistSymbol(symbol)
            if (_state.value.activeSymbol.symbol == symbol) {
                selectSymbol(NseWatchlistCatalog.defaultSymbols.first())
            }
        }
    }

    fun setTimeframe(timeframe: Timeframe) {
        if (timeframe == _state.value.timeframe) return
        _state.update { it.copy(timeframe = timeframe, pendingAnchor = null, scrollRightOffset = 0) }
        observeDrawingsForActiveKey()
        fetchActiveSymbolCandles()
    }

    fun setDateRange(range: DateRangePreset) {
        _state.update { it.copy(dateRange = range, scrollRightOffset = 0) }
        fetchActiveSymbolCandles()
    }

    fun setChartType(type: ChartType) {
        _state.update { it.copy(chartType = type) }
        recomputeDisplayCandlesOnly()
    }

    fun setScaleMode(mode: PriceScaleMode) = _state.update { it.copy(scaleMode = mode) }

    fun setThemeMode(mode: ChartThemeMode) = _state.update { it.copy(themeMode = mode) }

    fun toggleThemeMode() = _state.update {
        val next = if (it.themeMode == ChartThemeMode.FINANCIAL_DARK) {
            ChartThemeMode.INSTITUTIONAL_LIGHT
        } else ChartThemeMode.FINANCIAL_DARK
        it.copy(themeMode = next)
    }

    fun toggleDisplayOption(
        grid: Boolean? = null,
        countdown: Boolean? = null,
        afterHours: Boolean? = null,
        vpvr: Boolean? = null,
        smc: Boolean? = null,
        sr: Boolean? = null,
        patterns: Boolean? = null,
        adjusted: Boolean? = null,
        magnet: Boolean? = null,
        hideDrawings: Boolean? = null
    ) {
        _state.update { cur ->
            cur.copy(
                showGrid = grid ?: cur.showGrid,
                showCountdown = countdown ?: cur.showCountdown,
                showAfterHours = afterHours ?: cur.showAfterHours,
                showVolumeProfile = vpvr ?: cur.showVolumeProfile,
                showSmcOverlays = smc ?: cur.showSmcOverlays,
                showSrLevels = sr ?: cur.showSrLevels,
                showPatternBadges = patterns ?: cur.showPatternBadges,
                isAdjustedPrices = adjusted ?: cur.isAdjustedPrices,
                magnetEnabled = magnet ?: cur.magnetEnabled,
                hideAllDrawings = hideDrawings ?: cur.hideAllDrawings
            )
        }
        if (adjusted != null) fetchActiveSymbolCandles()
    }

    fun updateViewport(visibleCountDelta: Int = 0, panBarsDelta: Int = 0, yZoomDelta: Float = 0f, reset: Boolean = false) {
        _state.update { cur ->
            val maxCandles = cur.displayCandles.size.coerceAtLeast(15)
            if (reset) {
                cur.copy(visibleCandleCount = minOf(60, maxCandles), scrollRightOffset = 0, yZoomFactor = 1.0f)
            } else {
                val newVis = (cur.visibleCandleCount + visibleCountDelta).coerceIn(12, minOf(250, maxCandles))
                val maxScroll = (cur.displayCandles.size - newVis).coerceAtLeast(0)
                val newScroll = (cur.scrollRightOffset + panBarsDelta).coerceIn(0, maxScroll)
                val newYZoom = (cur.yZoomFactor + yZoomDelta).coerceIn(0.45f, 3.0f)
                cur.copy(visibleCandleCount = newVis, scrollRightOffset = newScroll, yZoomFactor = newYZoom)
            }
        }
    }

    fun toggleIndicator(id: String) {
        _state.update { cur ->
            cur.copy(indicatorConfigs = cur.indicatorConfigs.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
        }
        recomputeAllAnalytics()
    }

    fun updateIndicatorParams(id: String, period: Int, secondary: Int, tertiary: Int, multiplier: Double, colorHex: Long) {
        _state.update { cur ->
            cur.copy(
                indicatorConfigs = cur.indicatorConfigs.map { cfg ->
                    if (cfg.id == id) cfg.copy(
                        enabled = true,
                        period = period.coerceIn(1, 300),
                        secondaryPeriod = secondary.coerceIn(0, 200),
                        tertiaryPeriod = tertiary.coerceIn(0, 200),
                        multiplier = multiplier.coerceIn(0.005, 10.0),
                        colorHex = colorHex
                    ) else cfg
                }
            )
        }
        recomputeAllAnalytics()
    }

    fun applyPresetTemplate(template: IndicatorPresetTemplate) {
        _state.update { cur ->
            val mapById = template.configs.associateBy { it.id }
            val merged = cur.indicatorConfigs.map { existing ->
                mapById[existing.id] ?: existing.copy(enabled = false)
            } + template.configs.filter { t -> cur.indicatorConfigs.none { it.id == t.id } }
            cur.copy(chartType = template.chartType, indicatorConfigs = merged)
        }
        recomputeAllAnalytics()
    }

    fun saveCurrentLayout(name: String) {
        val cur = _state.value
        val layout = SavedChartLayout(
            id = "layout_${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "Custom Layout" },
            themeMode = cur.themeMode,
            chartType = cur.chartType,
            scaleMode = cur.scaleMode,
            indicators = cur.indicatorConfigs,
            showGrid = cur.showGrid,
            showCountdown = cur.showCountdown,
            showAfterHours = cur.showAfterHours
        )
        scope.launch {
            settingsStore.saveLayout(layout)
            _state.update { it.copy(statusBanner = "Saved layout '${layout.name}'") }
        }
    }

    fun applySavedLayout(layout: SavedChartLayout) {
        _state.update {
            it.copy(
                themeMode = layout.themeMode,
                chartType = layout.chartType,
                scaleMode = layout.scaleMode,
                indicatorConfigs = layout.indicators,
                showGrid = layout.showGrid,
                showCountdown = layout.showCountdown,
                showAfterHours = layout.showAfterHours
            )
        }
        recomputeAllAnalytics()
    }

    fun deleteSavedLayout(id: String) {
        scope.launch { settingsStore.deleteLayout(id) }
    }

    fun selectDrawingTool(tool: DrawingToolType) {
        _state.update { it.copy(activeDrawingTool = tool, pendingAnchor = null) }
    }

    fun setDrawingColor(colorHex: Long) {
        _state.update { it.copy(activeDrawingColor = colorHex) }
    }

    fun handleChartCanvasTapForDrawing(rawAnchor: ChartAnchorPoint, snappedAnchor: ChartAnchorPoint, noteText: String = "Note") {
        val cur = _state.value
        val tool = cur.activeDrawingTool
        if (tool == DrawingToolType.NONE) return
        val effectiveAnchor = if (cur.magnetEnabled) snappedAnchor else rawAnchor

        if (tool.requiredAnchors == 1) {
            commitNewDrawing(
                ChartDrawing(
                    id = UUID.randomUUID().toString(),
                    symbol = cur.activeSymbol.symbol,
                    timeframe = cur.timeframe.label,
                    toolType = tool,
                    points = listOf(effectiveAnchor),
                    colorHex = cur.activeDrawingColor,
                    text = if (tool == DrawingToolType.TEXT_NOTE) noteText else ""
                )
            )
            _state.update { it.copy(activeDrawingTool = DrawingToolType.NONE, pendingAnchor = null) }
        } else {
            val first = cur.pendingAnchor
            if (first == null) {
                _state.update { it.copy(pendingAnchor = effectiveAnchor) }
            } else {
                commitNewDrawing(
                    ChartDrawing(
                        id = UUID.randomUUID().toString(),
                        symbol = cur.activeSymbol.symbol,
                        timeframe = cur.timeframe.label,
                        toolType = tool,
                        points = listOf(first, effectiveAnchor),
                        colorHex = cur.activeDrawingColor
                    )
                )
                _state.update { it.copy(activeDrawingTool = DrawingToolType.NONE, pendingAnchor = null) }
            }
        }
    }

    private fun commitNewDrawing(drawing: ChartDrawing) {
        undoStack.addLast(drawing)
        redoStack.clear()
        scope.launch { settingsStore.saveDrawing(drawing) }
    }

    fun undoLastDrawing() {
        val currentDrawings = _state.value.drawings
        val lockedIds = currentDrawings.filter { it.isLocked }.mapTo(HashSet()) { it.id }
        var target: ChartDrawing? = null
        while (undoStack.isNotEmpty()) {
            val candidate = undoStack.removeLast()
            if (candidate.id !in lockedIds) {
                target = candidate
                break
            }
        }
        if (target == null) {
            target = currentDrawings.lastOrNull { !it.isLocked }
        }
        val toDelete = target ?: return
        redoStack.addLast(toDelete)
        scope.launch { settingsStore.deleteDrawing(toDelete.id) }
    }

    fun redoDrawing() {
        val item = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(item)
        scope.launch { settingsStore.saveDrawing(item) }
    }

    fun toggleLockDrawing(drawing: ChartDrawing) {
        scope.launch { settingsStore.saveDrawing(drawing.copy(isLocked = !drawing.isLocked)) }
    }

    fun deleteDrawing(id: String) {
        val target = _state.value.drawings.firstOrNull { it.id == id }
        if (target?.isLocked == true) return
        scope.launch { settingsStore.deleteDrawing(id) }
    }

    fun clearAllDrawings() {
        val cur = _state.value
        val lockedIds = cur.drawings.filter { it.isLocked }.mapTo(HashSet()) { it.id }
        val keptUndo = undoStack.filter { it.id in lockedIds }
        undoStack.clear()
        keptUndo.forEach { undoStack.addLast(it) }
        redoStack.clear()
        scope.launch { settingsStore.clearDrawings(cur.activeSymbol.symbol, cur.timeframe.label) }
    }

    fun updateRiskInputs(capital: Double? = null, riskPct: Double? = null, rewardRatio: Double? = null, customSl: Double? = null, clearCustomSl: Boolean = false, showOnChart: Boolean? = null) {
        _state.update { cur ->
            val cap = capital ?: cur.riskCapital
            val rPct = riskPct ?: cur.riskPct
            val rr = rewardRatio ?: cur.rewardRiskRatio
            val sl = if (clearCustomSl) null else (customSl ?: cur.customStopLoss)
            val candles = cur.candles
            val lastClose = candles.lastOrNull()?.close ?: 25000.0
            val atr = cur.indicatorSeries.atrSeries.lastOrNull()?.takeIf { it > 0 } ?: (lastClose * 0.005)
            val isLong = cur.technicalSignal.score >= 0
            cur.copy(
                riskCapital = cap,
                riskPct = rPct,
                rewardRiskRatio = rr,
                customStopLoss = sl,
                showRiskOnChart = showOnChart ?: cur.showRiskOnChart,
                riskPlan = SignalRiskAndBacktestEngine.calculateRiskPosition(cap, rPct, lastClose, sl, atr, isLong, rr)
            )
        }
    }

    fun runBacktest(config: BacktestConfig) {
        val cur = _state.value
        _state.update { it.copy(backtestConfig = config) }
        if (cur.candles.isEmpty()) return
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                SignalRiskAndBacktestEngine.runBacktest(cur.candles, config)
            }
            _state.update { it.copy(backtestResult = result) }
            settingsStore.saveBacktestRun(cur.activeSymbol.symbol, cur.timeframe.label, result)
        }
    }

    fun selectLlmProvider(providerId: String) {
        scope.launch { settingsStore.setActiveLlmProvider(providerId) }
    }

    fun saveLlmConfig(config: LlmProviderConfig) {
        scope.launch { settingsStore.saveLlmConfig(config) }
    }

    fun testLlmConnection(config: LlmProviderConfig) {
        scope.launch {
            _state.update { it.copy(isTestingLlm = true, llmTestStatus = null) }
            val result = llmManager.testConnection(config)
            _state.update {
                it.copy(
                    isTestingLlm = false,
                    llmTestStatus = result.fold(
                        onSuccess = { msg -> "PASS: $msg" },
                        onFailure = { err -> "ERROR: ${err.message ?: "Connection failed"}" }
                    )
                )
            }
        }
    }

    fun runAiChartAnalysis(customQuestion: String? = null) {
        val cur = _state.value
        if (cur.candles.isEmpty()) return
        aiJob?.cancel()
        aiJob = scope.launch {
            llmManager.analyzeChartStream(
                config = cur.activeLlmConfig,
                symbolName = "${cur.activeSymbol.symbol} (${cur.activeSymbol.displayName})",
                timeframe = cur.timeframe,
                candles = cur.candles,
                indicators = cur.indicatorSeries,
                patterns = cur.detectedPatterns,
                structure = cur.marketStructure,
                srLevels = cur.srLevels,
                volumeProfile = cur.volumeProfile,
                mtf = cur.mtfConfluence,
                signal = cur.technicalSignal,
                customQuestion = customQuestion
            ).collect { ui ->
                _state.update { it.copy(aiAnalysisState = ui) }
            }
        }
    }

    fun fetchActiveSymbolCandles() {
        val cur = _state.value
        val requestedSymbol = cur.activeSymbol
        val requestedTimeframe = cur.timeframe
        val requestedRange = cur.dateRange
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _state.update { it.copy(isLoadingCandles = true, statusBanner = null) }
            val res = marketDataRepo.fetchCandlesForSymbol(
                symbol = requestedSymbol,
                timeframe = requestedTimeframe,
                dateRange = requestedRange,
                indstocksToken = indstocksTokenProvider(),
                isAdjusted = cur.isAdjustedPrices
            )
            val latest = _state.value
            if (latest.activeSymbol.symbol != requestedSymbol.symbol ||
                latest.timeframe != requestedTimeframe ||
                latest.dateRange != requestedRange
            ) {
                return@launch
            }
            if (res.candles.isNotEmpty()) {
                _state.update {
                    it.copy(
                        isLoadingCandles = false,
                        candles = res.candles,
                        dataFreshness = res.freshness,
                        statusBanner = res.freshness.gapNote
                    )
                }
                recomputeAllAnalytics()
            } else {
                _state.update {
                    it.copy(
                        isLoadingCandles = false,
                        dataFreshness = res.freshness,
                        statusBanner = res.errorMessage ?: "Unable to fetch OHLCV for ${requestedSymbol.symbol}"
                    )
                }
            }
        }
    }

    fun refreshMtfConfluence() {
        val cur = _state.value
        scope.launch {
            val map = marketDataRepo.fetchMultiTimeframeCandles(
                symbol = cur.activeSymbol,
                indstocksToken = indstocksTokenProvider(),
                currentTf = cur.timeframe,
                currentCandles = cur.candles
            )
            if (map.isNotEmpty()) {
                val summary = withContext(Dispatchers.Default) {
                    SignalRiskAndBacktestEngine.evaluateMtfConfluence(map)
                }
                _state.update { it.copy(mtfConfluence = summary) }
            }
        }
    }

    private fun recomputeDisplayCandlesOnly() {
        val cur = _state.value
        val disp = if (cur.chartType == ChartType.HEIKIN_ASHI) {
            TimeframeAggregationEngine.toHeikinAshi(cur.candles)
        } else cur.candles
        _state.update { it.copy(displayCandles = disp) }
    }

    private fun recomputeAllAnalytics() {
        recomputeJob?.cancel()
        recomputeJob = scope.launch {
            val snap = _state.value
            val candles = snap.candles
            if (candles.isEmpty()) return@launch
            val disp = if (snap.chartType == ChartType.HEIKIN_ASHI) {
                TimeframeAggregationEngine.toHeikinAshi(candles)
            } else candles

            val computed = withContext(Dispatchers.Default) {
                val series = IndicatorCalculationEngine.computeAll(candles, snap.indicatorConfigs)
                val patterns = PatternDetectionEngine.detectAllPatterns(candles)
                val structure = MarketStructureEngine.analyze(candles, snap.timeframe)
                val sr = SupportResistanceEngine.detectLevels(candles, structure)
                val visSlice = candles.takeLast(minOf(snap.visibleCandleCount, candles.size))
                val vp = VolumeAnalysisEngine.computeVolumeProfile(visSlice, candles)
                val signal = SignalRiskAndBacktestEngine.evaluateTechnicalSignal(candles, structure, sr, vp)
                val atr = series.atrSeries.lastOrNull()?.takeIf { it > 0 } ?: (candles.last().close * 0.005)
                val risk = SignalRiskAndBacktestEngine.calculateRiskPosition(
                    capital = snap.riskCapital,
                    riskPct = snap.riskPct,
                    entryPrice = candles.last().close,
                    customStopLoss = snap.customStopLoss,
                    atr14 = atr,
                    isLong = signal.score >= 0,
                    rewardRiskRatio = snap.rewardRiskRatio
                )
                Tuple7(series, patterns, structure, sr, vp, signal, risk)
            }

            _state.update { cur ->
                cur.copy(
                    displayCandles = disp,
                    visibleCandleCount = cur.visibleCandleCount.coerceIn(12, minOf(250, maxOf(12, disp.size))),
                    indicatorSeries = computed.series,
                    detectedPatterns = computed.patterns,
                    marketStructure = computed.structure,
                    srLevels = computed.sr,
                    volumeProfile = computed.vp,
                    technicalSignal = computed.signal,
                    riskPlan = computed.risk
                )
            }
        }
    }

    private data class Tuple7(
        val series: IndicatorSeriesSet,
        val patterns: List<DetectedCandlestickPattern>,
        val structure: MarketStructureAnalysis,
        val sr: List<SupportResistanceLevel>,
        val vp: VolumeProfileSummary,
        val signal: TechnicalSignalSummary,
        val risk: RiskPositionPlan
    )
}

fun createWorkstationCoordinator(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    indstocksTokenProvider: () -> String
): ChartWorkstationCoordinator {
    val db = ChartWorkstationDatabase.getInstance(context)
    val ohlcvDao = com.example.niftychart.di.DatabaseModule.provideNiftyOhlcvDao(context)
    return ChartWorkstationCoordinator(
        scope = scope,
        settingsStore = RoomChartSettingsRepository(db),
        marketDataRepo = MarketChartDataRepository(ohlcvDao = ohlcvDao),
        indstocksTokenProvider = indstocksTokenProvider
    )
}
