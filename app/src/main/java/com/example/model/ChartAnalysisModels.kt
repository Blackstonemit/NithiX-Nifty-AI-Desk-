package com.example.model

enum class ChartType(val label: String, val shortLabel: String) {
    CANDLESTICK("Candlestick", "Candles"),
    HOLLOW_CANDLESTICK("Hollow Candlestick", "Hollow"),
    HEIKIN_ASHI("Heikin-Ashi", "Heikin"),
    OHLC_BARS("OHLC Bars", "OHLC"),
    LINE("Line", "Line"),
    AREA("Area", "Area"),
    BASELINE("Baseline", "Baseline")
}

enum class PriceScaleMode(val label: String) {
    LINEAR("Linear"),
    LOGARITHMIC("Log"),
    PERCENTAGE("%")
}

enum class DateRangePreset(val label: String, val yahooRange: String, val lookbackDays: Int) {
    D1("1D", "1d", 1),
    D5("5D", "5d", 5),
    M1("1M", "1mo", 30),
    M3("3M", "3mo", 90),
    M6("6M", "6mo", 180),
    YTD("YTD", "ytd", 365),
    Y1("1Y", "1y", 365),
    ALL("ALL", "5y", 1825)
}

data class MarketSymbol(
    val symbol: String,
    val displayName: String,
    val yahooTicker: String,
    val indstocksScripCode: String? = null,
    val exchange: String = "NSE",
    val isIndex: Boolean = false
)

object NseWatchlistCatalog {
    val defaultSymbols: List<MarketSymbol> = listOf(
        MarketSymbol("NIFTY 50", "NIFTY 50 Index", "%5ENSEI", "NSE_40000001", isIndex = true),
        MarketSymbol("BANKNIFTY", "NIFTY Bank Index", "%5ENSEBANK", "NSE_26009", isIndex = true),
        MarketSymbol("RELIANCE", "Reliance Industries", "RELIANCE.NS", null),
        MarketSymbol("HDFCBANK", "HDFC Bank Ltd", "HDFCBANK.NS", null),
        MarketSymbol("TCS", "Tata Consultancy Services", "TCS.NS", null),
        MarketSymbol("INFY", "Infosys Ltd", "INFY.NS", null),
        MarketSymbol("ICICIBANK", "ICICI Bank Ltd", "ICICIBANK.NS", null),
        MarketSymbol("SBIN", "State Bank of India", "SBIN.NS", null),
        MarketSymbol("LT", "Larsen & Toubro", "LT.NS", null),
        MarketSymbol("BHARTIARTL", "Bharti Airtel Ltd", "BHARTIARTL.NS", null),
        MarketSymbol("ITC", "ITC Ltd", "ITC.NS", null),
        MarketSymbol("AXISBANK", "Axis Bank Ltd", "AXISBANK.NS", null)
    )

    fun normalizeCustomNseSymbol(rawInput: String): MarketSymbol {
        val rawUpper = rawInput.trim().uppercase()
            .removePrefix("$")
            .removeSuffix(".NS")
            .removeSuffix(".BO")
        val sanitized = rawUpper
            .replace(Regex("[^A-Z0-9._^-]"), "")
            .trim('.', '-', '_')
            .ifBlank { "NIFTY50" }
        return when (sanitized) {
            "NIFTY", "NIFTY50", "^NSEI" -> defaultSymbols[0]
            "BANKNIFTY", "NIFTYBANK", "^NSEBANK" -> defaultSymbols[1]
            else -> defaultSymbols.firstOrNull { it.symbol == sanitized } ?: MarketSymbol(
                symbol = sanitized,
                displayName = "$sanitized (NSE Equity)",
                yahooTicker = "$sanitized.NS",
                indstocksScripCode = null
            )
        }
    }
}

data class DataFreshnessInfo(
    val sourceLabel: String,
    val isRealTime: Boolean,
    val isAdjusted: Boolean = true,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val hasHolidayGaps: Boolean = false,
    val gapNote: String? = null
)

enum class IndicatorCategory(val label: String) {
    MOVING_AVERAGE("Moving Averages"),
    MOMENTUM("Momentum"),
    VOLATILITY("Volatility"),
    VOLUME("Volume"),
    TREND("Trend & Cloud")
}

