package com.example.niftychart.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.MarketChartDataRepository
import com.example.niftychart.data.remote.DemoTickMarketDataProvider
import com.example.niftychart.data.remote.HybridNiftyMarketDataSource
import com.example.niftychart.data.remote.MarketDataProvider
import com.example.niftychart.data.repository.NiftyMarketRepositoryImpl
import com.example.niftychart.domain.repository.NiftyMarketRepository
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import com.example.niftychart.domain.usecase.GetHistoricalCandlesUseCase
import com.example.niftychart.presentation.chart.ChartViewModel

/**
 * Lightweight manual DI container wiring remote providers, Room repository, domain use cases,
 * and ChartViewModel factory without requiring external DI code-generation plugins.
 */
object RepositoryModule {

    @Volatile
    private var sharedRepository: NiftyMarketRepository? = null

    fun provideMarketDataProvider(
        indstocksTokenProvider: () -> String = { "" },
        offlineOnly: Boolean = false
    ): MarketDataProvider {
        return if (offlineOnly) {
            DemoTickMarketDataProvider()
        } else {
            HybridNiftyMarketDataSource(
                chartDataRepo = MarketChartDataRepository(),
                fallbackProvider = DemoTickMarketDataProvider(),
                indstocksTokenProvider = indstocksTokenProvider
            )
        }
    }

    fun provideNiftyMarketRepository(
        context: Context,
        indstocksTokenProvider: () -> String = { "" }
    ): NiftyMarketRepository {
        return sharedRepository ?: synchronized(this) {
            sharedRepository ?: NiftyMarketRepositoryImpl(
                dao = DatabaseModule.provideNiftyOhlcvDao(context),
                remoteProvider = provideMarketDataProvider(indstocksTokenProvider = indstocksTokenProvider),
                demoProvider = DemoTickMarketDataProvider()
            ).also { sharedRepository = it }
        }
    }

    fun provideCalculateSmaUseCase(): CalculateSMAUseCase = CalculateSMAUseCase()

    fun provideCalculateRsiUseCase(): CalculateRSIUseCase = CalculateRSIUseCase()

    fun provideGetHistoricalCandlesUseCase(repository: NiftyMarketRepository): GetHistoricalCandlesUseCase =
        GetHistoricalCandlesUseCase(repository)

    fun provideChartViewModelFactory(
        context: Context,
        indstocksTokenProvider: () -> String = { "" }
    ): ViewModelProvider.Factory {
        val repo = provideNiftyMarketRepository(context, indstocksTokenProvider)
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChartViewModel(
                    repository = repo,
                    getHistoricalCandlesUseCase = provideGetHistoricalCandlesUseCase(repo),
                    calculateSMAUseCase = provideCalculateSmaUseCase(),
                    calculateRSIUseCase = provideCalculateRsiUseCase()
                ) as T
            }
        }
    }
}
