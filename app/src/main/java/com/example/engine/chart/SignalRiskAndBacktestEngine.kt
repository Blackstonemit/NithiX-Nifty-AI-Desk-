package com.example.engine.chart

import com.example.engine.TechnicalCalculator
import com.example.model.BacktestConfig
import com.example.model.BacktestResult
import com.example.model.BacktestStrategyId
import com.example.model.BacktestTrade
import com.example.model.Candle
import com.example.model.MarketBias
import com.example.model.MarketStructureAnalysis
import com.example.model.MtfConfluenceSummary
import com.example.model.MtfTimeframeState
import com.example.model.RiskPositionPlan
import com.example.model.SupportResistanceLevel
import com.example.model.TechnicalSignalSummary
import com.example.model.Timeframe
import com.example.model.VolumeProfileSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Multi-Timeframe Confluence, Rule-Based Signal Scoring (-100..+100), ATR Risk/Position Sizing,
 * and Zero-Look-Ahead Historical Backtesting Engine.
 */
object SignalRiskAndBacktestEngine {

    fun evaluateMtfConfluence(candlesByTf: Map<Timeframe, List<Candle>>): MtfConfluenceSummary {
        val targetTfs = listOf(Timeframe.DAILY, Timeframe.H4, Timeframe.H1, Timeframe.M15)
        val rows = targetTfs.mapNotNull { tf ->
            val list = candlesByTf[tf].orEmpty()
            if (list.size < 5) null else analyzeSingleTf(tf, list)
        }

        if (rows.isEmpty()) {
            return MtfConfluenceSummary(emptyList(), 50, MarketBias.NEUTRAL, null)
        }

        val bullCount = rows.count { it.bias == MarketBias.BULLISH }
        val bearCount = rows.count { it.bias == MarketBias.BEARISH }
        val dominant = when {
            bullCount > bearCount -> MarketBias.BULLISH
            bearCount > bullCount -> MarketBias.BEARISH
            else -> MarketBias.SIDEWAYS
        }
        val alignedCount = max(bullCount, bearCount)
        val alignmentPct = ((alignedCount.toDouble() / rows.size) * 100.0).toInt().coerceIn(0, 100)

        val higherTf = rows.firstOrNull { it.timeframe == Timeframe.DAILY || it.timeframe == Timeframe.H4 }
        val lowerTf = rows.lastOrNull { it.timeframe == Timeframe.M15 || it.timeframe == Timeframe.H1 }
        val divergence = if (higherTf != null && lowerTf != null &&
            higherTf.bias != lowerTf.bias &&
            higherTf.bias != MarketBias.SIDEWAYS && lowerTf.bias != MarketBias.SIDEWAYS
        ) {
            "MTF Divergence: ${higherTf.timeframe.label} is ${higherTf.bias.name} while ${lowerTf.timeframe.label} flipped ${lowerTf.bias.name}."
        } else null

        return MtfConfluenceSummary(
            rows = rows,
            alignmentScorePct = alignmentPct,
            overallBias = dominant,
            divergenceAlert = divergence
        )
    }

    private fun analyzeSingleTf(tf: Timeframe, candles: List<Candle>): MtfTimeframeState {
        val closes = candles.map { it.close }
        val ema20 = TechnicalCalculator.calculateEma(closes, min(20, closes.size))
        val ema50 = TechnicalCalculator.calculateEma(closes, min(50, closes.size))
        val rsi = TechnicalCalculator.calculateRsi(candles, 14)
        val (_, supertrendBull) = TechnicalCalculator.calculateSupertrend(candles, 10, 3.0)
        val last = closes.last()

        val bias = when {
            last > ema20 && ema20 >= ema50 && supertrendBull -> MarketBias.BULLISH
            last < ema20 && ema20 <= ema50 && !supertrendBull -> MarketBias.BEARISH
            else -> MarketBias.SIDEWAYS
        }
        val emaRel = when {
            last > ema20 && ema20 > ema50 -> "Price > EMA20 > EMA50"
            last < ema20 && ema20 < ema50 -> "Price < EMA20 < EMA50"
            else -> "Mixed MA"
        }
        val struct = if (supertrendBull) "Bullish HH/HL" else "Bearish LH/LL"
        val trendText = when (bias) {
            MarketBias.BULLISH -> "Bullish"
            MarketBias.BEARISH -> "Bearish"
            else -> "Neutral"
        }
        return MtfTimeframeState(tf, trendText, bias, rsi, emaRel, supertrendBull, struct)
    }

