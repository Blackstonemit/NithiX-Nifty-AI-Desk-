package com.example.niftychart.presentation.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.niftychart.data.remote.SampleNiftyDataCatalog
import com.example.niftychart.domain.model.ChartInterval
import com.example.niftychart.domain.model.FeedConnectionStatus
import com.example.niftychart.domain.model.SmaPeriod
import com.example.niftychart.domain.usecase.CalculateRSIUseCase
import com.example.niftychart.domain.usecase.CalculateSMAUseCase
import com.example.niftychart.presentation.components.ChartTimeframeSelector
import com.example.niftychart.presentation.components.OHLCVInfoPanel
import com.example.niftychart.presentation.components.TechnicalIndicatorSelector
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TabularNumericStyle

/**
 * Reusable standalone financial trading screen backed by [ChartUiState].
 * Supports Dark & Light Material 3 themes, SMA 20/50/100/200 overlays, Wilder RSI panel,
 * timeframe selection, live tick streaming toggle, loading/empty/error states, and zoom/reset.
 */
@Composable
fun NiftyChartScreen(
    uiState: ChartUiState,
    onSelectInterval: (ChartInterval) -> Unit,
    onToggleSma: (SmaPeriod) -> Unit,
    onToggleRsi: () -> Unit,
    onSelectRsiPeriod: (Int) -> Unit,
    onToggleLiveStream: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
    onPanByBars: (Int) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetViewport: () -> Unit,
    onSelectCandle: (Int?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val palette = LocalChartPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("nifty_chart_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header & Controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = palette.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    onNavigateBack?.let { navigateBack ->
                        IconButton(
                            onClick = navigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("nifty_chart_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = palette.textPrimary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.CandlestickChart,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.nifty_terminal_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.nifty_terminal_subtitle),
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleLiveStream,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_live_ticks_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isStreamingActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (uiState.isStreamingActive) R.string.cd_pause_tick_stream else R.string.cd_start_tick_stream
                            ),
                            tint = if (uiState.isStreamingActive) palette.bullGreen else palette.accentCyan
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_candles_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_sync_historical_candles),
                            tint = palette.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_chart_theme_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = stringResource(R.string.cd_switch_chart_theme),
                            tint = palette.goldAccent
                        )
                    }
                }
            }
        }

        // OHLCV Readout Panel
        OHLCVInfoPanel(
            candle = uiState.inspectedOrLatestCandle,
            isInspected = uiState.selectedCandleIndex != null,
            connectionStatus = uiState.connectionStatus,
            syncLabel = uiState.syncStatusLabel
        )

        // Error Banner (non-fatal if cached data exists)
        if (uiState.errorMessage != null && uiState.candles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.bearRedDim)
                    .border(1.dp, palette.bearRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = palette.bearRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.errorMessage,
                    fontSize = 11.sp,
                    color = palette.bearRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Timeframe Selector
        ChartTimeframeSelector(
            selectedInterval = uiState.selectedInterval,
            onSelectInterval = onSelectInterval
        )

        // Technical Indicator Toggle Ribbon
        TechnicalIndicatorSelector(
            enabledSmas = uiState.enabledSmas,
            onToggleSma = onToggleSma,
            rsiEnabled = uiState.rsiEnabled,
            onToggleRsi = onToggleRsi,
            rsiPeriod = uiState.rsiPeriod,
            onSelectRsiPeriod = onSelectRsiPeriod,
            latestSmaValues = uiState.latestSmaValues,
            latestRsi = uiState.selectedCandleIndex?.let { uiState.rsiSeries.values.getOrNull(it) }
                ?: uiState.rsiSeries.latestValue
        )

        // Zoom & Inspection Controls Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (uiState.isCrosshairLocked) {
                        R.string.hint_crosshair_locked
                    } else {
                        R.string.hint_chart_gestures
                    }
                ),
                fontSize = 10.sp,
                color = palette.textMuted,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactChartToolButton(
                    icon = Icons.Default.ZoomIn,
                    contentDescription = stringResource(R.string.cd_zoom_in),
                    onClick = onZoomIn,
                    tag = "chart_zoom_in"
                )
                CompactChartToolButton(
                    icon = Icons.Default.ZoomOut,
                    contentDescription = stringResource(R.string.cd_zoom_out),
                    onClick = onZoomOut,
                    tag = "chart_zoom_out"
                )
                CompactChartToolButton(
                    icon = Icons.Default.FitScreen,
                    contentDescription = stringResource(R.string.cd_reset_viewport),
                    onClick = onResetViewport,
                    tag = "chart_reset_zoom"
                )
            }
        }

        // Main Chart Area (Loading / Empty / Error / Live Candles)
        when {
            uiState.isLoading && uiState.candles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .testTag("chart_loading_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.accentCyan)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.loading_room_candles_format,
                                uiState.selectedInterval.code
                            ),
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            uiState.isEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(20.dp)
                        .testTag("chart_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CandlestickChart,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: stringResource(
                                R.string.empty_room_candles_format,
                                uiState.selectedInterval.displayLabel
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRefresh) {
                            Text(
                                text = stringResource(R.string.action_sync_nifty_data),
                                style = TabularNumericStyle
                            )
                        }
                    }
                }
            }

            else -> {
                NiftyCandlestickChart(
                    candles = uiState.candles,
                    visibleCandleCount = uiState.visibleCandleCount,
                    scrollRightOffset = uiState.scrollRightOffset,
                    selectedGlobalIndex = uiState.selectedCandleIndex,
                    isCrosshairLocked = uiState.isCrosshairLocked,
                    enabledSmas = uiState.enabledSmas,
                    smaLines = uiState.smaLines,
                    onPanByBars = onPanByBars,
                    onPinchZoom = onPinchZoom,
                    onSelectCandle = onSelectCandle,
                    onResetViewport = onResetViewport,
                    chartHeight = 290.dp
                )

                if (uiState.rsiEnabled) {
                    RSIIndicatorChart(
                        rsiSeries = uiState.rsiSeries,
                        viewport = uiState.viewport,
                        selectedGlobalIndex = uiState.selectedCandleIndex,
                        panelHeight = 110.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactChartToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String
) {
    val palette = LocalChartPalette.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceHighlight)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(8.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun previewUiState(isDark: Boolean): ChartUiState {
    val sampleCandles = SampleNiftyDataCatalog.generateSampleCandles(count = 160)
    val smaUseCase = CalculateSMAUseCase()
    val rsiUseCase = CalculateRSIUseCase()
    val closes = sampleCandles.map { it.close }
    val smas = setOf(SmaPeriod.SMA_20, SmaPeriod.SMA_50)
    val smaLines = smas.associateWith { smaUseCase.calculateFromCloses(closes, it.period) }
    val rsi = rsiUseCase.calculateFromCloses(closes, 14)

    return ChartUiState(
        candles = sampleCandles,
        visibleCandleCount = 55,
        enabledSmas = smas,
        smaLines = smaLines,
        rsiEnabled = true,
        rsiPeriod = 14,
        rsiSeries = rsi,
        isLoading = false,
        connectionStatus = FeedConnectionStatus.SIMULATED,
        syncStatusLabel = "Sample NIFTY 50 Dataset",
        isDarkTheme = isDark
    )
}

@Preview(showBackground = true, name = "NiftyChartScreen - Financial Dark")
@Composable
private fun NiftyChartScreenDarkPreview() {
    MyApplicationTheme(chartThemeMode = ChartThemeMode.FINANCIAL_DARK) {
        NiftyChartScreen(
            uiState = previewUiState(isDark = true),
            onSelectInterval = {},
            onToggleSma = {},
            onToggleRsi = {},
            onSelectRsiPeriod = {},
            onToggleLiveStream = {},
            onRefresh = {},
            onToggleTheme = {},
            onPanByBars = {},
            onPinchZoom = {},
            onZoomIn = {},
            onZoomOut = {},
            onResetViewport = {},
            onSelectCandle = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "NiftyChartScreen - Institutional Light")
@Composable
private fun NiftyChartScreenLightPreview() {
    MyApplicationTheme(chartThemeMode = ChartThemeMode.INSTITUTIONAL_LIGHT) {
        NiftyChartScreen(
            uiState = previewUiState(isDark = false),
            onSelectInterval = {},
            onToggleSma = {},
            onToggleRsi = {},
            onSelectRsiPeriod = {},
            onToggleLiveStream = {},
            onRefresh = {},
            onToggleTheme = {},
            onPanByBars = {},
            onPinchZoom = {},
            onZoomIn = {},
            onZoomOut = {},
            onResetViewport = {},
            onSelectCandle = { _, _ -> }
        )
    }
}
