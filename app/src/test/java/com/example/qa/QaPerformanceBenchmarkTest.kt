package com.example.qa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.engine.chart.IndicatorCalculationEngine
import com.example.engine.chart.SignalRiskAndBacktestEngine
import com.example.model.BacktestConfig
import com.example.model.Candle
import com.example.niftychart.data.local.NiftyDatabase
import com.example.niftychart.data.local.NiftyOHLCVEntity
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

/**
 * STAGE 12 — Performance, Memory, and Scalability Baseline Benchmarks
 *
 * Establishes empirical baselines for Small (100), Medium (5,000), and Large (20,000) candle datasets
 * across technical indicators, historical backtesting, and Room batch/dedup SQLite operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QaPerformanceBenchmarkTest {

    private fun generateCandles(count: Int): List<Candle> {
        val baseTs = 1_700_000_000_000L
        var price = 22000.0
        return ArrayList<Candle>(count).apply {
            for (i in 0 until count) {
                val delta = if (i % 5 == 0) -11.5 else 9.2
                val open = price
                val close = open + delta
                price = close
                add(
                    Candle(
                        timestamp = baseTs + i * 60_000L,
                        open = open,
                        high = maxOf(open, close) + 6.0,
                        low = minOf(open, close) - 5.0,
                        close = close,
                        volume = 100_000L + (i % 100) * 1000L,
                        vwap = (open + close) / 2.0
                    )
                )
            }
        }
    }

    private fun usedHeapMb(): Double {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
    }

    @Test
    fun `QA-PERF-01 Indicator engine and backtest scale cleanly across Small, Medium, and Large datasets`() {
        val sma = CalculateSMAUseCase()
        val rsi = CalculateRSIUseCase()
        val configs = IndicatorCalculationEngine.defaultConfigs().map { it.copy(enabled = true) }

        listOf(100, 5_000, 20_000).forEach { size ->
            val candles = generateCandles(size)
            val closes = candles.map { it.close }
            val memBefore = usedHeapMb()

            val smaMs = measureNanoTime {
                val out = sma.calculateFromCloses(closes, 200)
                assertEquals(size, out.size)
            } / 1_000_000.0

            val rsiMs = measureNanoTime {
                val out = rsi.calculateFromCloses(closes, 14)
                assertEquals(size, out.values.size)
            } / 1_000_000.0

            val engineMs = measureNanoTime {
                val out = IndicatorCalculationEngine.computeAll(candles, configs)
                assertEquals(size, out.rsiSeries.size)
            } / 1_000_000.0

            val backtestMs = measureNanoTime {
                val res = SignalRiskAndBacktestEngine.runBacktest(candles, BacktestConfig())
                assertTrue(res.equityCurve.isNotEmpty())
            } / 1_000_000.0

            val memDelta = usedHeapMb() - memBefore
            println(
                "[QA-BENCH] Compute size=$size smaMs=${"%.2f".format(smaMs)} rsiMs=${"%.2f".format(rsiMs)} " +
                    "engineAllMs=${"%.2f".format(engineMs)} backtestMs=${"%.2f".format(backtestMs)} heapDeltaMb=${"%.2f".format(memDelta)}"
            )
            assertTrue("Indicator compute exceeded 5s budget on $size bars", engineMs < 5_000.0)
        }
    }

    @Test
    fun `QA-PERF-02 Room batch insert, query latest N, and repeated upsert deduplication throughput`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NiftyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.niftyOhlcvDao()
            val entities5k = generateCandles(5_000).mapIndexed { idx, c ->
                NiftyOHLCVEntity(
                    symbol = "NIFTY 50",
                    timestamp = c.timestamp,
                    interval = "1m",
                    open = c.open,
                    high = c.high,
                    low = c.low,
                    close = c.close,
                    volume = c.volume,
                    createdAt = c.timestamp,
                    updatedAt = c.timestamp + idx
                )
            }

            val batchInsertMs = measureNanoTime {
                dao.insertCandlesBatch(entities5k)
            } / 1_000_000.0

            val query200Ms = measureNanoTime {
                val latest = dao.getLatestNCandles("NIFTY 50", "1m", 200)
                assertEquals(200, latest.size)
            } / 1_000_000.0

            // Upsert 500 bars via row-by-row deduplicated upsert transaction
            val upsert500Ms = measureNanoTime {
                dao.upsertCandlesDeduplicated(entities5k.takeLast(500))
            } / 1_000_000.0

            println(
                "[QA-BENCH] Room 5000 batchInsertMs=${"%.2f".format(batchInsertMs)} " +
                    "queryLatest200Ms=${"%.2f".format(query200Ms)} upsertDedup500Ms=${"%.2f".format(upsert500Ms)}"
            )
            assertEquals(5_000, dao.getCandleCount("NIFTY 50", "1m"))
            assertTrue("Query latest 200 exceeded 500ms", query200Ms < 500.0)
        } finally {
            db.close()
        }
    }
}