enum class IndicatorType(
    val displayName: String,
    val shortName: String,
    val category: IndicatorCategory,
    val isOverlay: Boolean,
    val defaultPeriod: Int,
    val defaultSecondaryPeriod: Int = 0,
    val defaultTertiaryPeriod: Int = 0,
    val defaultMultiplier: Double = 2.0,
    val defaultColorHex: Long
) {
    SMA("Simple Moving Average", "SMA", IndicatorCategory.MOVING_AVERAGE, true, 20, defaultColorHex = 0xFF38BDF8),
    EMA("Exponential Moving Average", "EMA", IndicatorCategory.MOVING_AVERAGE, true, 20, defaultColorHex = 0xFFFFB300),
    WMA("Weighted Moving Average", "WMA", IndicatorCategory.MOVING_AVERAGE, true, 20, defaultColorHex = 0xFFA78BFA),
    HMA("Hull Moving Average", "HMA", IndicatorCategory.MOVING_AVERAGE, true, 21, defaultColorHex = 0xFFF472B6),
    VWMA("Volume Weighted MA", "VWMA", IndicatorCategory.MOVING_AVERAGE, true, 20, defaultColorHex = 0xFF2DD4BF),
    BOLLINGER_BANDS("Bollinger Bands", "BB", IndicatorCategory.VOLATILITY, true, 20, defaultMultiplier = 2.0, defaultColorHex = 0xFF60A5FA),
    KELTNER_CHANNELS("Keltner Channels", "KC", IndicatorCategory.VOLATILITY, true, 20, 10, defaultMultiplier = 1.5, defaultColorHex = 0xFFC084FC),
    DONCHIAN_CHANNELS("Donchian Channels", "DC", IndicatorCategory.VOLATILITY, true, 20, defaultColorHex = 0xFFFBBF24),
    VWAP("Session VWAP", "VWAP", IndicatorCategory.VOLUME, true, 1, defaultColorHex = 0xFF00E5FF),
    ANCHORED_VWAP("Anchored VWAP", "AVWAP", IndicatorCategory.VOLUME, true, 1, defaultColorHex = 0xFFFB7185),
    PARABOLIC_SAR("Parabolic SAR", "PSAR", IndicatorCategory.TREND, true, 2, defaultMultiplier = 0.02, defaultColorHex = 0xFFE879F9),
    SUPERTREND("Supertrend", "ST", IndicatorCategory.TREND, true, 10, defaultMultiplier = 3.0, defaultColorHex = 0xFF00E676),
    ICHIMOKU_CLOUD("Ichimoku Cloud", "ICHI", IndicatorCategory.TREND, true, 9, 26, 52, defaultColorHex = 0xFF34D399),

    VOLUME("Volume + MA", "VOL", IndicatorCategory.VOLUME, false, 20, defaultColorHex = 0xFF00E676),
    RSI("Relative Strength Index", "RSI", IndicatorCategory.MOMENTUM, false, 14, defaultColorHex = 0xFFA855F7),
    MACD("MACD (12, 26, 9)", "MACD", IndicatorCategory.MOMENTUM, false, 12, 26, 9, defaultColorHex = 0xFF38BDF8),
    STOCHASTIC("Stochastic Oscillator", "STOCH", IndicatorCategory.MOMENTUM, false, 14, 3, 3, defaultColorHex = 0xFFF59E0B),
    CCI("Commodity Channel Index", "CCI", IndicatorCategory.MOMENTUM, false, 20, defaultColorHex = 0xFF22D3EE),
    WILLIAMS_R("Williams %R", "W%R", IndicatorCategory.MOMENTUM, false, 14, defaultColorHex = 0xFFF43F5E),
    ROC("Rate of Change", "ROC", IndicatorCategory.MOMENTUM, false, 12, defaultColorHex = 0xFF4ADE80),
    MFI("Money Flow Index", "MFI", IndicatorCategory.MOMENTUM, false, 14, defaultColorHex = 0xFF818CF8),
    ATR("Average True Range", "ATR", IndicatorCategory.VOLATILITY, false, 14, defaultColorHex = 0xFFFB923C),
    BB_BANDWIDTH("Bollinger Bandwidth / %B", "BB%B", IndicatorCategory.VOLATILITY, false, 20, defaultMultiplier = 2.0, defaultColorHex = 0xFF60A5FA),
    OBV("On-Balance Volume", "OBV", IndicatorCategory.VOLUME, false, 20, defaultColorHex = 0xFF2DD4BF),
    CMF("Chaikin Money Flow", "CMF", IndicatorCategory.VOLUME, false, 20, defaultColorHex = 0xFF10B981),
    ADX("ADX / +DI / -DI", "ADX", IndicatorCategory.TREND, false, 14, defaultColorHex = 0xFFFACC15)
}

