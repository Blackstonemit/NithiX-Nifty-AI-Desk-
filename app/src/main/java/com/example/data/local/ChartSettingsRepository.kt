package com.example.data.local

import com.example.engine.chart.IndicatorCalculationEngine
import com.example.model.BacktestResult
import com.example.model.ChartAnchorPoint
import com.example.model.ChartDrawing
import com.example.model.ChartType
import com.example.model.DrawingToolType
import com.example.model.IndicatorConfig
import com.example.model.IndicatorType
import com.example.model.LlmProviderConfig
import com.example.model.LlmProviderType
import com.example.model.MarketSymbol
import com.example.model.NseWatchlistCatalog
import com.example.model.PriceScaleMode
import com.example.security.SecretCipherVault
import com.example.ui.theme.ChartThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedChartLayout(
    val id: String,
    val name: String,
    val themeMode: ChartThemeMode,
    val chartType: ChartType,
    val scaleMode: PriceScaleMode,
    val indicators: List<IndicatorConfig>,
    val showGrid: Boolean = true,
    val showCountdown: Boolean = true,
    val showAfterHours: Boolean = false,
    val isDefault: Boolean = false
)

interface ChartSettingsStore {
    fun observeWatchlist(): Flow<List<MarketSymbol>>
    fun observeLayouts(): Flow<List<SavedChartLayout>>
    fun observeDrawings(symbol: String, timeframe: String): Flow<List<ChartDrawing>>
    fun observeLlmConfigs(): Flow<List<LlmProviderConfig>>
    fun observeBacktestRuns(): Flow<List<BacktestRunEntity>>

    suspend fun ensureSeeded()
    suspend fun addWatchlistSymbol(symbol: MarketSymbol)
    suspend fun removeWatchlistSymbol(symbol: String)
    suspend fun saveLayout(layout: SavedChartLayout)
    suspend fun deleteLayout(id: String)
    suspend fun saveDrawing(drawing: ChartDrawing)
    suspend fun deleteDrawing(id: String)
    suspend fun clearDrawings(symbol: String, timeframe: String)
    suspend fun saveLlmConfig(config: LlmProviderConfig)
    suspend fun setActiveLlmProvider(id: String)
    suspend fun saveBacktestRun(symbol: String, timeframe: String, result: BacktestResult)
}