    fun evaluateTechnicalSignal(
        candles: List<Candle>,
        structure: MarketStructureAnalysis,
        srLevels: List<SupportResistanceLevel>,
        volumeProfile: VolumeProfileSummary
    ): TechnicalSignalSummary {
        if (candles.size < 10) {
            val price = candles.lastOrNull()?.close ?: 0.0
            return TechnicalSignalSummary(0, "Neutral", 0, 0, 0, 0, listOf("Insufficient candles for quantitative scoring"), price)
        }

        val closes = candles.map { it.close }
        val last = closes.last()
        val ema20 = TechnicalCalculator.calculateEma(closes, min(20, closes.size))
        val ema50 = TechnicalCalculator.calculateEma(closes, min(50, closes.size))
        val rsi = TechnicalCalculator.calculateRsi(candles, 14)
        val (_, _, macdHist) = TechnicalCalculator.calculateMacd(candles)
        val (_, stBull) = TechnicalCalculator.calculateSupertrend(candles, 10, 3.0)
        val (adx, diPlus, diMinus) = TechnicalCalculator.calculateAdx(candles, 14)
        val atr = TechnicalCalculator.calculateAtr(candles, 14).coerceAtLeast(last * 0.002)

        val reasons = mutableListOf<String>()

        // 1. Trend factor (-35..+35)
        var trendScore = 0
        trendScore += if (last > ema20) 12 else -12
        trendScore += if (ema20 > ema50) 10 else -10
        trendScore += if (stBull) 13 else -13
        if (adx > 22.0) {
            trendScore = (trendScore + if (diPlus > diMinus) 5 else -5).coerceIn(-35, 35)
            reasons.add("Trend ADX ${String.format(Locale.US, "%.1f", adx)} (${if (stBull) "Bullish Supertrend" else "Bearish Supertrend"})")
        }

        // 2. Momentum factor (-25..+25)
        var momScore = 0
        momScore += when {
            rsi > 58.0 && rsi < 75.0 -> 15
            rsi >= 75.0 -> 5 // overbought caution
            rsi < 42.0 && rsi > 25.0 -> -15
            rsi <= 25.0 -> -5
            else -> 0
        }
        momScore += if (macdHist > 0.0) 10 else if (macdHist < 0.0) -10 else 0
        reasons.add("RSI(14)=${String.format(Locale.US, "%.1f", rsi)}, MACD Hist=${String.format(Locale.US, "%.2f", macdHist)}")

        // 3. Volume factor (-20..+20)
        var volScore = 0
        if (volumeProfile.pocPrice > 0.0) {
            volScore += if (last >= volumeProfile.pocPrice) 10 else -10
        }
        volScore += if (volumeProfile.upVolumePct >= 55.0) 10 else if (volumeProfile.upVolumePct <= 45.0) -10 else 0
        reasons.add("Volume Delta ${String.format(Locale.US, "%.0f", volumeProfile.upVolumePct)}% Bull | RVOL ${String.format(Locale.US, "%.2f", volumeProfile.rvolRatio)}x")

        // 4. Structure & S/R alignment (-20..+20)
        var structScore = 0
        val latestBreak = structure.structureBreaks.lastOrNull()
        if (latestBreak != null) {
            structScore += if (latestBreak.isBullish) 12 else -12
            reasons.add("Latest Structure: ${latestBreak.type} (${if (latestBreak.isBullish) "Bullish" else "Bearish"})")
        }
        val nearestSupport = srLevels.filter { it.isSupport }.minByOrNull { abs(it.price - last) }
        val nearestResist = srLevels.filter { !it.isSupport }.minByOrNull { abs(it.price - last) }
        if (nearestSupport != null && (last - nearestSupport.price) < atr * 1.2) structScore += 8
        if (nearestResist != null && (nearestResist.price - last) < atr * 0.8) structScore -= 8
        structScore = structScore.coerceIn(-20, 20)

        val total = (trendScore + momScore + volScore + structScore).coerceIn(-100, 100)
        val rating = when {
            total >= 55 -> "Strong Buy"
            total >= 20 -> "Buy"
            total <= -55 -> "Strong Sell"
            total <= -20 -> "Sell"
            else -> "Neutral"
        }

        val invalidation = if (total >= 0) {
            (nearestSupport?.price ?: (last - atr * 1.8))
        } else {
            (nearestResist?.price ?: (last + atr * 1.8))
        }

        return TechnicalSignalSummary(
            score = total,
            rating = rating,
            trendScore = trendScore,
            momentumScore = momScore,
            volumeScore = volScore,
            structureScore = structScore,
            reasons = reasons,
            invalidationPrice = invalidation
        )
    }