data class IndicatorConfig(
    val id: String,
    val type: IndicatorType,
    val enabled: Boolean = true,
    val period: Int = type.defaultPeriod,
    val secondaryPeriod: Int = type.defaultSecondaryPeriod,
    val tertiaryPeriod: Int = type.defaultTertiaryPeriod,
    val multiplier: Double = type.defaultMultiplier,
    val colorHex: Long = type.defaultColorHex,
    val anchoredIndex: Int? = null
) {
    fun displayTag(): String = when (type) {
        IndicatorType.MACD -> "MACD($period,$secondaryPeriod,$tertiaryPeriod)"
        IndicatorType.STOCHASTIC -> "STOCH($period,$secondaryPeriod,$tertiaryPeriod)"
        IndicatorType.BOLLINGER_BANDS, IndicatorType.SUPERTREND -> "${type.shortName}($period,${multiplier})"
        IndicatorType.VWAP -> "VWAP"
        IndicatorType.ANCHORED_VWAP -> "AVWAP"
        else -> "${type.shortName}($period)"
    }
}

data class IndicatorSeriesSet(
    val overlayLines: Map<String, List<Double>>,
    val bands: Map<String, Triple<List<Double>, List<Double>, List<Double>>>,
    val ichimoku: IchimokuSeries? = null,
    val psarSeries: List<Double> = emptyList(),
    val supertrendSeries: List<Pair<Double, Boolean>> = emptyList(),
    val rsiSeries: List<Double> = emptyList(),
    val macdSeries: Triple<List<Double>, List<Double>, List<Double>> = Triple(emptyList(), emptyList(), emptyList()),
    val stochSeries: Pair<List<Double>, List<Double>> = Pair(emptyList(), emptyList()),
    val cciSeries: List<Double> = emptyList(),
    val williamsRSeries: List<Double> = emptyList(),
    val rocSeries: List<Double> = emptyList(),
    val mfiSeries: List<Double> = emptyList(),
    val atrSeries: List<Double> = emptyList(),
    val bbBandwidthPctB: Pair<List<Double>, List<Double>> = Pair(emptyList(), emptyList()),
    val obvSeries: List<Double> = emptyList(),
    val cmfSeries: List<Double> = emptyList(),
    val adxSeries: Triple<List<Double>, List<Double>, List<Double>> = Triple(emptyList(), emptyList(), emptyList()),
    val volumeMaSeries: List<Double> = emptyList()
)

data class IchimokuSeries(
    val tenkanSen: List<Double>,
    val kijunSen: List<Double>,
    val senkouSpanA: List<Double>,
    val senkouSpanB: List<Double>,
    val chikouSpan: List<Double>
)

data class IndicatorPresetTemplate(
    val id: String,
    val name: String,
    val description: String,
    val chartType: ChartType,
    val configs: List<IndicatorConfig>
)

enum class CandlestickPatternId(val title: String, val sentiment: String) {
    DOJI("Doji", "NEUTRAL"),
    DRAGONFLY_DOJI("Dragonfly Doji", "BULLISH"),
    GRAVESTONE_DOJI("Gravestone Doji", "BEARISH"),
    HAMMER("Hammer", "BULLISH"),
    INVERTED_HAMMER("Inverted Hammer", "BULLISH"),
    SHOOTING_STAR("Shooting Star", "BEARISH"),
    HANGING_MAN("Hanging Man", "BEARISH"),
    BULLISH_ENGULFING("Bullish Engulfing", "BULLISH"),
    BEARISH_ENGULFING("Bearish Engulfing", "BEARISH"),
    MORNING_STAR("Morning Star", "BULLISH"),
    EVENING_STAR("Evening Star", "BEARISH"),
    THREE_WHITE_SOLDIERS("Three White Soldiers", "BULLISH"),
    THREE_BLACK_CROWS("Three Black Crows", "BEARISH"),
    HARAMI("Harami Reversal", "NEUTRAL"),
    PIERCING_LINE("Piercing Line", "BULLISH"),
    DARK_CLOUD_COVER("Dark Cloud Cover", "BEARISH")
}

data class DetectedCandlestickPattern(
    val candleIndex: Int,
    val timestamp: Long,
    val patternId: CandlestickPatternId,
    val title: String,
    val sentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val confidencePct: Int,
    val explanation: String
)

data class SwingPoint(
    val index: Int,
    val timestamp: Long,
    val price: Double,
    val isHigh: Boolean,
    val structureTag: String // "HH", "HL", "LH", "LL", "SH", "SL"
)

data class StructureBreakEvent(
    val index: Int,
    val timestamp: Long,
    val levelPrice: Double,
    val type: String, // "BOS" or "CHoCH"
    val isBullish: Boolean
)