class RoomChartSettingsRepository(
    private val database: ChartWorkstationDatabase
) : ChartSettingsStore {

    private val watchlistDao = database.watchlistDao()
    private val layoutDao = database.layoutDao()
    private val drawingDao = database.drawingDao()
    private val llmDao = database.llmProviderDao()
    private val backtestDao = database.backtestRunDao()

    override fun observeWatchlist(): Flow<List<MarketSymbol>> =
        watchlistDao.observeWatchlist().map { list ->
            list.map {
                MarketSymbol(
                    symbol = it.symbol,
                    displayName = it.displayName,
                    yahooTicker = it.yahooTicker,
                    indstocksScripCode = it.indstocksScripCode,
                    exchange = it.exchange,
                    isIndex = it.isIndex
                )
            }
        }

    override fun observeLayouts(): Flow<List<SavedChartLayout>> =
        layoutDao.observeLayouts().map { entities ->
            entities.map { entity ->
                SavedChartLayout(
                    id = entity.id,
                    name = entity.name,
                    themeMode = runCatching { ChartThemeMode.valueOf(entity.themeMode) }.getOrDefault(ChartThemeMode.FINANCIAL_DARK),
                    chartType = runCatching { ChartType.valueOf(entity.chartType) }.getOrDefault(ChartType.CANDLESTICK),
                    scaleMode = runCatching { PriceScaleMode.valueOf(entity.scaleMode) }.getOrDefault(PriceScaleMode.LINEAR),
                    indicators = decodeIndicators(entity.indicatorsJson),
                    showGrid = entity.showGrid,
                    showCountdown = entity.showCountdown,
                    showAfterHours = entity.showAfterHours,
                    isDefault = entity.isDefault
                )
            }
        }

    override fun observeDrawings(symbol: String, timeframe: String): Flow<List<ChartDrawing>> =
        drawingDao.observeDrawings(symbol, timeframe).map { entities ->
            entities.mapNotNull { entity ->
                val tool = runCatching { DrawingToolType.valueOf(entity.toolType) }.getOrNull() ?: return@mapNotNull null
                ChartDrawing(
                    id = entity.id,
                    symbol = entity.symbol,
                    timeframe = entity.timeframe,
                    toolType = tool,
                    points = decodePoints(entity.pointsJson),
                    colorHex = entity.colorHex,
                    strokeWidth = entity.strokeWidth,
                    isDashed = entity.isDashed,
                    isLocked = entity.isLocked,
                    isHidden = entity.isHidden,
                    text = entity.text
                )
            }
        }

    override fun observeLlmConfigs(): Flow<List<LlmProviderConfig>> =
        llmDao.observeConfigs().map { list ->
            list.map {
                val pType = runCatching { LlmProviderType.valueOf(it.providerType) }.getOrDefault(LlmProviderType.GEMINI_FIREBASE)
                LlmProviderConfig(
                    id = it.id,
                    providerType = pType,
                    baseUrl = it.baseUrl,
                    modelName = it.modelName,
                    apiKey = SecretCipherVault.decrypt(it.apiKey),
                    temperature = it.temperature,
                    isActive = it.isActive
                )
            }
        }

    override fun observeBacktestRuns(): Flow<List<BacktestRunEntity>> = backtestDao.observeRecentRuns()

    override suspend fun ensureSeeded() {
        val defaultWatchlist = NseWatchlistCatalog.defaultSymbols.mapIndexed { idx, s ->
            WatchlistSymbolEntity(
                symbol = s.symbol,
                displayName = s.displayName,
                yahooTicker = s.yahooTicker,
                indstocksScripCode = s.indstocksScripCode,
                exchange = s.exchange,
                isIndex = s.isIndex,
                sortOrder = idx
            )
        }
        watchlistDao.insertAllIgnore(defaultWatchlist)

        val defaultLlm = LlmProviderType.entries.map { type ->
            LlmProviderConfigEntity(
                id = type.name,
                providerType = type.name,
                baseUrl = type.defaultUrl,
                modelName = type.defaultModel,
                apiKey = "",
                temperature = 0.25,
                isActive = type == LlmProviderType.GEMINI_FIREBASE
            )
        }
        llmDao.insertAllIgnore(defaultLlm)

        val defaultLayout = ChartLayoutEntity(
            id = "layout_default",
            name = "Institutional Default",
            themeMode = ChartThemeMode.FINANCIAL_DARK.name,
            chartType = ChartType.CANDLESTICK.name,
            scaleMode = PriceScaleMode.LINEAR.name,
            indicatorsJson = encodeIndicators(IndicatorCalculationEngine.defaultConfigs()),
            isDefault = true
        )
        layoutDao.upsertLayout(defaultLayout)
    }

    override suspend fun addWatchlistSymbol(symbol: MarketSymbol) {
        watchlistDao.upsertSymbol(
            WatchlistSymbolEntity(
                symbol = symbol.symbol,
                displayName = symbol.displayName,
                yahooTicker = symbol.yahooTicker,
                indstocksScripCode = symbol.indstocksScripCode,
                exchange = symbol.exchange,
                isIndex = symbol.isIndex,
                sortOrder = 99
            )
        )
    }

    override suspend fun removeWatchlistSymbol(symbol: String) {
        if (symbol != "NIFTY 50") {
            watchlistDao.deleteSymbol(symbol)
        }
    }

    override suspend fun saveLayout(layout: SavedChartLayout) {
        layoutDao.upsertLayout(
            ChartLayoutEntity(
                id = layout.id,
                name = layout.name,
                themeMode = layout.themeMode.name,
                chartType = layout.chartType.name,
                scaleMode = layout.scaleMode.name,
                indicatorsJson = encodeIndicators(layout.indicators),
                showGrid = layout.showGrid,
                showCountdown = layout.showCountdown,
                showAfterHours = layout.showAfterHours,
                isDefault = layout.isDefault,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteLayout(id: String) {
        if (id != "layout_default") layoutDao.deleteLayout(id)
    }

    override suspend fun saveDrawing(drawing: ChartDrawing) {
        drawingDao.upsertDrawing(
            ChartDrawingEntity(
                id = drawing.id,
                symbol = drawing.symbol,
                timeframe = drawing.timeframe,
                toolType = drawing.toolType.name,
                pointsJson = encodePoints(drawing.points),
                colorHex = drawing.colorHex,
                strokeWidth = drawing.strokeWidth,
                isDashed = drawing.isDashed,
                isLocked = drawing.isLocked,
                isHidden = drawing.isHidden,
                text = drawing.text,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteDrawing(id: String) = drawingDao.deleteDrawing(id)

    override suspend fun clearDrawings(symbol: String, timeframe: String) =
        drawingDao.clearDrawings(symbol, timeframe)

    override suspend fun saveLlmConfig(config: LlmProviderConfig) {
        llmDao.upsertConfig(
            LlmProviderConfigEntity(
                id = config.id,
                providerType = config.providerType.name,
                baseUrl = config.baseUrl,
                modelName = config.modelName,
                apiKey = SecretCipherVault.encrypt(config.apiKey),
                temperature = config.temperature,
                isActive = config.isActive,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        if (config.isActive) llmDao.setActiveProvider(config.id)
    }

    override suspend fun setActiveLlmProvider(id: String) = llmDao.setActiveProvider(id)

    override suspend fun saveBacktestRun(symbol: String, timeframe: String, result: BacktestResult) {
        backtestDao.insertRun(
            BacktestRunEntity(
                id = UUID.randomUUID().toString(),
                strategyName = result.config.strategyId.title,
                symbol = symbol,
                timeframe = timeframe,
                totalReturnPct = result.totalReturnPct,
                winRatePct = result.winRatePct,
                profitFactor = result.profitFactor,
                maxDrawdownPct = result.maxDrawdownPct,
                sharpeRatio = result.sharpeRatio,
                tradesCount = result.trades.size
            )
        )
    }

    companion object {
        fun encodeIndicators(list: List<IndicatorConfig>): String {
            val arr = JSONArray()
            list.forEach { cfg ->
                arr.put(JSONObject().apply {
                    put("id", cfg.id)
                    put("type", cfg.type.name)
                    put("enabled", cfg.enabled)
                    put("period", cfg.period)
                    put("secondaryPeriod", cfg.secondaryPeriod)
                    put("tertiaryPeriod", cfg.tertiaryPeriod)
                    put("multiplier", cfg.multiplier)
                    put("colorHex", cfg.colorHex)
                    cfg.anchoredIndex?.let { put("anchoredIndex", it) }
                })
            }
            return arr.toString()
        }

        fun decodeIndicators(raw: String): List<IndicatorConfig> {
            return runCatching {
                val arr = JSONArray(raw)
                val out = mutableListOf<IndicatorConfig>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching { IndicatorType.valueOf(o.getString("type")) }.getOrNull() ?: continue
                    out.add(
                        IndicatorConfig(
                            id = o.optString("id", type.name.lowercase()),
                            type = type,
                            enabled = o.optBoolean("enabled", false),
                            period = o.optInt("period", type.defaultPeriod),
                            secondaryPeriod = o.optInt("secondaryPeriod", type.defaultSecondaryPeriod),
                            tertiaryPeriod = o.optInt("tertiaryPeriod", type.defaultTertiaryPeriod),
                            multiplier = o.optDouble("multiplier", type.defaultMultiplier),
                            colorHex = o.optLong("colorHex", type.defaultColorHex),
                            anchoredIndex = if (o.has("anchoredIndex")) o.optInt("anchoredIndex") else null
                        )
                    )
                }
                if (out.isEmpty()) IndicatorCalculationEngine.defaultConfigs() else out
            }.getOrDefault(IndicatorCalculationEngine.defaultConfigs())
        }

        fun encodePoints(points: List<ChartAnchorPoint>): String {
            val arr = JSONArray()
            points.forEach { p ->
                arr.put(JSONObject().apply {
                    put("ts", p.timestamp)
                    put("price", p.price)
                })
            }
            return arr.toString()
        }

        fun decodePoints(raw: String): List<ChartAnchorPoint> {
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ChartAnchorPoint(o.getLong("ts"), o.getDouble("price"))
                }
            }.getOrDefault(emptyList())
        }
    }
}

class InMemoryChartSettingsStore : ChartSettingsStore {
    private val watchlist = MutableStateFlow(NseWatchlistCatalog.defaultSymbols)
    private val layouts = MutableStateFlow(
        listOf(
            SavedChartLayout(
                id = "layout_default",
                name = "Institutional Default",
                themeMode = ChartThemeMode.FINANCIAL_DARK,
                chartType = ChartType.CANDLESTICK,
                scaleMode = PriceScaleMode.LINEAR,
                indicators = IndicatorCalculationEngine.defaultConfigs(),
                isDefault = true
            )
        )
    )
    private val drawings = MutableStateFlow<List<ChartDrawing>>(emptyList())
    private val llmConfigs = MutableStateFlow(
        LlmProviderType.entries.map {
            LlmProviderConfig(
                id = it.name,
                providerType = it,
                baseUrl = it.defaultUrl,
                modelName = it.defaultModel,
                isActive = it == LlmProviderType.GEMINI_FIREBASE
            )
        }
    )
    private val backtests = MutableStateFlow<List<BacktestRunEntity>>(emptyList())

    override fun observeWatchlist(): Flow<List<MarketSymbol>> = watchlist.asStateFlow()
    override fun observeLayouts(): Flow<List<SavedChartLayout>> = layouts.asStateFlow()
    override fun observeDrawings(symbol: String, timeframe: String): Flow<List<ChartDrawing>> =
        drawings.map { list -> list.filter { it.symbol == symbol && it.timeframe == timeframe } }
    override fun observeLlmConfigs(): Flow<List<LlmProviderConfig>> = llmConfigs.asStateFlow()
    override fun observeBacktestRuns(): Flow<List<BacktestRunEntity>> = backtests.asStateFlow()

    override suspend fun ensureSeeded() = Unit
    override suspend fun addWatchlistSymbol(symbol: MarketSymbol) {
        watchlist.value = (watchlist.value.filterNot { it.symbol == symbol.symbol } + symbol)
    }
    override suspend fun removeWatchlistSymbol(symbol: String) {
        if (symbol != "NIFTY 50") watchlist.value = watchlist.value.filterNot { it.symbol == symbol }
    }
    override suspend fun saveLayout(layout: SavedChartLayout) {
        layouts.value = (layouts.value.filterNot { it.id == layout.id } + layout)
    }
    override suspend fun deleteLayout(id: String) {
        if (id != "layout_default") layouts.value = layouts.value.filterNot { it.id == id }
    }
    override suspend fun saveDrawing(drawing: ChartDrawing) {
        drawings.value = (drawings.value.filterNot { it.id == drawing.id } + drawing)
    }
    override suspend fun deleteDrawing(id: String) {
        drawings.value = drawings.value.filterNot { it.id == id && !it.isLocked }
    }
    override suspend fun clearDrawings(symbol: String, timeframe: String) {
        drawings.value = drawings.value.filterNot {
            it.symbol == symbol && it.timeframe == timeframe && !it.isLocked
        }
    }
    override suspend fun saveLlmConfig(config: LlmProviderConfig) {
        val updated = llmConfigs.value.map {
            when {
                it.id == config.id -> config
                config.isActive -> it.copy(isActive = false)
                else -> it
            }
        }
        llmConfigs.value = if (updated.any { it.id == config.id }) updated else updated + config
    }
    override suspend fun setActiveLlmProvider(id: String) {
        llmConfigs.value = llmConfigs.value.map { it.copy(isActive = it.id == id) }
    }
    override suspend fun saveBacktestRun(symbol: String, timeframe: String, result: BacktestResult) {
        val run = BacktestRunEntity(
            id = UUID.randomUUID().toString(),
            strategyName = result.config.strategyId.title,
            symbol = symbol,
            timeframe = timeframe,
            totalReturnPct = result.totalReturnPct,
            winRatePct = result.winRatePct,
            profitFactor = result.profitFactor,
            maxDrawdownPct = result.maxDrawdownPct,
            sharpeRatio = result.sharpeRatio,
            tradesCount = result.trades.size
        )
        backtests.value = (listOf(run) + backtests.value).take(20)
    }
}
