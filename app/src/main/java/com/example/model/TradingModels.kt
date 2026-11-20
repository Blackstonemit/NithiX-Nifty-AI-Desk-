package com.example.model

enum class MarketBias {
    BULLISH,
    BEARISH,
    SIDEWAYS,
    NEUTRAL
}

enum class SignalType(val label: String) {
    BUY_CE("BUY CALL (CE)"),
    BUY_PE("BUY PUT (PE)"),
    BULLISH_FUTURES("BULLISH FUTURES"),
    BEARISH_FUTURES("BEARISH FUTURES"),
    HOLD("HOLD"),
    EXIT("EXIT"),
    NO_TRADE("NO TRADE")
}

enum class BuildupType(val label: String) {
    LONG_BUILDUP("Long Buildup"),
    SHORT_BUILDUP("Short Buildup"),
    SHORT_COVERING("Short Covering"),
    LONG_UNWINDING("Long Unwinding"),
    NEUTRAL("Consolidation")
}

enum class Timeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M3("3m", 3),
    M5("5m", 5),
    M10("10m", 10),
    M15("15m", 15),
    M30("30m", 30),
    M45("45m", 45),
    H1("1h", 60),
    H2("2h", 120),
    H4("4h", 240),
    DAILY("1D", 375),
    WEEKLY("1W", 1875),
    MONTHLY("1Mo", 7500)
}

enum class TradingWorkflow(val title: String, val hours: String) {
    PRE_MARKET("Pre-Market", "08:45 - 09:15 IST"),
    OPENING_RANGE("Opening Range", "09:15 - 09:30 IST"),
    MARKET_HOURS("Market Hours", "09:30 - 15:15 IST"),
    PRE_CLOSING("Pre-Closing", "15:15 - 15:30 IST")
}

enum class TradingStyle(val title: String, val description: String) {
    INTRADAY_SCALPING("Intraday Scalping", "Fast momentum on 1m-3m options premiums"),
    INTRADAY_MOMENTUM("Intraday Momentum", "5m-15m trend-following with VWAP & EMA confluence"),
    BREAKOUT_TRADING("Breakout Trading", "ORB, PDH/PDL breakouts with volume confirmation"),
    SWING_FUTURES("Swing Futures", "Multi-day trend riding using Index Futures contracts"),
    SHORT_OPTIONS("Short Options / Spreads", "Defined-risk spreads capitalizing on Theta decay")
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val vwap: Double
)

data class OptionStrike(
    val strikePrice: Double,
    val callOi: Long,
    val callOiChange: Long,
    val callLtp: Double,
    val callIv: Double,
    val callDelta: Double,
    val callGamma: Double,
    val callTheta: Double,
    val callVega: Double,
    val callBuildup: BuildupType,
    val putOi: Long,
    val putOiChange: Long,
    val putLtp: Double,
    val putIv: Double,
    val putDelta: Double,
    val putGamma: Double,
    val putTheta: Double,
    val putVega: Double,
    val putBuildup: BuildupType,
    val isAtm: Boolean = false,
    val isMaxCallOi: Boolean = false,
    val isMaxPutOi: Boolean = false
)

data class OptionsChainSnapshot(
    val strikes: List<OptionStrike>,
    val atmStrike: Double,
    val totalCallOi: Long,
    val totalPutOi: Long,
    val pcr: Double,
    val maxCallOiStrike: Double,
    val maxPutOiStrike: Double,
    val maxPainStrike: Double,
    val ivPercentile: Double,
    val atmStraddlePremium: Double,
    val expectedMovePoints: Double,
    val expiryDate: String
)

data class NiftySpotData(
    val spotPrice: Double,
    val change: Double,
    val changePct: Double,
    val dayOpen: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val prevClose: Double,
    val indiaVix: Double,
    val vixChangePct: Double,
    val gapType: String,
    val gapPoints: Double,
    val marketStructure: String,
    val pdh: Double,
    val pdl: Double,
    val dailyPivot: Double,
    val r1: Double,
    val r2: Double,
    val s1: Double,
    val s2: Double,
    val weeklyHigh: Double,
    val weeklyLow: Double
)

data class FuturesData(
    val currentMonthExpiry: String,
    val currentMonthPrice: Double,
    val currentMonthOi: Long,
    val currentMonthOiChange: Long,
    val currentMonthBuildup: BuildupType,
    val nextMonthExpiry: String,
    val nextMonthPrice: Double,
    val nextMonthOi: Long,
    val basis: Double, // Futures - Spot
    val fiiLongContracts: Long,
    val fiiShortContracts: Long,
    val fiiNetRatio: Double,
    val diiNetActivityCr: Double,
    val institutionalBias: MarketBias
)

data class TechnicalAnalysisSnapshot(
    val trendDirection: MarketBias,
    val rsi14: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHist: Double,
    val vwap: Double,
    val ema9: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val supertrend: Double,
    val supertrendBullish: Boolean,
    val adx: Double,
    val diPlus: Double,
    val diMinus: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val atr14: Double,
    val fibLevels: Map<String, Double>,
    val candlestickPattern: String,
    val momentumStrength: String
)

data class PriceZone(
    val label: String,
    val low: Double,
    val high: Double,
    val isBullish: Boolean
)

