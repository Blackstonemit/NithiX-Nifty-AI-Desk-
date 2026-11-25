package com.example.niftychart.presentation.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.FeedConnectionStatus
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.model.RsiSeries
import com.example.niftychart.domain.model.SmaPeriod
import com.example.niftychart.domain.repository.NiftyMarketRepository
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import com.example.niftychart.domain.usecase.GetHistoricalCandlesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable UI State for [NiftyChartScreen].
 */
data class ChartUiState(
    val symbol: String = OHLCV.SYMBOL_NIFTY_50,
    val selectedInterval: ChartInterval = ChartInterval.M15,
    val candles: List<OHLCV> = emptyList(),
    val visibleCandleCount: Int = 55,
    val scrollRightOffset: Int = 0,
    val selectedCandleIndex: Int? = null,
    val isCrosshairLocked: Boolean = false,
    val enabledSmas: Set<SmaPeriod> = setOf(SmaPeriod.SMA_20, SmaPeriod.SMA_50),
    val smaLines: Map<SmaPeriod, List<Double?>> = emptyMap(),
    val rsiEnabled: Boolean = true,
    val rsiPeriod: Int = CalculateRSIUseCase.DEFAULT_RSI_PERIOD,
    val rsiSeries: RsiSeries = RsiSeries(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val connectionStatus: FeedConnectionStatus = FeedConnectionStatus.OFFLINE,
    val syncStatusLabel: String = "Loading Room Cache…",
    val isStreamingActive: Boolean = false,
    val isDarkTheme: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && candles.isEmpty()

    val inspectedOrLatestCandle: OHLCV?
        get() = selectedCandleIndex?.let { candles.getOrNull(it) } ?: candles.lastOrNull()

    val viewport: ChartViewportRange
        get() = ChartScaleCalculator.computeViewport(
            totalCandles = candles.size,
            visibleCount = visibleCandleCount,
            scrollRightOffset = scrollRightOffset
        )

    val latestSmaValues: Map<SmaPeriod, Double?>
        get() {
            val targetIdx = selectedCandleIndex ?: candles.lastIndex
            if (targetIdx < 0) return emptyMap()
            return enabledSmas.associateWith { sma ->
                smaLines[sma]?.getOrNull(targetIdx)
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val repository: NiftyMarketRepository,
    private val getHistoricalCandlesUseCase: GetHistoricalCandlesUseCase = GetHistoricalCandlesUseCase(repository),
    private val calculateSMAUseCase: CalculateSMAUseCase = CalculateSMAUseCase(),
    private val calculateRSIUseCase: CalculateRSIUseCase = CalculateRSIUseCase(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoInitialize: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private val activeKey = MutableStateFlow(OHLCV.SYMBOL_NIFTY_50 to ChartInterval.M15)
    private var streamJob: Job? = null
    private var indicatorJob: Job? = null

    init {
        if (autoInitialize) {
            observeDatabaseAndSync()
        }
    }

    fun startObserving() {
        observeDatabaseAndSync()
    }

    private fun observeDatabaseAndSync() {
        viewModelScope.launch {
            repository.connectionStatus.collectLatest { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        viewModelScope.launch {
            activeKey
                .flatMapLatest { (sym, interval) ->
                    getHistoricalCandlesUseCase.observe(symbol = sym, interval = interval, limit = 360)
                }
                .collectLatest { persistedCandles ->
                    _uiState.update { cur ->
                        val maxVis = persistedCandles.size.coerceAtLeast(15)
                        val adjustedVisible = cur.visibleCandleCount.coerceIn(12, minOf(240, maxVis))
                        val maxScroll = (persistedCandles.size - adjustedVisible).coerceAtLeast(0)
                        cur.copy(
                            candles = persistedCandles,
                            visibleCandleCount = adjustedVisible,
                            scrollRightOffset = cur.scrollRightOffset.coerceIn(0, maxScroll),
                            isLoading = if (persistedCandles.isNotEmpty()) false else cur.isLoading,
                            errorMessage = if (persistedCandles.isNotEmpty()) null else cur.errorMessage
                        )
                    }
                    recomputeIndicators(persistedCandles)
                }
        }

        syncActiveInterval(forceRefresh = false)
    }

    fun selectInterval(interval: ChartInterval) {
        val current = _uiState.value
        if (current.selectedInterval == interval && current.candles.isNotEmpty()) return

        _uiState.update {
            it.copy(
                selectedInterval = interval,
                isLoading = true,
                errorMessage = null,
                scrollRightOffset = 0,
                selectedCandleIndex = null,
                isCrosshairLocked = false
            )
        }
        activeKey.value = current.symbol to interval
        syncActiveInterval(forceRefresh = false)
        if (_uiState.value.isStreamingActive) {
            restartLiveStream(current.symbol, interval)
        }
    }

    fun selectSymbol(symbol: String) {
        symbol.trim()
            .ifBlank { OHLCV.SYMBOL_NIFTY_50 }
            .takeIf { it != _uiState.value.symbol }
            ?.let { clean ->
                val current = _uiState.value
                _uiState.update {
                    it.copy(
                        symbol = clean,
                        isLoading = true,
                        errorMessage = null,
                        scrollRightOffset = 0,
                        selectedCandleIndex = null,
                        isCrosshairLocked = false
                    )
                }
                activeKey.value = clean to current.selectedInterval
                syncActiveInterval(forceRefresh = false)
                if (_uiState.value.isStreamingActive) {
                    restartLiveStream(clean, current.selectedInterval)
                }
            }
    }

    fun syncActiveInterval(forceRefresh: Boolean = true) {
        val (sym, interval) = activeKey.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = getHistoricalCandlesUseCase.sync(
                symbol = sym,
                interval = interval,
                forceRefresh = forceRefresh
            )
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSyncing = false,
                            syncStatusLabel = summary.sourceLabel,
                            errorMessage = if (summary.totalStoredCount == 0) "No market candles available" else null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { cur ->
                        cur.copy(
                            isLoading = false,
                            isSyncing = false,
                            errorMessage = if (cur.candles.isEmpty()) {
                                err.message ?: "Unable to load ${interval.code} historical data"
                            } else null,
                            syncStatusLabel = "Offline Cache (${err.message ?: "Sync failed"})"
                        )
                    }
                }
            )
        }
    }

    fun toggleLiveStream() {
        val next = !_uiState.value.isStreamingActive
        _uiState.update { it.copy(isStreamingActive = next) }
        if (next) {
            val (sym, interval) = activeKey.value
            restartLiveStream(sym, interval)
        } else {
            streamJob?.cancel()
            streamJob = null
        }
    }

    private fun restartLiveStream(symbol: String, interval: ChartInterval) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repository.streamRealTimeCandles(symbol, interval).collectLatest { updatedCandle ->
                updatedCandle?.let {
                    _uiState.update { it.copy(syncStatusLabel = "Streaming ${interval.code} ticks") }
                }
            }
        }
    }

    fun toggleSma(sma: SmaPeriod) {
        _uiState.update { cur ->
            val next = if (sma in cur.enabledSmas) cur.enabledSmas - sma else cur.enabledSmas + sma
            cur.copy(enabledSmas = next)
        }
        recomputeIndicators(_uiState.value.candles)
    }

    fun toggleRsi() {
        _uiState.update { cur -> cur.copy(rsiEnabled = !cur.rsiEnabled) }
        recomputeIndicators(_uiState.value.candles)
    }

    fun setRsiPeriod(period: Int) {
        period.coerceIn(2, 50)
            .takeIf { it != _uiState.value.rsiPeriod }
            ?.let { safe ->
                _uiState.update { it.copy(rsiPeriod = safe) }
                recomputeIndicators(_uiState.value.candles)
            }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun panByBars(deltaBars: Int) {
        _uiState.update { cur ->
            val maxOffset = (cur.candles.size - cur.visibleCandleCount).coerceAtLeast(0)
            // Dragging right (positive deltaBars) reveals older historical bars on the left -> increases scrollRightOffset
            val nextOffset = (cur.scrollRightOffset + deltaBars).coerceIn(0, maxOffset)
            cur.copy(scrollRightOffset = nextOffset)
        }
    }

    fun applyPinchZoom(zoomScale: Float) {
        if (!zoomScale.isFinite() || zoomScale <= 0f) return
        _uiState.update { cur ->
            val maxBars = minOf(240, cur.candles.size.coerceAtLeast(15))
            val targetVisible = (cur.visibleCandleCount / zoomScale).toInt().coerceIn(12, maxBars)
            val maxOffset = (cur.candles.size - targetVisible).coerceAtLeast(0)
            cur.copy(
                visibleCandleCount = targetVisible,
                scrollRightOffset = cur.scrollRightOffset.coerceIn(0, maxOffset)
            )
        }
    }

    fun zoomIn() = applyPinchZoom(1.25f)

    fun zoomOut() = applyPinchZoom(0.8f)

    fun resetViewport() {
        _uiState.update { cur ->
            val maxBars = minOf(55, cur.candles.size.coerceAtLeast(15))
            cur.copy(
                visibleCandleCount = maxBars,
                scrollRightOffset = 0,
                selectedCandleIndex = null,
                isCrosshairLocked = false
            )
        }
    }

    fun selectCandle(globalIndex: Int?, lockCrosshair: Boolean) {
        _uiState.update { cur ->
            val clamped = globalIndex?.takeIf { it in cur.candles.indices }
            cur.copy(
                selectedCandleIndex = clamped,
                isCrosshairLocked = clamped != null && lockCrosshair
            )
        }
    }

    fun recomputeIndicatorsNow(candles: List<OHLCV> = _uiState.value.candles) {
        recomputeIndicators(candles)
    }

    private fun recomputeIndicators(candles: List<OHLCV>) {
        indicatorJob?.cancel()
        val snap = _uiState.value
        if (candles.isEmpty()) {
            _uiState.update { it.copy(smaLines = emptyMap(), rsiSeries = RsiSeries(period = snap.rsiPeriod)) }
            return
        }
        indicatorJob = viewModelScope.launch {
            val computed = withContext(computeDispatcher) {
                val closes = candles.map { it.close }
                val smaMap = snap.enabledSmas.associateWith { sma ->
                    calculateSMAUseCase.calculateFromCloses(closes, sma.period)
                }
                val rsi = if (snap.rsiEnabled) {
                    calculateRSIUseCase.calculateFromCloses(closes, snap.rsiPeriod)
                } else {
                    RsiSeries(period = snap.rsiPeriod)
                }
                smaMap to rsi
            }
            _uiState.update { cur ->
                cur.copy(
                    smaLines = computed.first,
                    rsiSeries = computed.second
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        indicatorJob?.cancel()
    }
}
