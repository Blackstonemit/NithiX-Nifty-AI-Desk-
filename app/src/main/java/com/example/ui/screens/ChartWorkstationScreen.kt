package com.example.ui.screens

import android.graphics.Picture
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.chart.TimeframeAggregationEngine
import com.example.model.ChartType
import com.example.model.DateRangePreset
import com.example.model.PriceScaleMode
import com.example.model.Timeframe
import com.example.ui.components.chart.AiChartAnalystSheet
import com.example.ui.components.chart.BacktestLabPanel
import com.example.ui.components.chart.ChartCanvasViewport
import com.example.ui.components.chart.ChartSnapshotHelper
import com.example.ui.components.chart.DrawingToolbar
import com.example.ui.components.chart.IndicatorsInspectorPanel
import com.example.ui.components.chart.MtfAndSignalPanel
import com.example.ui.components.chart.OscillatorSubCharts
import com.example.ui.components.chart.RiskCalculatorPanel
import com.example.ui.components.chart.SavedLayoutsDialog
import com.example.ui.components.chart.SrAndVolumeProfilePanel
import com.example.ui.components.chart.StructureAndPatternsPanel
import com.example.ui.components.chart.WatchlistPickerDialog
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationCoordinator
import com.example.ui.viewmodel.ChartWorkstationUiState
import com.example.ui.viewmodel.WorkstationInspectorTab
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-Screen Institutional Chart Analysis Workstation combining multi-symbol NSE watchlist,
 * 7 chart styles, 13 timeframes + IST countdown, pinch/zoom/pan, 12 vector drawing tools,
 * synchronized oscillator panes, SMC + S&R + VPVR, Risk sizing, Backtesting, and Multi-LLM AI Analyst.
 */
