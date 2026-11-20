package com.example.qa

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BacktestRunEntity
import com.example.data.local.ChartDrawingEntity
import com.example.data.local.ChartLayoutEntity
import com.example.data.local.ChartWorkstationDatabase
import com.example.data.local.LlmProviderConfigEntity
import com.example.data.local.WatchlistSymbolEntity
import com.example.niftychart.data.local.NiftyDatabase
import com.example.niftychart.data.local.NiftyOHLCVEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STAGE 10 — Isolated Room & SQLite Database Audit
 *
 * Audits schema constraints, PRAGMA checks, index coverage, transaction atomicity & rollback,
 * versioned migrations (MIGRATION_1_2), duplicate handling, concurrent write safety,
 * SQL injection immunity via parameterized Room queries, and backup/destructive migration policy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QaDatabaseAuditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var niftyDb: NiftyDatabase
    private lateinit var workstationDb: ChartWorkstationDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        niftyDb = Room.inMemoryDatabaseBuilder(context, NiftyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workstationDb = Room.inMemoryDatabaseBuilder(context, ChartWorkstationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        niftyDb.close()
        workstationDb.close()
    }

    private fun candleEntity(
        symbol: String = "NIFTY 50",
        interval: String = "5m",
        timestamp: Long,
        close: Double = 25000.0
    ): NiftyOHLCVEntity = NiftyOHLCVEntity(
        symbol = symbol,
        timestamp = timestamp,
        interval = interval,
        open = close - 10.0,
        high = close + 15.0,
        low = close - 20.0,
        close = close,
        volume = 100_000L,
        createdAt = timestamp,
        updatedAt = timestamp
    )

    @Test
    fun `QA-DB-01 PRAGMA integrity_check, foreign_key_check, journal_mode, and user_version pass on file-backed DBs`() {
        val niftyFile = tempFolder.newFile("qa_nifty.db")
        val workFile = tempFolder.newFile("qa_workstation.db")

        val fileNiftyDb = Room.databaseBuilder(context, NiftyDatabase::class.java, niftyFile.absolutePath)
            .addMigrations(NiftyDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val fileWorkDb = Room.databaseBuilder(context, ChartWorkstationDatabase::class.java, workFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        try {
            val niftySqlite = fileNiftyDb.openHelper.writableDatabase
            val workSqlite = fileWorkDb.openHelper.writableDatabase

            niftySqlite.query("PRAGMA integrity_check").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0).lowercase())
            }
            workSqlite.query("PRAGMA integrity_check").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0).lowercase())
            }

            // Foreign-key check returns zero rows when valid
            niftySqlite.query("PRAGMA foreign_key_check").use { c ->
                assertEquals(0, c.count)
            }
            workSqlite.query("PRAGMA foreign_key_check").use { c ->
                assertEquals(0, c.count)
            }

            // Journal mode and user_version checks
            niftySqlite.query("PRAGMA journal_mode").use { c ->
                assertTrue(c.moveToFirst())
                val mode = c.getString(0).lowercase()
                assertTrue("Expected WAL or delete/truncate journal mode, got $mode", mode in setOf("wal", "truncate", "delete"))
            }
            niftySqlite.query("PRAGMA user_version").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
            workSqlite.query("PRAGMA user_version").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        } finally {
            fileNiftyDb.close()
            fileWorkDb.close()
        }
    }

    @Test
    fun `QA-DB-02 NiftyDatabase declares all required indices including unique composite index`() {
        val db = niftyDb.openHelper.writableDatabase
        val indexNames = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'nifty_ohlcv'").use { c ->
            while (c.moveToNext()) {
                indexNames.add(c.getString(0))
            }
        }
        assertTrue("Missing symbol index: $indexNames", indexNames.any { it.contains("symbol") })
        assertTrue("Missing timestamp index: $indexNames", indexNames.any { it.contains("timestamp") })
        assertTrue("Missing interval index: $indexNames", indexNames.any { it.contains("interval") })
        assertTrue(
            "Missing composite unique index: $indexNames",
            indexNames.contains("index_nifty_ohlcv_symbol_interval_timestamp")
        )
    }

    @Test
    fun `QA-DB-03 CRUD lifecycle and deduplicated upsert preserve existing ID and createdAt`() = runBlocking {
        val dao = niftyDb.niftyOhlcvDao()
        val first = candleEntity(timestamp = 1_740_000_000_000L, close = 24800.0).copy(
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val insertedId = dao.insertCandle(first)
        assertTrue(insertedId > 0L)
        assertTrue(dao.candleExists("NIFTY 50", "5m", 1_740_000_000_000L))

        // Duplicate raw insert with IGNORE must return -1 and keep row count = 1
        val dupId = dao.insertCandle(first.copy(close = 24999.0))
        assertEquals(-1L, dupId)
        assertEquals(1, dao.getCandleCount("NIFTY 50", "5m"))

        // Deduplicated upsert updates OHLCV while keeping original id and createdAt
        val upsertedId = dao.upsertCandleDeduplicated(
            candleEntity(timestamp = 1_740_000_000_000L, close = 24950.0).copy(
                createdAt = 9999L,
                updatedAt = 5000L
            )
        )
        assertEquals(insertedId, upsertedId)
        val stored = dao.getCandleAt("NIFTY 50", "5m", 1_740_000_000_000L)
        assertNotNull(stored)
        assertEquals(24950.0, stored!!.close, 0.001)
        assertEquals(1000L, stored.createdAt)
        assertEquals(5000L, stored.updatedAt)

        // Delete outdated records
        val deleted = dao.deleteOutdatedRecords(1_740_000_000_001L)
        assertEquals(1, deleted)
        assertNull(dao.getLatestPrice("NIFTY 50"))
    }

    @Test
    fun `QA-DB-04 Transaction rollback prevents partial commits on failure`() = runBlocking {
        val db = niftyDb.openHelper.writableDatabase
        val dao = niftyDb.niftyOhlcvDao()

        dao.insertCandle(candleEntity(timestamp = 1_000_000L, close = 25000.0))
        assertEquals(1, dao.getCandleCount("NIFTY 50", "5m"))

        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO nifty_ohlcv (symbol, timestamp, interval, open, high, low, close, volume, created_at, updated_at) " +
                    "VALUES ('NIFTY 50', 2000000, '5m', 25010.0, 25020.0, 25000.0, 25015.0, 500, 2000, 2000)"
            )
            // Intentionally do NOT call setTransactionSuccessful() to simulate exception / interruption
        } finally {
            db.endTransaction()
        }

        // Only original pre-transaction row should exist
        assertEquals(1, dao.getCandleCount("NIFTY 50", "5m"))
        assertFalse(dao.candleExists("NIFTY 50", "5m", 2_000_000L))
    }

    @Test
    fun `QA-DB-05 MIGRATION_1_2 deduplicates legacy v1 table and preserves latest candle per key`() = runBlocking {
        val v1File = tempFolder.newFile("legacy_v1_nifty.db")
        // Build authentic v1 schema without unique composite index and insert duplicates
        val rawDb = SQLiteDatabase.openOrCreateDatabase(v1File, null)
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nifty_ohlcv (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                symbol TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                interval TEXT NOT NULL,
                open REAL NOT NULL,
                high REAL NOT NULL,
                low REAL NOT NULL,
                close REAL NOT NULL,
                volume INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            "INSERT INTO nifty_ohlcv (id, symbol, timestamp, interval, open, high, low, close, volume, created_at, updated_at) " +
                "VALUES (1, 'NIFTY 50', 1740000000000, '5m', 24000.0, 24050.0, 23990.0, 24010.0, 100, 1, 1)"
        )
        rawDb.execSQL(
            "INSERT INTO nifty_ohlcv (id, symbol, timestamp, interval, open, high, low, close, volume, created_at, updated_at) " +
                "VALUES (2, 'NIFTY 50', 1740000000000, '5m', 24000.0, 24060.0, 23990.0, 24045.0, 250, 1, 2)"
        )
        rawDb.execSQL(
            "INSERT INTO nifty_ohlcv (id, symbol, timestamp, interval, open, high, low, close, volume, created_at, updated_at) " +
                "VALUES (3, 'NIFTY 50', 1740000300000, '5m', 24045.0, 24070.0, 24040.0, 24065.0, 300, 2, 2)"
        )
        rawDb.version = 1
        rawDb.close()

        val migratedDb = Room.databaseBuilder(context, NiftyDatabase::class.java, v1File.absolutePath)
            .addMigrations(NiftyDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = migratedDb.niftyOhlcvDao()
            assertEquals(2, dao.getCandleCount("NIFTY 50", "5m"))
            val kept = dao.getCandleAt("NIFTY 50", "5m", 1_740_000_000_000L)
            assertNotNull(kept)
            assertEquals(2L, kept!!.id)
            assertEquals(24045.0, kept.close, 0.001)
            migratedDb.openHelper.writableDatabase.query("PRAGMA integrity_check").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0).lowercase())
            }
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `QA-DB-06 Parameterized Room queries resist SQL injection payloads`() = runBlocking {
        val dao = niftyDb.niftyOhlcvDao()
        val payload = "NIFTY 50'; DROP TABLE nifty_ohlcv; --"
        dao.insertCandle(candleEntity(symbol = "NIFTY 50", timestamp = 1_000_000L, close = 25100.0))

        val queried = dao.getLatestPrice(payload)
        assertNull(queried)
        // Table still exists and original data is intact
        assertEquals(1, dao.getCandleCount("NIFTY 50", "5m"))

        // Also test ChartWorkstationDatabase WatchlistDao
        val wDao = workstationDb.watchlistDao()
        wDao.upsertSymbol(
            WatchlistSymbolEntity(
                symbol = payload,
                displayName = "Inject Test",
                yahooTicker = "TEST.NS",
                indstocksScripCode = null,
                exchange = "NSE",
                isIndex = false,
                sortOrder = 1
            )
        )
        val items = wDao.observeWatchlist().first()
        assertEquals(1, items.size)
        assertEquals(payload, items.first().symbol)
    }

    @Test
    fun `QA-DB-07 Concurrent multi-coroutine upserts produce zero duplicates or crashes`() = runBlocking {
        val dao = niftyDb.niftyOhlcvDao()
        val jobs = (1..50).map { worker ->
            async {
                val batch = (0 until 20).map { barIdx ->
                    candleEntity(
                        timestamp = 1_740_000_000_000L + barIdx * 60_000L,
                        close = 25000.0 + worker + barIdx
                    )
                }
                dao.insertCandlesBatch(batch)
            }
        }
        jobs.awaitAll()
        assertEquals(20, dao.getCandleCount("NIFTY 50", "5m"))
    }

    @Test
    fun `QA-DB-08 ChartWorkstationDatabase CRUD persists watchlist, layouts, drawings, LLM configs, and backtest runs`() = runBlocking {
        workstationDb.layoutDao().upsertLayout(
            ChartLayoutEntity(
                id = "lay_1",
                name = "QA Layout",
                themeMode = "FINANCIAL_DARK",
                chartType = "CANDLESTICK",
                scaleMode = "LINEAR",
                indicatorsJson = "[]"
            )
        )
        workstationDb.drawingDao().upsertDrawing(
            ChartDrawingEntity(
                id = "drw_1",
                symbol = "NIFTY 50",
                timeframe = "15m",
                toolType = "TRENDLINE",
                pointsJson = "[]",
                colorHex = 0xFF00E5FF,
                strokeWidth = 2f,
                isDashed = false,
                isLocked = true,
                isHidden = false,
                text = "QA"
            )
        )
        workstationDb.llmProviderDao().upsertConfig(
            LlmProviderConfigEntity(
                id = "OPENAI",
                providerType = "OPENAI",
                baseUrl = "https://api.openai.com/v1",
                modelName = "gpt-4o-mini",
                apiKey = "sk-test-redacted",
                temperature = 0.2,
                isActive = true
            )
        )
        workstationDb.backtestRunDao().insertRun(
            BacktestRunEntity(
                id = "bt_1",
                strategyName = "EMA Crossover",
                symbol = "NIFTY 50",
                timeframe = "15m",
                totalReturnPct = 12.4,
                winRatePct = 61.5,
                profitFactor = 1.85,
                maxDrawdownPct = 4.2,
                sharpeRatio = 1.72,
                tradesCount = 13
            )
        )

        assertEquals(1, workstationDb.layoutDao().observeLayouts().first().size)
        assertEquals(1, workstationDb.drawingDao().observeDrawings("NIFTY 50", "15m").first().size)
        assertEquals(1, workstationDb.llmProviderDao().observeConfigs().first().size)
        assertEquals(1, workstationDb.backtestRunDao().observeRecentRuns().first().size)
    }
}
