package com.example.niftychart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.niftychart.domain.model.OHLCV
import kotlinx.coroutines.flow.Flow

@Dao
interface NiftyOHLCVDao {

    /**
     * Inserts a single candle record. Duplicate (symbol, interval, timestamp) records are ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandle(candle: NiftyOHLCVEntity): Long

    /**
     * Batch-inserts multiple candle records cleanly while ignoring duplicate keys.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandlesBatch(candles: List<NiftyOHLCVEntity>): List<Long>

    /**
     * Updates an existing candle record by primary key.
     */
    @Update
    suspend fun updateCandle(candle: NiftyOHLCVEntity): Int

    /**
     * Checks whether a candle already exists for a given (symbol, interval, timestamp).
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval AND timestamp = :timestamp)"
    )
    suspend fun candleExists(symbol: String, interval: String, timestamp: Long): Boolean

    @Query(
        "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval AND timestamp = :timestamp LIMIT 1"
    )
    suspend fun getCandleAt(symbol: String, interval: String, timestamp: Long): NiftyOHLCVEntity?

    /**
     * Deduplicated upsert that preserves existing row id & createdAt timestamp if the candle
     * already exists, or inserts a new row otherwise.
     */
    @Transaction
    suspend fun upsertCandleDeduplicated(candle: NiftyOHLCVEntity): Long {
        val existing = getCandleAt(candle.symbol, candle.interval, candle.timestamp)
        return if (existing == null) {
            insertCandle(candle)
        } else {
            val updated = existing.copy(
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume,
                updatedAt = candle.updatedAt
            )
            updateCandle(updated)
            existing.id
        }
    }

    @Transaction
    suspend fun upsertCandlesDeduplicated(candles: List<NiftyOHLCVEntity>): Int {
        var affected = 0
        for (candle in candles) {
            val existing = getCandleAt(candle.symbol, candle.interval, candle.timestamp)
            if (existing == null) {
                val insertedId = insertCandle(candle)
                if (insertedId != -1L) affected++
            } else if (
                existing.open != candle.open ||
                existing.high != candle.high ||
                existing.low != candle.low ||
                existing.close != candle.close ||
                existing.volume != candle.volume
            ) {
                updateCandle(
                    existing.copy(
                        open = candle.open,
                        high = candle.high,
                        low = candle.low,
                        close = candle.close,
                        volume = candle.volume,
                        updatedAt = candle.updatedAt
                    )
                )
                affected++
            }
        }
        return affected
    }

    /**
     * Retrieves the most recent stored NIFTY 50 price candle across intervals.
     */
    @Query("SELECT * FROM nifty_ohlcv WHERE symbol = :symbol ORDER BY timestamp DESC, updated_at DESC LIMIT 1")
    suspend fun getLatestPrice(symbol: String = OHLCV.SYMBOL_NIFTY_50): NiftyOHLCVEntity?

    @Query("SELECT * FROM nifty_ohlcv WHERE symbol = :symbol ORDER BY timestamp DESC, updated_at DESC LIMIT 1")
    fun observeLatestPrice(symbol: String = OHLCV.SYMBOL_NIFTY_50): Flow<NiftyOHLCVEntity?>

    /**
     * Retrieves historical OHLCV records within an inclusive timestamp range.
     */
    @Query(
        "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval " +
            "AND timestamp BETWEEN :startTimestamp AND :endTimestamp " +
            "ORDER BY timestamp ASC"
    )
    fun observeCandlesInRange(
        symbol: String,
        interval: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<NiftyOHLCVEntity>>

    @Query(
        "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval " +
            "AND timestamp BETWEEN :startTimestamp AND :endTimestamp " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getCandlesInRange(
        symbol: String,
        interval: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): List<NiftyOHLCVEntity>

    /**
     * Observes all historical candles for a specific timeframe reactively via Kotlin Flow.
     */
    @Query(
        "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval " +
            "ORDER BY timestamp ASC"
    )
    fun observeCandlesByInterval(symbol: String, interval: String): Flow<List<NiftyOHLCVEntity>>

    /**
     * Observes the latest N candles ordered chronologically ascending for direct chart rendering.
     */
    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval " +
            "ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC"
    )
    fun observeLatestNCandles(
        symbol: String,
        interval: String,
        limit: Int
    ): Flow<List<NiftyOHLCVEntity>>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval " +
            "ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC"
    )
    suspend fun getLatestNCandles(
        symbol: String,
        interval: String,
        limit: Int
    ): List<NiftyOHLCVEntity>

    @Query(
        "SELECT MAX(timestamp) FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval"
    )
    suspend fun getLatestTimestamp(symbol: String, interval: String): Long?

    @Query(
        "SELECT COUNT(*) FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval"
    )
    suspend fun getCandleCount(symbol: String, interval: String): Int

    /**
     * Deletes outdated historical records older than cutoffTimestamp.
     */
    @Query("DELETE FROM nifty_ohlcv WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOutdatedRecords(cutoffTimestamp: Long): Int

    @Query(
        "DELETE FROM nifty_ohlcv " +
            "WHERE symbol = :symbol AND interval = :interval AND timestamp < :cutoffTimestamp"
    )
    suspend fun deleteOutdatedRecordsForInterval(
        symbol: String,
        interval: String,
        cutoffTimestamp: Long
    ): Int
}
