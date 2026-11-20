package com.example.niftychart.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.OHLCV
import java.util.Date

/**
 * Room Entity persisting OHLCV market candles locally.
 * Enforces a composite unique index on (symbol, interval, timestamp) to eliminate duplicate
 * candles across incremental historical syncs and real-time tick aggregations.
 */
@Entity(
    tableName = "nifty_ohlcv",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["timestamp"]),
        Index(value = ["interval"]),
        Index(value = ["symbol", "interval", "timestamp"], unique = true)
    ]
)
data class NiftyOHLCVEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val symbol: String,
    val timestamp: Long,
    val interval: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    fun toDomain(): OHLCV = OHLCV(
        id = id,
        symbol = symbol,
        timestamp = timestamp,
        interval = ChartInterval.fromCode(interval),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(ohlcv: OHLCV): NiftyOHLCVEntity = NiftyOHLCVEntity(
            id = ohlcv.id,
            symbol = ohlcv.symbol,
            timestamp = ohlcv.timestamp,
            interval = ohlcv.interval.code,
            open = ohlcv.open,
            high = maxOf(ohlcv.high, ohlcv.open, ohlcv.close),
            low = minOf(ohlcv.low, ohlcv.open, ohlcv.close),
            close = ohlcv.close,
            volume = ohlcv.volume.coerceAtLeast(0L),
            createdAt = ohlcv.createdAt,
            updatedAt = ohlcv.updatedAt
        )
    }
}

/**
 * Room type converters for Date and ChartInterval domain value conversions.
 */
class NiftyTypeConverters {
    @TypeConverter
    fun fromChartInterval(interval: ChartInterval?): String? = interval?.code

    @TypeConverter
    fun toChartInterval(code: String?): ChartInterval? = code?.let { ChartInterval.fromCode(it) }

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(epochMs: Long?): Date? = epochMs?.let { Date(it) }
}