data class OrderBlock(
    val name: String,
    val low: Double,
    val high: Double,
    val isBullish: Boolean,
    val isMitigated: Boolean
)

data class SmartMoneySnapshot(
    val bosDetected: Boolean,
    val bosType: String,
    val chochDetected: Boolean,
    val chochType: String,
    val fvgZones: List<PriceZone>,
    val orderBlocks: List<OrderBlock>,
    val currentZone: String, // e.g. "Discount Zone (42%)"
    val liquiditySweepObserved: Boolean
)

data class BreakoutSnapshot(
    val orb15mHigh: Double,
    val orb15mLow: Double,
    val orbStatus: String,
    val pdhStatus: String,
    val pdlStatus: String,
    val patternDetected: String,
    val volumeConfirmed: Boolean
)

data class AgentReport(
    val agentId: Int,
    val agentName: String,
    val agentRole: String,
    val weightPct: Int,
    val bias: MarketBias,
    val confidence: Int, // 0 - 100
    val summary: String,
    val keyFindings: List<String>,
    val rawMetrics: Map<String, String>
)

data class FinalTradeRecommendation(
    val direction: MarketBias,
    val signal: SignalType,
    val instrument: String,
    val expiryDate: String,
    val strike: Double,
    val optionType: String, // "CE", "PE", "FUTURES", "NONE"
    val entryZone: String,
    val stopLoss: String,
    val target1: String,
    val target2: String,
    val target3: String,
    val riskToReward: String,
    val expectedHoldingPeriod: String,
    val confidenceScore: Int,
    val setupInvalidationLevel: String,
    val tradeRationale: String,
    val timestamp: Long,
    val latencyMs: Long,
    val strategyName: String
)

data class ExecutedOrder(
    val id: String,
    val timestamp: Long,
    val instrument: String,
    val signal: SignalType,
    val lots: Int,
    val quantity: Int, // lots * 25
    val entryPrice: Double,
    val currentPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val status: String, // "ACTIVE", "TARGET_1", "TARGET_2", "STOPPED_OUT", "EXITED"
    val pnl: Double,
    val charges: Double,
    val netPnl: Double
)

enum class ExecutionAction(val label: String) {
    ENTRY("ENTRY"),
    HOLD("HOLD"),
    PARTIAL_EXIT_50("PARTIAL EXIT (50%)"),
    PARTIAL_EXIT_75("PARTIAL EXIT (75%)"),
    TRAIL_STOP_LOSS("TRAIL STOP-LOSS"),
    FULL_EXIT("FULL EXIT"),
    RE_ENTRY("RE-ENTRY")
}

enum class AlertAction(val label: String) {
    BUY_CE_NOW("BUY CE NOW"),
    BUY_PE_NOW("BUY PE NOW"),
    SELL_CE_NOW("SELL CE NOW"),
    SELL_PE_NOW("SELL PE NOW"),
    HOLD_POSITION("HOLD POSITION"),
    BOOK_50_PROFIT("BOOK 50% PROFIT"),
    BOOK_75_PROFIT("BOOK 75% PROFIT"),
    TRAIL_STOP_LOSS("TRAIL STOP-LOSS"),
    EXIT_FULL_POSITION("EXIT FULL POSITION"),
    RE_ENTRY_OPPORTUNITY("RE-ENTRY OPPORTUNITY"),
    STOP_LOSS_HIT("STOP-LOSS HIT")
}

enum class OptionsStrategyType(val label: String, val isSelling: Boolean) {
    BUY_CE("BUY CALL (CE)", false),
    BUY_PE("BUY PUT (PE)", false),
    SELL_CE("SELL CALL (CE)", true),
    SELL_PE("SELL PUT (PE)", true)
}

data class LiveTradeAlert(
    val id: String,
    val timestamp: Long,
    val alertType: AlertAction,
    val strategy: OptionsStrategyType,
    val instrument: String,
    val strike: Double,
    val expiry: String,
    val entryPremium: Double,
    val currentPremium: Double,
    val stopLoss: Double,
    val trailingStopLoss: Double,
    val target1: Double,
    val target2: Double,
    val target3: Double,
    val pnlPoints: Double,
    val pnlPct: Double,
    val unrealizedPnlInr: Double,
    val timeDecayImpact: String,
    val remainingRisk: String,
    val exitTrigger: String,
    val reason: String,
    val priorityLevel: Int // 1: Hard SL, 2: Thesis Invalidation, 3: Reversal, 4: Profit Booking, 5: Trailing SL, 6: Time-based
)

data class PositionLifecycle(
    val positionId: String,
    val strategy: OptionsStrategyType,
    val instrument: String,
    val strike: Double,
    val expiry: String,
    val lots: Int,
    val quantity: Int, // lots * 25
    val entryPremium: Double,
    val currentPremium: Double,
    val initialStopLoss: Double,
    val trailingStopLoss: Double,
    val target1: Double,
    val target2: Double,
    val target3: Double,
    val unrealizedPnl: Double,
    val returnPct: Double,
    val status: ExecutionAction,
    val marginRequired: Double,
    val gammaExposure: String,
    val exitReason: String,
    val openTimestamp: Long
)