data class SmartMoneyZone(
    val id: String,
    val type: String, // "OB" or "FVG"
    val startIndex: Int,
    val endIndex: Int,
    val topPrice: Double,
    val bottomPrice: Double,
    val isBullish: Boolean,
    val isMitigated: Boolean
)

data class LiquiditySweepEvent(
    val index: Int,
    val timestamp: Long,
    val sweptPrice: Double,
    val isBuySide: Boolean
)

data class OrbLevels(
    val high: Double,
    val low: Double,
    val durationMinutes: Int,
    val status: String
)

data class MarketStructureAnalysis(
    val swingPoints: List<SwingPoint>,
    val structureBreaks: List<StructureBreakEvent>,
    val orderBlocks: List<SmartMoneyZone>,
    val fvgZones: List<SmartMoneyZone>,
    val liquiditySweeps: List<LiquiditySweepEvent>,
    val dealingRangeHigh: Double,
    val dealingRangeLow: Double,
    val equilibriumPrice: Double,
    val currentZoneLabel: String, // "Discount", "Premium", "Equilibrium"
    val orb15m: OrbLevels?,
    val orb30m: OrbLevels?
)

enum class SrSource(val label: String) {
    SWING("Swing Cluster"),
    PDH_PDL("Prev Day H/L"),
    PWH_PWL("Prev Week H/L"),
    PIVOT_CLASSIC("Classic Pivot"),
    PIVOT_CAMARILLA("Camarilla Pivot"),
    DYNAMIC_MA("Dynamic MA"),
    VOLUME_CLUSTER("Volume Cluster")
}

data class SupportResistanceLevel(
    val id: String,
    val price: Double,
    val zoneLow: Double,
    val zoneHigh: Double,
    val label: String,
    val touchCount: Int,
    val strengthScore: Int, // 0..100
    val source: SrSource,
    val isSupport: Boolean
)

data class VolumeProfileBin(
    val lowPrice: Double,
    val highPrice: Double,
    val totalVolume: Long,
    val bullVolume: Long,
    val bearVolume: Long
)

data class VolumeProfileSummary(
    val bins: List<VolumeProfileBin>,
    val pocPrice: Double,
    val vahPrice: Double,
    val valPrice: Double,
    val rvolRatio: Double,
    val upVolumePct: Double,
    val isApproximated: Boolean,
    val hasVolumeData: Boolean
)

enum class DrawingToolType(
    val label: String,
    val shortLabel: String,
    val requiredAnchors: Int
) {
    NONE("Cursor / Pan", "Pan", 0),
    TRENDLINE("Trendline", "Line", 2),
    RAY("Trend Ray", "Ray", 2),
    HORIZONTAL_LINE("Horizontal Level", "H-Line", 1),
    VERTICAL_LINE("Vertical Time", "V-Line", 1),
    CHANNEL("Parallel Channel", "Channel", 2),
    RECTANGLE_ZONE("Rectangle / Zone", "Box", 2),
    FIB_RETRACEMENT("Fib Retracement", "Fib Ret", 2),
    FIB_EXTENSION("Fib Extension", "Fib Ext", 2),
    TEXT_NOTE("Text Note", "Text", 1),
    ARROW_CALLOUT("Arrow Callout", "Arrow", 2),
    LONG_POSITION("Long Position R:R", "Long R:R", 2),
    SHORT_POSITION("Short Position R:R", "Short R:R", 2)
}

data class ChartAnchorPoint(
    val timestamp: Long,
    val price: Double
)

data class ChartDrawing(
    val id: String,
    val symbol: String,
    val timeframe: String,
    val toolType: DrawingToolType,
    val points: List<ChartAnchorPoint>,
    val colorHex: Long = 0xFF00E5FF,
    val strokeWidth: Float = 2.5f,
    val isDashed: Boolean = false,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val text: String = ""
)

data class MtfTimeframeState(
    val timeframe: Timeframe,
    val trendLabel: String,
    val bias: MarketBias,
    val rsi: Double,
    val emaAlignment: String,
    val supertrendBullish: Boolean,
    val structureTag: String
)

data class MtfConfluenceSummary(
    val rows: List<MtfTimeframeState>,
    val alignmentScorePct: Int,
    val overallBias: MarketBias,
    val divergenceAlert: String?
)

data class TechnicalSignalSummary(
    val score: Int, // -100..+100
    val rating: String, // "Strong Buy", "Buy", "Neutral", "Sell", "Strong Sell"
    val trendScore: Int,
    val momentumScore: Int,
    val volumeScore: Int,
    val structureScore: Int,
    val reasons: List<String>,
    val invalidationPrice: Double
)

