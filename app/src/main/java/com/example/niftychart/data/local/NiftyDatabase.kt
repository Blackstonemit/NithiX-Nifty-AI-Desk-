package com.example.niftychart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Dedicated Room database for offline-first NIFTY 50 & NSE OHLCV market history.
 * Uses explicit versioned migrations to protect locally stored historical bars across schema updates.
 */
@Database(
    entities = [NiftyOHLCVEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(NiftyTypeConverters::class)
abstract class NiftyDatabase : RoomDatabase() {

    abstract fun niftyOhlcvDao(): NiftyOHLCVDao

    companion object {
        const val DATABASE_NAME = "nifty_market_ohlcv.db"

        /**
         * Explicit migration from v1 to v2 ensuring deduplication cleanup and unique composite index
         * enforcement on (symbol, interval, timestamp) without losing historical market candles.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM nifty_ohlcv
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM nifty_ohlcv GROUP BY symbol, interval, timestamp
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nifty_ohlcv_symbol ON nifty_ohlcv(symbol)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nifty_ohlcv_timestamp ON nifty_ohlcv(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nifty_ohlcv_interval ON nifty_ohlcv(interval)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_nifty_ohlcv_symbol_interval_timestamp " +
                        "ON nifty_ohlcv(symbol, interval, timestamp)"
                )
            }
        }

        @Volatile
        private var INSTANCE: NiftyDatabase? = null

        fun getInstance(context: Context): NiftyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NiftyDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
