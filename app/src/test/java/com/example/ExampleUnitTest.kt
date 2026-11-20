package com.example

import com.example.data.MarketHoursEngine
import com.example.data.NiftyMarketRepository
import com.example.engine.OptionsGreeksCalculator
import com.example.engine.TechnicalCalculator
import com.example.engine.agents.OptionsExecutionAgent
import com.example.model.AlertAction
import com.example.model.Candle
import com.example.model.ExecutionAction
import com.example.model.FinalTradeRecommendation
import com.example.model.MarketBias
import com.example.model.OptionsStrategyType
import com.example.model.PositionLifecycle
import com.example.model.SignalType
import com.example.network.LiveMarketDataClient
import com.example.network.NseMcpClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ExampleUnitTest {

    private fun fastOfflineClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(150, TimeUnit.MILLISECONDS)
        .readTimeout(150, TimeUnit.MILLISECONDS)
        .build()

    @Test
    fun testBlackScholesGreeksCalculation() {
        val spot = 25150.0
        val strike = 25150.0 // ATM
        val iv = 13.5
        val dte = 4.0

        val callGreeks = OptionsGreeksCalculator.calculateCallGreeks(spot, strike, iv, dte)
        val putGreeks = OptionsGreeksCalculator.calculatePutGreeks(spot, strike, iv, dte)

        assertTrue("Call delta should be positive near 0.5", callGreeks.delta in 0.45..0.58)
        assertTrue("Put delta should be negative near -0.5", putGreeks.delta in -0.58..-0.45)
        assertTrue("Gamma should be positive", callGreeks.gamma > 0.0)
        assertTrue("Call Theta should be negative", callGreeks.theta < 0.0)
        assertTrue("Put Theta should be negative", putGreeks.theta < 0.0)
    }

    @Test
    fun testTechnicalCalculatorEmaSeriesMacdAndStatefulSupertrend() {
        val candles = mutableListOf<Candle>()
        var price = 24800.0
        for (i in 0..34) {
            price += 12.0
            candles.add(
                Candle(
                    timestamp = 1700000000000L + i * 60_000L,
                    open = price - 6.0,
                    high = price + 14.0,
                    low = price - 10.0,
                    close = price,
                    volume = 12_000L,
                    vwap = price - 3.0
                )
            )
        }

        val closes = candles.map { it.close }
        val emaSeries = TechnicalCalculator.calculateEmaSeries(closes, 20)
        assertEquals(candles.size, emaSeries.size)
        assertEquals(TechnicalCalculator.calculateEma(closes, 20), emaSeries.last(), 1e-6)
        assertTrue("EMA20 should trend monotonically up on rising prices", emaSeries.last() > emaSeries.first())

        val rsi = TechnicalCalculator.calculateRsi(candles, 14)
        assertTrue("RSI should reflect upward price movement", rsi > 65.0)

        val (macdLine, macdSignal, _) = TechnicalCalculator.calculateMacd(candles)
        assertTrue("MACD should be positive in steady uptrend", macdLine > 0.0)
        assertFalse(macdSignal.isNaN())

        val (stVal, stBullish) = TechnicalCalculator.calculateSupertrend(candles, 10, 3.0)
        assertTrue("Supertrend should be bullish on steady uptrend", stBullish)
        assertTrue("Supertrend trailing stop should sit below current close", stVal < candles.last().close)

        val zeroVolCandles = candles.map { it.copy(volume = 0L) }
        val fallbackVwap = TechnicalCalculator.calculateVwap(zeroVolCandles)
        assertFalse("VWAP must never be NaN when volume is zero", fallbackVwap.isNaN())
        assertTrue(fallbackVwap > 24800.0)
    }

    @Test
    fun testExecutionAgentPeUsesPutThetaAndLocaleFormatting() {
        val repo = NiftyMarketRepository(
            liveMarketClient = LiveMarketDataClient(fastOfflineClient(), "http://127.0.0.1:1", "http://127.0.0.1:1"),
            nseMcpClient = NseMcpClient(fastOfflineClient(), "http://127.0.0.1:1", "http://127.0.0.1:1")
        )
        val state = repo.deskState.value
        val atmStrike = state.optionsChain.atmStrike
        val customChain = state.optionsChain.copy(
            strikes = state.optionsChain.strikes.map { s ->
                if (s.strikePrice == atmStrike) {
                    s.copy(putLtp = 188.0, callTheta = -9.0, putTheta = -27.0)
                } else s
            }
        )

        val pePosition = PositionLifecycle(
            positionId = "POS-PE-1",
            strategy = OptionsStrategyType.BUY_PE,
            instrument = "NIFTY ${atmStrike.toInt()} PE",
            strike = atmStrike,
            expiry = state.optionsChain.expiryDate,
            lots = 2,
            quantity = 50,
            entryPremium = 135.0,
            currentPremium = 135.0,
            initialStopLoss = 110.0,
            trailingStopLoss = 110.0,
            target1 = 175.0,
            target2 = 220.0,
            target3 = 270.0,
            unrealizedPnl = 0.0,
            returnPct = 0.0,
            status = ExecutionAction.HOLD,
            marginRequired = 6750.0,
            gammaExposure = "ATM PE",
            exitReason = "Active",
            openTimestamp = System.currentTimeMillis()
        )

        val bearTech = state.technicals.copy(supertrendBullish = false, rsi14 = 42.0, vwap = state.spot.spotPrice + 30.0)
        val (updatedPos, alert) = OptionsExecutionAgent.evaluatePositionLifecycle(
            pePosition, state.spot, customChain, bearTech
        )

        assertEquals(ExecutionAction.PARTIAL_EXIT_50, updatedPos.status)
        assertNotNull(alert)
        assertEquals(AlertAction.BOOK_50_PROFIT, alert?.alertType)
        assertTrue("PE alert should report putTheta (-₹27/day)", alert!!.timeDecayImpact.contains("27"))

        val report = OptionsExecutionAgent.generateTradeExecutionReport(listOf(updatedPos))
        assertEquals(1, report.size)
        assertTrue(report[0]["P&L"]!!.contains("2,650"))
    }

    @Test
    fun testBullishAndBearishFuturesPnlPolarity() {
        val repo = NiftyMarketRepository(
            liveMarketClient = LiveMarketDataClient(fastOfflineClient(), "http://127.0.0.1:1", "http://127.0.0.1:1"),
            nseMcpClient = NseMcpClient(fastOfflineClient(), "http://127.0.0.1:1", "http://127.0.0.1:1")
        )
        repo.switchScenario("Bullish Breakout (CE Expansion)")
        val state = repo.deskState.value

        val bullRec = FinalTradeRecommendation(
            direction = MarketBias.BULLISH,
            signal = SignalType.BULLISH_FUTURES,
            instrument = "NIFTY FUT",
            expiryDate = state.futures.currentMonthExpiry,
            strike = state.optionsChain.atmStrike,
            optionType = "FUTURES",
            entryZone = "Current",
            stopLoss = "SL",
            target1 = "T1",
            target2 = "T2",
            target3 = "T3",
            riskToReward = "1 : 2.5",
            expectedHoldingPeriod = "Intraday",
            confidenceScore = 76,
            setupInvalidationLevel = "S1",
            tradeRationale = "Bullish Futures",
            timestamp = System.currentTimeMillis(),
            latencyMs = 15L,
            strategyName = "Swing Futures"
        )
        val bearRec = bullRec.copy(
            direction = MarketBias.BEARISH,
            signal = SignalType.BEARISH_FUTURES,
            instrument = "NIFTY BEAR FUT"
        )

        repo.executeAuthorizedTrade(bullRec, lots = 1)
        repo.executeAuthorizedTrade(bearRec, lots = 1)

        val spotBefore = repo.deskState.value.spot.spotPrice
        repo.simulateMarketTick()
        val spotAfter = repo.deskState.value.spot.spotPrice
        val spotDelta = spotAfter - spotBefore
        assertTrue("Bullish trend tick should rise", spotDelta > 0.0)

        val orders = repo.deskState.value.activeOrders
        val bullFutOrder = orders.first { it.signal == SignalType.BULLISH_FUTURES }
        val bearFutOrder = orders.first { it.signal == SignalType.BEARISH_FUTURES }

        assertTrue("BULLISH_FUTURES price must move UP with rising spot", bullFutOrder.currentPrice > bullFutOrder.entryPrice)
        assertTrue("BEARISH_FUTURES price must move DOWN with rising spot", bearFutOrder.currentPrice < bearFutOrder.entryPrice)
    }

    @Test
    fun testMarketHoursEngineSessionLabelConsistency() {
        val statusPaused = MarketHoursEngine.getMarketSessionStatus(false)
        val statusSim = MarketHoursEngine.getMarketSessionStatus(true)
        assertEquals(statusPaused.isExchangeOpen, statusSim.isExchangeOpen)
        assertFalse(statusPaused.isSimulationActive)
        assertTrue(statusSim.isSimulationActive)
        assertTrue(statusPaused.currentTimeIst.endsWith("IST"))
    }
}