    fun calculateRiskPosition(
        capital: Double,
        riskPct: Double,
        entryPrice: Double,
        customStopLoss: Double?,
        atr14: Double,
        isLong: Boolean,
        rewardRiskRatio: Double = 2.0,
        lotSize: Int = 75
    ): RiskPositionPlan {
        val safeCap = capital.coerceAtLeast(1000.0)
        val safeRiskPct = riskPct.coerceIn(0.1, 20.0)
        val riskAmount = safeCap * (safeRiskPct / 100.0)
        val safeEntry = entryPrice.coerceAtLeast(1.0)
        val safeAtr = atr14.coerceAtLeast(safeEntry * 0.003)
        val safeLotSize = lotSize.coerceAtLeast(1)

        val atrSl15 = if (isLong) safeEntry - 1.5 * safeAtr else safeEntry + 1.5 * safeAtr
        val atrSl20 = if (isLong) safeEntry - 2.0 * safeAtr else safeEntry + 2.0 * safeAtr
        val stopLoss = customStopLoss ?: atrSl15

        val riskPerUnit = abs(safeEntry - stopLoss).coerceAtLeast(0.5)
        val dir = if (isLong) 1.0 else -1.0
        val t1 = safeEntry + dir * riskPerUnit * 1.5
        val t2 = safeEntry + dir * riskPerUnit * rewardRiskRatio
        val t3 = safeEntry + dir * riskPerUnit * 3.0

        val shares = (riskAmount / riskPerUnit).toInt().coerceAtLeast(1)
        val lots = (riskAmount / (riskPerUnit * safeLotSize.toDouble())).toInt().coerceAtLeast(1)
        val positionValue = shares * safeEntry

        return RiskPositionPlan(
            capital = safeCap,
            riskPct = safeRiskPct,
            riskAmountInr = riskAmount,
            entryPrice = safeEntry,
            stopLossPrice = stopLoss,
            atrStop15x = atrSl15,
            atrStop20x = atrSl20,
            target1 = t1,
            target2 = t2,
            target3 = t3,
            recommendedShares = shares,
            recommendedNiftyLots = lots,
            positionValueInr = positionValue,
            rewardRiskRatio = rewardRiskRatio,
            isLong = isLong,
            lotSize = safeLotSize
        )
    }

