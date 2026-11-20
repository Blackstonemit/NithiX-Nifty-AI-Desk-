package com.example.niftychart.di

import android.content.Context
import com.example.niftychart.data.local.NiftyDatabase
import com.example.niftychart.data.local.NiftyOHLCVDao

/**
 * Lightweight thread-safe DI module providing the Room OHLCV database and DAO.
 */
object DatabaseModule {

    fun provideNiftyDatabase(context: Context): NiftyDatabase {
        return NiftyDatabase.getInstance(context.applicationContext)
    }

    fun provideNiftyOhlcvDao(context: Context): NiftyOHLCVDao {
        return provideNiftyDatabase(context).niftyOhlcvDao()
    }
}
