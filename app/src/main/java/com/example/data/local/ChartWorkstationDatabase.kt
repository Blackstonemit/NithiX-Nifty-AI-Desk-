package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlist_symbols")
data class WatchlistSymbolEntity(
    @PrimaryKey val symbol: String,
    val displayName: String,
    val yahooTicker: String,
    val indstocksScripCode: String?,
    val exchange: String,
    val isIndex: Boolean,
    val sortOrder: Int,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "chart_layouts")
data class ChartLayoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val themeMode: String,
    val chartType: String,
    val scaleMode: String,
    val indicatorsJson: String,
    val showGrid: Boolean = true,
    val showCountdown: Boolean = true,
    val showAfterHours: Boolean = false,
    val isDefault: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "chart_drawings")
data class ChartDrawingEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val timeframe: String,
    val toolType: String,
    val pointsJson: String,
    val colorHex: Long,
    val strokeWidth: Float,
    val isDashed: Boolean,
    val isLocked: Boolean,
    val isHidden: Boolean,
    val text: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "llm_provider_configs")
data class LlmProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String,
    val temperature: Double,
    val isActive: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "backtest_runs")
data class BacktestRunEntity(
    @PrimaryKey val id: String,
    val strategyName: String,
    val symbol: String,
    val timeframe: String,
    val totalReturnPct: Double,
    val winRatePct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val sharpeRatio: Double,
    val tradesCount: Int,
    val executedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_symbols ORDER BY sortOrder ASC, addedAtMs ASC")
    fun observeWatchlist(): Flow<List<WatchlistSymbolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSymbol(item: WatchlistSymbolEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<WatchlistSymbolEntity>)

    @Query("DELETE FROM watchlist_symbols WHERE symbol = :symbol")
    suspend fun deleteSymbol(symbol: String)
}

@Dao
interface ChartLayoutDao {
    @Query("SELECT * FROM chart_layouts ORDER BY isDefault DESC, updatedAtMs DESC")
    fun observeLayouts(): Flow<List<ChartLayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayout(layout: ChartLayoutEntity)

    @Query("DELETE FROM chart_layouts WHERE id = :id")
    suspend fun deleteLayout(id: String)
}

@Dao
interface ChartDrawingDao {
    @Query("SELECT * FROM chart_drawings WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY updatedAtMs ASC")
    fun observeDrawings(symbol: String, timeframe: String): Flow<List<ChartDrawingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrawing(drawing: ChartDrawingEntity)

    @Query("DELETE FROM chart_drawings WHERE id = :id AND isLocked = 0")
    suspend fun deleteDrawing(id: String)

    @Query("DELETE FROM chart_drawings WHERE symbol = :symbol AND timeframe = :timeframe AND isLocked = 0")
    suspend fun clearDrawings(symbol: String, timeframe: String)
}

@Dao
interface LlmProviderDao {
    @Query("SELECT * FROM llm_provider_configs ORDER BY isActive DESC, providerType ASC")
    fun observeConfigs(): Flow<List<LlmProviderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: LlmProviderConfigEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(configs: List<LlmProviderConfigEntity>)

    @Query("UPDATE llm_provider_configs SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveProvider(activeId: String)
}

@Dao
interface BacktestRunDao {
    @Query("SELECT * FROM backtest_runs ORDER BY executedAtMs DESC LIMIT 20")
    fun observeRecentRuns(): Flow<List<BacktestRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: BacktestRunEntity)
}

@Database(
    entities = [
        WatchlistSymbolEntity::class,
        ChartLayoutEntity::class,
        ChartDrawingEntity::class,
        LlmProviderConfigEntity::class,
        BacktestRunEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChartWorkstationDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun layoutDao(): ChartLayoutDao
    abstract fun drawingDao(): ChartDrawingDao
    abstract fun llmProviderDao(): LlmProviderDao
    abstract fun backtestRunDao(): BacktestRunDao

    companion object {
        @Volatile
        private var INSTANCE: ChartWorkstationDatabase? = null

        fun getInstance(context: Context): ChartWorkstationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChartWorkstationDatabase::class.java,
                    "chart_workstation.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