    /**
     * Zero look-ahead bias historical backtester: signal evaluated only on bar i (using <= i history),
     * filled on bar i close / next bar transition with realistic slippage + per-order brokerage.
     */
    fun runBacktest(candles: List<Candle>, config: BacktestConfig): BacktestResult {
        if (candles.size < 30) {
            return BacktestResult(
                config = config,
                trades = emptyList(),
                equityCurve = candles.map { it.timestamp to config.initialCapital },
                totalReturnPct = 0.0,
                buyAndHoldReturnPct = 0.0,
                winRatePct = 0.0,
                profitFactor = 0.0,
                maxDrawdownPct = 0.0,
                sharpeRatio = 0.0,
                netProfitInr = 0.0
            )
        }

        val closes = candles.map { it.close }
        val fastEma = TechnicalCalculator.calculateEmaSeries(closes, max(2, config.fastPeriod))
        val slowEma = TechnicalCalculator.calculateEmaSeries(closes, max(3, config.slowPeriod))
        val rsi = IndicatorCalculationEngine.rsiSeries(candles, 14)
        val supertrend = IndicatorCalculationEngine.supertrendSeries(candles, 10, 3.0)
        val (macdLine, macdSignal, _) = IndicatorCalculationEngine.macdSeries(closes, 12, 26, 9)
        val (adx, _, _) = IndicatorCalculationEngine.adxSeries(candles, 14)
        val (donHigh, _, donLow) = IndicatorCalculationEngine.donchianSeries(candles, 20)

        var equity = config.initialCapital
        var peakEquity = equity
        var maxDrawdownPct = 0.0

        var inPosition = false
        var entryPrice = 0.0
        var entryTs = 0L
        var qty = 0

        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        val tradeReturns = mutableListOf<Double>()

        val warmup = 26.coerceAtMost(candles.size - 2)
        for (i in 0 until warmup) {
            equityCurve.add(candles[i].timestamp to equity)
        }

        for (i in warmup until candles.size) {
            val c = candles[i]
            val slippageFactor = config.slippagePct / 100.0

            val enterLong = when (config.strategyId) {
                BacktestStrategyId.EMA_CROSSOVER ->
                    fastEma[i - 1] <= slowEma[i - 1] && fastEma[i] > slowEma[i]
                BacktestStrategyId.RSI_MEAN_REVERSION ->
                    rsi[i - 1] < 32.0 && rsi[i] >= 32.0
                BacktestStrategyId.SUPERTREND_TREND ->
                    !supertrend[i - 1].second && supertrend[i].second
                BacktestStrategyId.MACD_ADX_MOMENTUM ->
                    macdLine[i - 1] <= macdSignal[i - 1] && macdLine[i] > macdSignal[i] && adx[i] >= 18.0
                BacktestStrategyId.DONCHIAN_BREAKOUT ->
                    c.close > donHigh[i - 1]
            }

            val exitLong = when (config.strategyId) {
                BacktestStrategyId.EMA_CROSSOVER -> fastEma[i] < slowEma[i]
                BacktestStrategyId.RSI_MEAN_REVERSION -> rsi[i] >= 62.0 || c.close < entryPrice * 0.97
                BacktestStrategyId.SUPERTREND_TREND -> !supertrend[i].second
                BacktestStrategyId.MACD_ADX_MOMENTUM -> macdLine[i] < macdSignal[i]
                BacktestStrategyId.DONCHIAN_BREAKOUT -> c.close < donLow[i - 1]
            }

            if (!inPosition && enterLong && equity > 1000.0) {
                entryPrice = c.close * (1.0 + slippageFactor)
                entryTs = c.timestamp
                val deployable = equity * 0.95
                qty = (deployable / entryPrice).toInt().coerceAtLeast(1)
                equity -= config.brokeragePerOrder
                inPosition = true
            } else if (inPosition && (exitLong || i == candles.lastIndex)) {
                val exitPrice = c.close * (1.0 - slippageFactor)
                val grossPnl = (exitPrice - entryPrice) * qty
                val netPnl = grossPnl - config.brokeragePerOrder
                val costBasis = entryPrice * qty
                val retPct = if (costBasis > 0.0) (netPnl / costBasis) * 100.0 else 0.0
                equity += netPnl
                tradeReturns.add(retPct)
                trades.add(
                    BacktestTrade(
                        entryTimestamp = entryTs,
                        exitTimestamp = c.timestamp,
                        side = "LONG",
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        quantity = qty,
                        netPnl = netPnl,
                        returnPct = retPct,
                        exitReason = if (i == candles.lastIndex) "End of Window" else "Rule Exit"
                    )
                )
                inPosition = false
            }

            val markToMarket = if (inPosition) equity + (c.close - entryPrice) * qty else equity
            if (markToMarket > peakEquity) peakEquity = markToMarket
            val dd = if (peakEquity > 0.0) ((peakEquity - markToMarket) / peakEquity) * 100.0 else 0.0
            if (dd > maxDrawdownPct) maxDrawdownPct = dd
            equityCurve.add(c.timestamp to markToMarket)
        }

        val finalEquity = equityCurve.lastOrNull()?.second ?: config.initialCapital
        val totalReturnPct = ((finalEquity - config.initialCapital) / config.initialCapital) * 100.0
        val bhStart = candles[warmup].close
        val bhEnd = candles.last().close
        val buyHoldPct = if (bhStart > 0.0) ((bhEnd - bhStart) / bhStart) * 100.0 else 0.0

        val wins = trades.filter { it.netPnl > 0.0 }
        val losses = trades.filter { it.netPnl <= 0.0 }
        val winRate = if (trades.isNotEmpty()) (wins.size.toDouble() / trades.size) * 100.0 else 0.0
        val grossWin = wins.sumOf { it.netPnl }
        val grossLoss = abs(losses.sumOf { it.netPnl })
        val profitFactor = if (grossLoss > 0.0) grossWin / grossLoss else if (grossWin > 0.0) 9.99 else 0.0

        val sharpe = if (tradeReturns.size >= 2) {
            val mean = tradeReturns.average()
            val variance = tradeReturns.map { (it - mean) * (it - mean) }.average()
            val std = sqrt(variance)
            if (std > 0.0) (mean / std) * sqrt(tradeReturns.size.toDouble()) else 0.0
        } else 0.0

        return BacktestResult(
            config = config,
            trades = trades,
            equityCurve = equityCurve,
            totalReturnPct = totalReturnPct,
            buyAndHoldReturnPct = buyHoldPct,
            winRatePct = winRate,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdownPct,
            sharpeRatio = sharpe,
            netProfitInr = finalEquity - config.initialCapital
        )
    }
}