@Composable
fun ChartWorkstationScreen(
    state: ChartWorkstationUiState,
    coordinator: ChartWorkstationCoordinator,
    onClose: () -> Unit,
    onOpenLlmSettings: () -> Unit
) {
    BackHandler { onClose() }

    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val context = LocalContext.current
    val snapshotPicture = remember { Picture() }

    var showWatchlistDialog by remember { mutableStateOf(false) }
    var showLayoutsDialog by remember { mutableStateOf(false) }
    var showDrawingToolbar by remember { mutableStateOf(false) }
    var selectedVisibleIdx by remember(state.activeSymbol.symbol, state.timeframe, state.displayCandles.size) {
        mutableStateOf<Int?>(null)
    }
    var snapshotFeedback by remember { mutableStateOf<String?>(null) }

    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.showCountdown, state.timeframe) {
        while (state.showCountdown) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val countdownText = remember(nowEpochMs, state.timeframe) {
        TimeframeAggregationEngine.formatCandleCountdown(state.timeframe, nowEpochMs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("chart_workstation_screen")
    ) {
        // 1. Top Workstation Bar: Back, Symbol Pill, Freshness, Snapshot, Saved Layouts, Theme Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .border(1.dp, palette.border)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("workstation_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_to_dashboard),
                        tint = palette.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("workstation_symbol_pill")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentCyanDim)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { showWatchlistDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val lastClose = state.candles.lastOrNull()?.close ?: 0.0
                    Column {
                        Text(
                            text = "${state.activeSymbol.symbol} ▾",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.accentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TabularNumericStyle
                        )
                        Text(
                            text = if (lastClose > 0) stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.2f", lastClose)) else state.activeSymbol.displayName,
                            fontSize = 10.sp,
                            color = palette.textPrimary,
                            style = TabularNumericStyle
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(if (state.dataFreshness.isRealTime) R.string.badge_live else R.string.workstation_badge_ref_del),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.dataFreshness.isRealTime) palette.bullGreen else palette.goldAccent,
                            style = TabularNumericStyle
                        )
                        if (state.showCountdown) {
                            Text(
                                text = stringResource(R.string.workstation_countdown_format, countdownText),
                                fontSize = 9.sp,
                                color = palette.textSecondary,
                                style = TabularNumericStyle
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.workstation_source_adj_format,
                            state.dataFreshness.sourceLabel,
                            stringResource(if (state.isAdjustedPrices) R.string.workstation_adj else R.string.workstation_unadj)
                        ),
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isLoadingCandles) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                        color = palette.accentCyan
                    )
                }
                IconButton(
                    onClick = { coordinator.fetchActiveSymbolCandles() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh_ohlcv), tint = palette.accentCyan, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { showLayoutsDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("workstation_layouts_button")
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.cd_saved_layouts), tint = palette.goldAccent, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val res = ChartSnapshotHelper.shareChartPicture(
                            context = context,
                            picture = snapshotPicture,
                            symbol = state.activeSymbol.symbol,
                            timeframeLabel = state.timeframe.label
                        )
                        snapshotFeedback = res.getOrElse { it.message ?: context.getString(R.string.workstation_snapshot_exported) }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("workstation_snapshot_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_export_snapshot_png), tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { coordinator.toggleThemeMode() },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("workstation_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (state.themeMode == ChartThemeMode.FINANCIAL_DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = stringResource(R.string.cd_toggle_chart_theme),
                        tint = palette.goldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Control Ribbon: 7 Chart Styles + Scale + Draw Toggle + Zoom/Fit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceVariant)
                .border(1.dp, palette.border.copy(alpha = 0.6f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartType.entries.forEach { ct ->
                val sel = ct == state.chartType
                Box(
                    modifier = Modifier
                        .testTag("workstation_chart_type_${ct.name.lowercase()}")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable { coordinator.setChartType(ct) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) palette.accentCyan else palette.surface)
                            .border(1.dp, if (sel) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            ct.shortLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) onAccent else palette.textPrimary,
                            style = TabularNumericStyle
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            val nextScale = when (state.scaleMode) {
                PriceScaleMode.LINEAR -> PriceScaleMode.LOGARITHMIC
                PriceScaleMode.LOGARITHMIC -> PriceScaleMode.PERCENTAGE
                PriceScaleMode.PERCENTAGE -> PriceScaleMode.LINEAR
            }
            Box(
                modifier = Modifier
                    .testTag("workstation_scale_toggle")
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { coordinator.setScaleMode(nextScale) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.goldAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(state.scaleMode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
                }
            }

            Box(
                modifier = Modifier
                    .testTag("workstation_draw_toggle")
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { showDrawingToolbar = !showDrawingToolbar },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showDrawingToolbar) palette.accentCyan else palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = if (showDrawingToolbar) onAccent else palette.textPrimary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(stringResource(R.string.workstation_draw), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (showDrawingToolbar) onAccent else palette.textPrimary)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { coordinator.updateViewport(visibleCountDelta = -10) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.cd_zoom_in), tint = palette.textPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { coordinator.updateViewport(visibleCountDelta = 10) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.cd_zoom_out), tint = palette.textPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { coordinator.updateViewport(reset = true) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.workstation_fit_all), tint = palette.accentCyan, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 3. Timeframes (13) + Date Ranges + Signal Score Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Timeframe.entries.forEach { tf ->
                val sel = tf == state.timeframe
                Box(
                    modifier = Modifier
                        .testTag("workstation_tf_${tf.label}")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable { coordinator.setTimeframe(tf) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (sel) palette.accentCyan else palette.surfaceVariant)
                            .border(1.dp, if (sel) palette.accentCyan else palette.border, RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(tf.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (sel) onAccent else palette.textSecondary, style = TabularNumericStyle)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            DateRangePreset.entries.forEach { dr ->
                val sel = dr == state.dateRange
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable { coordinator.setDateRange(dr) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (sel) palette.goldAccentDim else palette.surfaceVariant)
                            .border(1.dp, if (sel) palette.goldAccent else palette.border, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(dr.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (sel) palette.goldAccent else palette.textMuted, style = TabularNumericStyle)
                    }
                }
            }
        }

        if (showDrawingToolbar) {
            DrawingToolbar(
                activeTool = state.activeDrawingTool,
                hasPendingAnchor = state.pendingAnchor != null,
                magnetEnabled = state.magnetEnabled,
                hideAllDrawings = state.hideAllDrawings,
                activeColorHex = state.activeDrawingColor,
                drawingsCount = state.drawings.size,
                onSelectTool = { coordinator.selectDrawingTool(it) },
                onSelectColor = { coordinator.setDrawingColor(it) },
                onToggleMagnet = { coordinator.toggleDisplayOption(magnet = !state.magnetEnabled) },
                onToggleHideAll = { coordinator.toggleDisplayOption(hideDrawings = !state.hideAllDrawings) },
                onUndo = { coordinator.undoLastDrawing() },
                onRedo = { coordinator.redoDrawing() },
                onClearAll = { coordinator.clearAllDrawings() },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        (snapshotFeedback ?: state.statusBanner)?.let { msg ->
            Text(
                text = msg,
                fontSize = 10.sp,
                color = palette.goldAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.goldAccentDim)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // 4. Adaptive Workspace Body (Split Pane >= 840dp vs Stacked Mobile)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isSplitPane = maxWidth >= 840.dp
            if (isSplitPane) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.45f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChartCanvasViewport(
                            state = state,
                            canvasHeight = 320.dp,
                            selectedVisibleIndex = selectedVisibleIdx,
                            onSelectVisibleIndex = { selectedVisibleIdx = it },
                            onPanBars = { bars -> coordinator.updateViewport(panBarsDelta = bars) },
                            onZoomBars = { delta -> coordinator.updateViewport(visibleCountDelta = delta) },
                            onDoubleTapReset = { coordinator.updateViewport(reset = true) },
                            onDrawingTap = { raw, snapped -> coordinator.handleChartCanvasTapForDrawing(raw, snapped) },
                            snapshotPicture = snapshotPicture
                        )
                        OscillatorSubCharts(state = state, selectedVisibleIndex = selectedVisibleIdx)
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val activeTab = state.activeInspectorTab ?: WorkstationInspectorTab.INDICATORS
                        InspectorContentRouter(
                            tab = activeTab,
                            state = state,
                            coordinator = coordinator,
                            onOpenLlmSettings = onOpenLlmSettings
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChartCanvasViewport(
                        state = state,
                        canvasHeight = if (state.activeInspectorTab == null) 310.dp else 230.dp,
                        selectedVisibleIndex = selectedVisibleIdx,
                        onSelectVisibleIndex = { selectedVisibleIdx = it },
                        onPanBars = { bars -> coordinator.updateViewport(panBarsDelta = bars) },
                        onZoomBars = { delta -> coordinator.updateViewport(visibleCountDelta = delta) },
                        onDoubleTapReset = { coordinator.updateViewport(reset = true) },
                        onDrawingTap = { raw, snapped -> coordinator.handleChartCanvasTapForDrawing(raw, snapped) },
                        snapshotPicture = snapshotPicture
                    )

                    OscillatorSubCharts(state = state, selectedVisibleIndex = selectedVisibleIdx)

                    state.activeInspectorTab?.let { tab ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            InspectorContentRouter(
                                tab = tab,
                                state = state,
                                coordinator = coordinator,
                                onOpenLlmSettings = onOpenLlmSettings
                            )
                        }
                    }
                }
            }
        }

        // 5. Bottom Inspector Dock Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .border(1.dp, palette.border)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WorkstationInspectorTab.entries.forEach { tab ->
                val selected = state.activeInspectorTab == tab
                Box(
                    modifier = Modifier
                        .testTag("workstation_tab_${tab.name.lowercase()}")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable { coordinator.selectInspectorTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyan else palette.surfaceVariant)
                            .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            tab.label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) onAccent else palette.textPrimary
                        )
                    }
                }
            }
        }
    }

    if (showWatchlistDialog) {
        WatchlistPickerDialog(
            watchlist = state.watchlist,
            activeSymbol = state.activeSymbol,
            onSelectSymbol = { coordinator.selectSymbol(it) },
            onAddCustomSymbol = { coordinator.addCustomSymbol(it) },
            onRemoveSymbol = { coordinator.removeWatchlistSymbol(it) },
            onDismiss = { showWatchlistDialog = false }
        )
    }

    if (showLayoutsDialog) {
        SavedLayoutsDialog(
            layouts = state.savedLayouts,
            onSaveLayout = { coordinator.saveCurrentLayout(it) },
            onApplyLayout = { coordinator.applySavedLayout(it) },
            onDeleteLayout = { coordinator.deleteSavedLayout(it) },
            onDismiss = { showLayoutsDialog = false }
        )
    }
}

@Composable
private fun InspectorContentRouter(
    tab: WorkstationInspectorTab,
    state: ChartWorkstationUiState,
    coordinator: ChartWorkstationCoordinator,
    onOpenLlmSettings: () -> Unit
) {
    when (tab) {
        WorkstationInspectorTab.INDICATORS -> IndicatorsInspectorPanel(
            state = state,
            onToggleIndicator = { coordinator.toggleIndicator(it) },
            onUpdateParams = { id, p1, p2, p3, mult, col -> coordinator.updateIndicatorParams(id, p1, p2, p3, mult, col) },
            onApplyPreset = { coordinator.applyPresetTemplate(it) }
        )
        WorkstationInspectorTab.STRUCTURE -> StructureAndPatternsPanel(
            state = state,
            onToggleSmcOverlay = { coordinator.toggleDisplayOption(smc = it) },
            onTogglePatternBadges = { coordinator.toggleDisplayOption(patterns = it) }
        )
        WorkstationInspectorTab.SR_VOLUME -> SrAndVolumeProfilePanel(
            state = state,
            onToggleSrOverlay = { coordinator.toggleDisplayOption(sr = it) },
            onToggleVpvrOverlay = { coordinator.toggleDisplayOption(vpvr = it) }
        )
        WorkstationInspectorTab.MTF_SIGNAL -> MtfAndSignalPanel(
            state = state,
            onRefreshMtf = { coordinator.refreshMtfConfluence() }
        )
        WorkstationInspectorTab.RISK -> RiskCalculatorPanel(
            state = state,
            onUpdateRisk = { cap, pct, rr, sl, clr, show, lot -> coordinator.updateRiskInputs(cap, pct, rr, sl, clr, show, lot) }
        )
        WorkstationInspectorTab.BACKTEST -> BacktestLabPanel(
            state = state,
            onRunBacktest = { coordinator.runBacktest(it) }
        )
        WorkstationInspectorTab.AI_ANALYST -> AiChartAnalystSheet(
            state = state,
            onSelectProvider = { coordinator.selectLlmProvider(it) },
            onRunAnalysis = { coordinator.runAiChartAnalysis(it) },
            onOpenLlmSettings = onOpenLlmSettings
        )
    }
}
