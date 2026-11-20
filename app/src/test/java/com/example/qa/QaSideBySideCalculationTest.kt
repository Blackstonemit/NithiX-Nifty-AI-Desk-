package com.example.qa

import com.example.engine.OptionsGreeksCalculator
import com.example.engine.TechnicalCalculator
import com.example.engine.chart.IndicatorCalculationEngine
import com.example.engine.chart.SignalRiskAndBacktestEngine
import com.example.engine.chart.TimeframeAggregationEngine
import com.example.engine.agents.NiftyMultiAgentSystem
import com.example.model.Candle
import com.example.model.IndicatorConfig
import com.example.model.IndicatorType
import com.example.model.Timeframe
import com.example.network.IndstocksApiClient
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * STAGE 7 — Side-by-Side Mathematical & Cross-Module Parity Audit
 *
 * Compares independent financial mathematical oracles and checks consistency across
 * duplicated indicator engines, Greeks calculations, timeframe aggregation, and F&O lot sizing.
 */
class QaSideBySideCalculationTest {

    private fun deterministicTrendingCandles(count: Int = 80): List<Candle> {
        val baseTs = 1_740_029_100_000L // 2025-02-20 09:15 IST approx aligned
        var close = 25000.0
        return (0 until count).map { i ->
            val open = close
            val move = if (i % 4 == 0) -12.0 else 18.0
            close = open + move
            val high = maxOf(open, close) + 8.0
            val low = minOf(open, close) - 7.0
            Candle(
                timestamp = baseTs + i * 5 * 60_000L,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = 100_000L + i * 500L,
                vwap = (high + low + close) / 3.0
            )
        }
    }

    private fun flatCandles(count: Int = 40, price: Double = 25250.0): List<Candle> {
        val baseTs = 1_740_029_100_000L
        return (0 until count).map { i ->
            Candle(
                timestamp = baseTs + i * 5 * 60_000L,
                open = price,
                high = price,
                low = price,
                close = price,
                volume = 10_000L,
                vwap = price
            )
        }
    }

    private fun toOhlcvList(candles: List<Candle>): List<OHLCV> = candles.mapIndexed { idx, c ->
        OHLCV(
            id = idx.toLong() + 1,
            symbol = "NIFTY 50",
            timestamp = c.timestamp,
            interval = ChartInterval.M5,
            open = c.open,
            high = c.high,
            low = c.low,
            close = c.close,
            volume = c.volume
        )
    }

    @Test
    fun `QA-S2S-01 Black-Scholes Greeks satisfy Put-Call Parity and Gamma symmetry`() {
        val spot = 25800.0
        val strike = 25800.0
        val iv = 16.0
        val days = 7.0
        val rate = 0.068
        val t = days / 365.0

        val call = OptionsGreeksCalculator.calculateCallGreeks(spot, strike, iv, days, rate)
        val put = OptionsGreeksCalculator.calculatePutGreeks(spot, strike, iv, days, rate)

        // C - P = S - K * e^(-r*t)
        val lhs = call.price - put.price
        val rhs = spot - strike * exp(-rate * t)
        assertEquals("Put-Call parity mismatch", rhs, lhs, 0.25)

        // Delta(Call) - Delta(Put) = 1.0
        assertEquals(1.0, call.delta - put.delta, 0.005)
        // Gamma(Call) == Gamma(Put)
        assertEquals(call.gamma, put.gamma, 1e-6)
        assertTrue(call.delta in 0.0..1.0)
        assertTrue(put.delta in -1.0..0.0)
    }

    @Test
    fun `QA-S2S-02 Independent SMA oracle matches CalculateSMAUseCase and IndicatorCalculationEngine`() {
        val candles = deterministicTrendingCandles(60)
        val closes = candles.map { it.close }
        val period = 20

        val smaUseCase = CalculateSMAUseCase().calculateFromCloses(closes, period)
        val engineSeries = IndicatorCalculationEngine.computeAll(
            candles,
            listOf(IndicatorConfig("sma20", IndicatorType.SMA, enabled = true, period = period))
        ).overlayLines["sma20"]!!

        // Independent manual average of last 20 closes
        val oracleLast = closes.takeLast(period).average()
        assertEquals(oracleLast, smaUseCase.last()!!, 1e-6)
        assertEquals(oracleLast, engineSeries.last()!!, 1e-6)
    }

    private fun indicatorConfigsForParity(): List<IndicatorConfig> = listOf(
        IndicatorConfig("rsi14", IndicatorType.RSI, enabled = true, period = 14),
        IndicatorConfig("atr14", IndicatorType.ATR, enabled = true, period = 14),
        IndicatorConfig("macd", IndicatorType.MACD, enabled = true, period = 12, secondaryPeriod = 26, tertiaryPeriod = 9)
    )

    @Test
    fun `QA-S2S-03 Wilder RSI matches across CalculateRSIUseCase, TechnicalCalculator, and IndicatorCalculationEngine on trending data`() {
        val candles = deterministicTrendingCandles(80)
        val rsiUseCase = CalculateRSIUseCase().calculateFromCloses(candles.map { it.close }, 14).latestValue!!
        val techRsi = TechnicalCalculator.calculateRsi(candles, 14)
        val engineRsi = IndicatorCalculationEngine.computeAll(candles, indicatorConfigsForParity()).rsiSeries.last()

        assertEquals(rsiUseCase, techRsi, 0.01)
        assertEquals(rsiUseCase, engineRsi, 0.01)
    }

