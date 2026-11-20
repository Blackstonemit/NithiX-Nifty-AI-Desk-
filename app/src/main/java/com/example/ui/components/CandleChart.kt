package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.Candle
import com.example.model.Timeframe

/**
 * Legacy alias forwarding to the lightweight native Compose [CandlestickChart] component.
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    vwapValue: Double,
    emaValue: Double,
    selectedTimeframe: Timeframe = Timeframe.M15,
    onSelectTimeframe: (Timeframe) -> Unit = {},
    isStreaming: Boolean = false,
    isMarketOpen: Boolean = false
) {
    CandlestickChart(
        candles = candles,
        modifier = modifier,
        vwapValue = vwapValue,
        emaValue = emaValue,
        selectedTimeframe = selectedTimeframe,
        onSelectTimeframe = onSelectTimeframe,
        isStreaming = isStreaming,
        isMarketOpen = isMarketOpen
    )
}
