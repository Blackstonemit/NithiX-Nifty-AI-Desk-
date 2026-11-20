package com.example.engine.chart

import com.example.engine.TechnicalCalculator
import com.example.model.Candle
import com.example.model.CandlestickPatternId
import com.example.model.DetectedCandlestickPattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Rule-based 16-pattern candlestick recognition engine evaluated with prior trend context
 * (downtrend for bullish reversals, uptrend for bearish reversals) and confidence scoring.
 */
object PatternDetectionEngine {

    fun detectAllPatterns(candles: List<Candle>): List<DetectedCandlestickPattern> {
        if (candles.size < 3) return emptyList()
        val ema20 = TechnicalCalculator.calculateEmaSeries(candles.map { it.close }, 20)
        val detected = ArrayList<DetectedCandlestickPattern>()

        for (i in 2 until candles.size) {
            val c0 = candles[i - 2]
            val c1 = candles[i - 1]
            val c2 = candles[i]

            val range2 = max(c2.high - c2.low, 0.0001)
            val body2 = abs(c2.close - c2.open)
            val upper2 = c2.high - max(c2.open, c2.close)
            val lower2 = min(c2.open, c2.close) - c2.low
            val isBull2 = c2.close > c2.open
            val isBear2 = c2.close < c2.open

            val body1 = abs(c1.close - c1.open)
            val isBull1 = c1.close > c1.open
            val isBear1 = c1.close < c1.open

            val body0 = abs(c0.close - c0.open)
            val range0 = max(c0.high - c0.low, 0.0001)
            val isBull0 = c0.close > c0.open
            val isBear0 = c0.close < c0.open

            val inDowntrend = i >= 5 && (c1.close < ema20[i - 1] || candles[i - 1].close < candles[i - 4].close)
            val inUptrend = i >= 5 && (c1.close > ema20[i - 1] || candles[i - 1].close > candles[i - 4].close)

            fun addPattern(id: CandlestickPatternId, conf: Int, reason: String) {
                detected.add(
                    DetectedCandlestickPattern(
                        candleIndex = i,
                        timestamp = c2.timestamp,
                        patternId = id,
                        title = id.title,
                        sentiment = id.sentiment,
                        confidencePct = conf.coerceIn(50, 95),
                        explanation = reason
                    )
                )
            }

            // Triple-candle patterns first (highest specificity)
            if (isBear0 && body0 > range0 * 0.45 &&
                body1 <= max(c1.high - c1.low, 0.0001) * 0.35 &&
                isBull2 && c2.close > (c0.open + c0.close) / 2.0
            ) {
                addPattern(
                    CandlestickPatternId.MORNING_STAR,
                    if (inDowntrend) 88 else 74,
                    "Three-candle bullish reversal: strong selling bar, indecision star, then close past midpoint of first bar."
                )
                continue
            }

            if (isBull0 && body0 > range0 * 0.45 &&
                body1 <= max(c1.high - c1.low, 0.0001) * 0.35 &&
                isBear2 && c2.close < (c0.open + c0.close) / 2.0
            ) {
                addPattern(
                    CandlestickPatternId.EVENING_STAR,
                    if (inUptrend) 88 else 74,
                    "Three-candle bearish reversal: strong impulse up, small star exhaustion, then decisive breakdown."
                )
                continue
            }

            if (isBull0 && isBull1 && isBull2 &&
                c1.close > c0.close && c2.close > c1.close &&
                body0 > range0 * 0.5 && body2 > range2 * 0.5 &&
                upper2 < range2 * 0.25
            ) {
                addPattern(
                    CandlestickPatternId.THREE_WHITE_SOLDIERS,
                    if (inDowntrend) 86 else 78,
                    "Three consecutive expanding bullish closes with minimal upper wicks signaling sustained buying control."
                )
                continue
            }

            if (isBear0 && isBear1 && isBear2 &&
                c1.close < c0.close && c2.close < c1.close &&
                body0 > range0 * 0.5 && body2 > range2 * 0.5 &&
                lower2 < range2 * 0.25
            ) {
                addPattern(
                    CandlestickPatternId.THREE_BLACK_CROWS,
                    if (inUptrend) 86 else 78,
                    "Three consecutive expanding bearish closes near lows signaling aggressive distribution."
                )
                continue
            }

            // Double-candle patterns
            if (isBear1 && isBull2 && c2.open <= c1.close && c2.close >= c1.open && body2 > body1) {
                addPattern(
                    CandlestickPatternId.BULLISH_ENGULFING,
                    if (inDowntrend) 85 else 72,
                    "Current green body completely engulfs the prior red candle body, shifting short-term control to buyers."
                )
                continue
            }

            if (isBull1 && isBear2 && c2.open >= c1.close && c2.close <= c1.open && body2 > body1) {
                addPattern(
                    CandlestickPatternId.BEARISH_ENGULFING,
                    if (inUptrend) 85 else 72,
                    "Current red body completely engulfs the prior green candle body, rejecting higher prices."
                )
                continue
            }

            if (isBear1 && isBull2 && c2.open < c1.low && c2.close > (c1.open + c1.close) / 2.0 && c2.close < c1.open) {
                addPattern(
                    CandlestickPatternId.PIERCING_LINE,
                    if (inDowntrend) 79 else 67,
                    "Bullish gap-down open followed by strong recovery closing above 50% of the prior bearish body."
                )
                continue
            }

            if (isBull1 && isBear2 && c2.open > c1.high && c2.close < (c1.open + c1.close) / 2.0 && c2.close > c1.open) {
                addPattern(
                    CandlestickPatternId.DARK_CLOUD_COVER,
                    if (inUptrend) 79 else 67,
                    "Bearish reversal opening above prior high and closing below the midpoint of the prior bullish candle."
                )
                continue
            }

            val haramiContained = max(c2.open, c2.close) < max(c1.open, c1.close) &&
                min(c2.open, c2.close) > min(c1.open, c1.close) &&
                body2 < body1 * 0.55
            if (haramiContained && body1 > 0.0) {
                addPattern(
                    CandlestickPatternId.HARAMI,
                    if ((isBear1 && isBull2 && inDowntrend) || (isBull1 && isBear2 && inUptrend)) 74 else 62,
                    "Inside candle body contained inside the prior wide candle signaling momentum pause or reversal."
                )
                continue
            }

            // Single-candle patterns (Doji variants & Pin Bars)
            if (body2 <= range2 * 0.12) {
                when {
                    lower2 >= range2 * 0.60 && upper2 <= range2 * 0.15 -> addPattern(
                        CandlestickPatternId.DRAGONFLY_DOJI,
                        if (inDowntrend) 80 else 68,
                        "Long lower shadow with open/close near the high shows strong demand defending support."
                    )
                    upper2 >= range2 * 0.60 && lower2 <= range2 * 0.15 -> addPattern(
                        CandlestickPatternId.GRAVESTONE_DOJI,
                        if (inUptrend) 80 else 68,
                        "Long upper rejection shadow with close back near the low highlights overhead supply."
                    )
                    else -> addPattern(
                        CandlestickPatternId.DOJI,
                        66,
                        "Tight body equilibrium between buyers and sellers; watch next candle break for direction."
                    )
                }
                continue
            }

            if (lower2 >= max(body2, range2 * 0.1) * 2.0 && upper2 <= body2 * 0.35) {
                if (inUptrend) {
                    addPattern(
                        CandlestickPatternId.HANGING_MAN,
                        74,
                        "Long lower wick after an advance warns that intraday selling pressure is emerging."
                    )
                } else {
                    addPattern(
                        CandlestickPatternId.HAMMER,
                        if (inDowntrend) 81 else 69,
                        "Hammer pin-bar: sellers pushed lower before buyers reclaimed the body near session highs."
                    )
                }
                continue
            }

            if (upper2 >= max(body2, range2 * 0.1) * 2.0 && lower2 <= body2 * 0.35) {
                if (inDowntrend) {
                    addPattern(
                        CandlestickPatternId.INVERTED_HAMMER,
                        73,
                        "Inverted Hammer in a downtrend shows buyers testing higher levels for a potential base."
                    )
                } else {
                    addPattern(
                        CandlestickPatternId.SHOOTING_STAR,
                        if (inUptrend) 82 else 69,
                        "Shooting Star rejection wick indicates failed breakout and active overhead supply."
                    )
                }
            }
        }

        return detected.takeLast(18)
    }
}