    @Test
    fun `QA-S2S-04 BUG-FUN-05 Side-by-side flat-price RSI returns 50 neutral across all modules`() {
        val flat = flatCandles(40, price = 25000.0)
        val useCaseRsi = CalculateRSIUseCase().calculateFromCloses(flat.map { it.close }, 14).latestValue!!
        val techRsi = TechnicalCalculator.calculateRsi(flat, 14)
        val engineRsi = IndicatorCalculationEngine.computeAll(flat, indicatorConfigsForParity()).rsiSeries.last()

        // Expected neutral 50.0 when avgGain == 0 && avgLoss == 0
        assertEquals("CalculateRSIUseCase must return 50.0 on flat closes", 50.0, useCaseRsi, 1e-6)
        assertEquals(
            "BUG-FUN-05: TechnicalCalculator & IndicatorCalculationEngine must agree with 50.0 neutral RSI on flat prices (not 100.0)",
            useCaseRsi,
            techRsi,
            0.01
        )
        assertEquals(useCaseRsi, engineRsi, 0.01)
    }

    @Test
    fun `QA-S2S-05 BUG-FUN-05 Side-by-side ATR matches between TechnicalCalculator and IndicatorCalculationEngine`() {
        val candles = deterministicTrendingCandles(60)
        val techAtr = TechnicalCalculator.calculateAtr(candles, 14)
        val engineAtr = IndicatorCalculationEngine.computeAll(candles, indicatorConfigsForParity()).atrSeries.last()

        // Both compute ATR(14) for the exact same dataset; divergence > 0.5 pts indicates SMA vs Wilder mismatch
        assertEquals(
            "BUG-FUN-05: TechnicalCalculator.calculateAtr ($techAtr) diverges from IndicatorCalculationEngine.atrSeries ($engineAtr)",
            engineAtr,
            techAtr,
            0.5
        )
    }

    @Test
    fun `QA-S2S-06 BUG-FUN-05 Side-by-side MACD(12,26,9) matches between TechnicalCalculator and IndicatorCalculationEngine`() {
        val candles = deterministicTrendingCandles(60)
        val (techMacd, techSignal, _) = TechnicalCalculator.calculateMacd(candles)
        val (engineMacdSeries, engineSignalSeries, _) = IndicatorCalculationEngine.computeAll(candles, indicatorConfigsForParity()).macdSeries
        val engineMacd = engineMacdSeries.last()
        val engineSignal = engineSignalSeries.last()

        assertEquals(engineMacd, techMacd, 0.05)
        assertEquals(
            "BUG-FUN-05: MACD signal line mismatch due to different warm-up slices ($techSignal vs $engineSignal)",
            engineSignal,
            techSignal,
            0.25
        )
    }

    @Test
    fun `QA-S2S-07 BUG-FUN-06 Offline Risk & Options sizing uses consistent NSE NIFTY lot size`() {
        val plan = SignalRiskAndBacktestEngine.calculateRiskPosition(
            capital = 200_000.0,
            riskPct = 1.5,
            entryPrice = 25800.0,
            customStopLoss = 25740.0, // riskPerUnit = 60 pts; riskAmount = 3,000
            atr14 = 40.0,
            isLong = true
        )
        // Broker client & Indstocks API default is 75 units/lot, while SignalRiskAndBacktestEngine divides by 25
        val indstocksClientDefaultLot = 75
        val riskAmount = 3000.0
        val expectedLotsAt75 = (riskAmount / (60.0 * indstocksClientDefaultLot)).toInt().coerceAtLeast(1) // 1 lot
        // At lotSize=25, floor(3000/(60*25)) = 2 lots vs floor(3000/(60*75)) = 1 lot
        assertEquals(
            "BUG-FUN-06: Offline risk lot sizing ($ {plan.recommendedNiftyLots} using hardcoded 25) mismatches NSE 75 lot size",
            expectedLotsAt75,
            plan.recommendedNiftyLots
        )
    }

    @Test
    fun `QA-S2S-08 TimeframeAggregationEngine preserves OHLCV conservation and Heikin-Ashi invariants`() {
        val candles5m = deterministicTrendingCandles(12)
        val agg10m = TimeframeAggregationEngine.aggregateCandlesForTimeframe(candles5m, Timeframe.M10)

        assertTrue(agg10m.isNotEmpty())
        assertEquals(candles5m.sumOf { it.volume }, agg10m.sumOf { it.volume })
        agg10m.forEach { c ->
            assertTrue("High >= max(open,close)", c.high >= maxOf(c.open, c.close))
            assertTrue("Low <= min(open,close)", c.low <= minOf(c.open, c.close))
        }

        val ha = TimeframeAggregationEngine.toHeikinAshi(candles5m)
        assertEquals(candles5m.size, ha.size)
        ha.forEachIndexed { idx, h ->
            assertEquals(candles5m[idx].timestamp, h.timestamp)
            assertTrue(abs(h.high - h.low) >= 0.0)
            assertTrue(h.high >= maxOf(h.open, h.close))
            assertTrue(h.low <= minOf(h.open, h.close))
        }
    }
}