data class RiskPositionPlan(
    val capital: Double,
    val riskPct: Double,
    val riskAmountInr: Double,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val atrStop15x: Double,
    val atrStop20x: Double,
    val target1: Double,
    val target2: Double,
    val target3: Double,
    val recommendedShares: Int,
    val recommendedNiftyLots: Int,
    val positionValueInr: Double,
    val rewardRiskRatio: Double,
    val isLong: Boolean
)

enum class BacktestStrategyId(val title: String, val summary: String) {
    EMA_CROSSOVER("EMA Crossover (9/21)", "Enters long on fast EMA cross above slow EMA; exits on reverse cross"),
    RSI_MEAN_REVERSION("RSI Mean Reversion (14)", "Buys oversold RSI < 30 recovery and exits above RSI 60"),
    SUPERTREND_TREND("Supertrend Breakout (10,3)", "Rides Supertrend bullish flips with close confirmation"),
    MACD_ADX_MOMENTUM("MACD + ADX Trend Filter", "Trades MACD bullish cross only when ADX > 20 confirms trend"),
    DONCHIAN_BREAKOUT("Donchian 20 Breakout", "Buys close above prior 20-bar high and trails 10-bar low")
}

data class BacktestConfig(
    val strategyId: BacktestStrategyId = BacktestStrategyId.EMA_CROSSOVER,
    val initialCapital: Double = 200000.0,
    val riskPerTradePct: Double = 1.5,
    val slippagePct: Double = 0.03,
    val brokeragePerOrder: Double = 20.0,
    val fastPeriod: Int = 9,
    val slowPeriod: Int = 21
)

data class BacktestTrade(
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val side: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Int,
    val netPnl: Double,
    val returnPct: Double,
    val exitReason: String
)

data class BacktestResult(
    val config: BacktestConfig,
    val trades: List<BacktestTrade>,
    val equityCurve: List<Pair<Long, Double>>,
    val totalReturnPct: Double,
    val buyAndHoldReturnPct: Double,
    val winRatePct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val sharpeRatio: Double,
    val netProfitInr: Double
)

enum class LlmProviderType(
    val displayName: String,
    val defaultUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean
) {
    GEMINI_FIREBASE(
        displayName = "Google Gemini (Firebase AI)",
        defaultUrl = "firebase-ai-logic",
        defaultModel = "gemini-3.8-flash",
        requiresApiKey = false
    ),
    OLLAMA(
        displayName = "Ollama Local / LAN",
        defaultUrl = "http://127.0.0.1:11434",
        defaultModel = "qwen2.5:14b",
        requiresApiKey = false
    ),
    NVIDIA_NIM(
        displayName = "NVIDIA NIM API",
        defaultUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
        defaultModel = "meta/llama-3.3-70b-instruct",
        requiresApiKey = true
    ),
    OPENAI(
        displayName = "OpenAI API",
        defaultUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        requiresApiKey = true
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultUrl = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-sonnet-latest",
        requiresApiKey = true
    ),
    CUSTOM_LOCAL(
        displayName = "Custom OpenAI-Compatible Local",
        defaultUrl = "http://127.0.0.1:8080/v1/chat/completions",
        defaultModel = "gemma-2-9b-it",
        requiresApiKey = false
    )
}

data class LlmProviderConfig(
    val id: String = LlmProviderType.GEMINI_FIREBASE.name,
    val providerType: LlmProviderType = LlmProviderType.GEMINI_FIREBASE,
    val baseUrl: String = providerType.defaultUrl,
    val modelName: String = providerType.defaultModel,
    val apiKey: String = "",
    val temperature: Double = 0.25,
    val isActive: Boolean = providerType == LlmProviderType.GEMINI_FIREBASE
)

data class StructuredAiChartReport(
    val marketSummary: String,
    val keyLevels: String,
    val tradeIdea: String,
    val riskFactors: String,
    val alternativeScenario: String,
    val rawText: String
)

sealed interface AiChartAnalysisUiState {
    data object Idle : AiChartAnalysisUiState
    data class Loading(val providerLabel: String) : AiChartAnalysisUiState
    data class Streaming(val partialText: String, val providerLabel: String) : AiChartAnalysisUiState
    data class Success(
        val report: StructuredAiChartReport,
        val providerLabel: String,
        val modelName: String,
        val latencyMs: Long
    ) : AiChartAnalysisUiState
    data class Error(
        val message: String,
        val providerLabel: String,
        val quantitativeFallback: String
    ) : AiChartAnalysisUiState
}
