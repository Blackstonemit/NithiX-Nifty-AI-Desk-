package com.example.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.chart.IndicatorCalculationEngine
import com.example.model.IndicatorCategory
import com.example.model.IndicatorConfig
import com.example.model.IndicatorPresetTemplate
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState
import java.util.Locale

@Composable
fun IndicatorsInspectorPanel(
    state: ChartWorkstationUiState,
    onToggleIndicator: (String) -> Unit,
    onUpdateParams: (String, Int, Int, Int, Double, Long) -> Unit,
    onApplyPreset: (IndicatorPresetTemplate) -> Unit
) {
    val palette = LocalChartPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<IndicatorCategory?>(null) }
    var editingConfig by remember { mutableStateOf<IndicatorConfig?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_indicators_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.inspector_quick_presets), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IndicatorCalculationEngine.builtInPresets.forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onApplyPreset(preset) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        Text(preset.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(preset.chartType.shortLabel, fontSize = 9.sp, color = palette.accentCyan)
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.inspector_search_indicators), fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryChip(stringResource(R.string.label_all), selectedCategory == null) { selectedCategory = null }
            IndicatorCategory.entries.forEach { cat ->
                CategoryChip(cat.label, selectedCategory == cat) { selectedCategory = cat }
            }
        }

        val filtered = state.indicatorConfigs.filter { cfg ->
            (selectedCategory == null || cfg.type.category == selectedCategory) &&
                (searchQuery.isBlank() ||
                    cfg.type.displayName.contains(searchQuery, ignoreCase = true) ||
                    cfg.type.shortName.contains(searchQuery, ignoreCase = true))
        }

        filtered.forEach { cfg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, if (cfg.enabled) Color(cfg.colorHex).copy(alpha = 0.6f) else palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(cfg.colorHex)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(cfg.type.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        val paneLabel = if (cfg.type.isOverlay) stringResource(R.string.inspector_price_overlay) else stringResource(R.string.inspector_lower_pane)
                        Text(
                            stringResource(R.string.inspector_tag_pane_format, cfg.displayTag(), paneLabel),
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            style = TabularNumericStyle
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { editingConfig = cfg },
                        modifier = Modifier.minimumInteractiveComponentSize().size(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_configure_indicator_format, cfg.type.shortName), tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Switch(
                        checked = cfg.enabled,
                        onCheckedChange = { onToggleIndicator(cfg.id) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = palette.accentCyan)
                    )
                }
            }
        }
    }

    editingConfig?.let { cfg ->
        var p1 by remember(cfg.id) { mutableStateOf(cfg.period.toString()) }
        var p2 by remember(cfg.id) { mutableStateOf(cfg.secondaryPeriod.toString()) }
        var p3 by remember(cfg.id) { mutableStateOf(cfg.tertiaryPeriod.toString()) }
        var mult by remember(cfg.id) { mutableStateOf(cfg.multiplier.toString()) }
        AlertDialog(
            onDismissRequest = { editingConfig = null },
            title = { Text(stringResource(R.string.inspector_tune_title_format, cfg.type.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text(stringResource(R.string.inspector_primary_period)) }, singleLine = true)
                    if (cfg.type.defaultSecondaryPeriod > 0) {
                        OutlinedTextField(value = p2, onValueChange = { p2 = it }, label = { Text(stringResource(R.string.inspector_secondary_period)) }, singleLine = true)
                    }
                    if (cfg.type.defaultTertiaryPeriod > 0) {
                        OutlinedTextField(value = p3, onValueChange = { p3 = it }, label = { Text(stringResource(R.string.inspector_signal_period)) }, singleLine = true)
                    }
                    OutlinedTextField(value = mult, onValueChange = { mult = it }, label = { Text(stringResource(R.string.inspector_multiplier_step)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateParams(
                        cfg.id,
                        p1.toIntOrNull() ?: cfg.period,
                        p2.toIntOrNull() ?: cfg.secondaryPeriod,
                        p3.toIntOrNull() ?: cfg.tertiaryPeriod,
                        mult.toDoubleOrNull() ?: cfg.multiplier,
                        cfg.colorHex
                    )
                    editingConfig = null
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = { TextButton(onClick = { editingConfig = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
fun StructureAndPatternsPanel(
    state: ChartWorkstationUiState,
    onToggleSmcOverlay: (Boolean) -> Unit,
    onTogglePatternBadges: (Boolean) -> Unit
) {
    val palette = LocalChartPalette.current
    val smc = state.marketStructure

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_structure_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.inspector_render_smc), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(stringResource(R.string.inspector_render_smc_sub), fontSize = 10.sp, color = palette.textSecondary)
            }
            Switch(checked = state.showSmcOverlays, onCheckedChange = onToggleSmcOverlay)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.inspector_pattern_markers), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(stringResource(R.string.inspector_patterns_count_format, state.detectedPatterns.size), fontSize = 10.sp, color = palette.textSecondary)
            }
            Switch(checked = state.showPatternBadges, onCheckedChange = onTogglePatternBadges)
        }

        // Dealing Range & ORB Summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.inspector_dealing_orb_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
            Text(
                stringResource(R.string.inspector_zone_eq_format, smc.currentZoneLabel, String.format(Locale.US, "%,.2f", smc.equilibriumPrice)),
                fontSize = 11.sp,
                color = palette.textPrimary,
                style = TabularNumericStyle
            )
            Text(
                stringResource(
                    R.string.inspector_ob_fvg_sweeps_format,
                    smc.orderBlocks.count { !it.isMitigated },
                    smc.fvgZones.count { !it.isMitigated },
                    smc.liquiditySweeps.size
                ),
                fontSize = 10.5.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
            smc.orb15m?.let {
                Text(
                    stringResource(
                        R.string.inspector_orb_15m_format,
                        String.format(Locale.US, "%.1f", it.low),
                        String.format(Locale.US, "%.1f", it.high),
                        it.status
                    ),
                    fontSize = 10.5.sp,
                    color = palette.accentCyan,
                    style = TabularNumericStyle
                )
            }
            smc.orb30m?.let {
                Text(
                    stringResource(
                        R.string.inspector_orb_30m_format,
                        String.format(Locale.US, "%.1f", it.low),
                        String.format(Locale.US, "%.1f", it.high),
                        it.status
                    ),
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    style = TabularNumericStyle
                )
            }
        }

        Text(stringResource(R.string.inspector_detected_patterns_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
        if (state.detectedPatterns.isEmpty()) {
            Text(stringResource(R.string.inspector_no_patterns), fontSize = 11.sp, color = palette.textMuted)
        } else {
            state.detectedPatterns.reversed().take(8).forEach { pat ->
                val accent = when (pat.sentiment) {
                    "BULLISH" -> palette.bullGreen
                    "BEARISH" -> palette.bearRed
                    else -> palette.goldAccent
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pat.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                        Text(stringResource(R.string.inspector_pattern_badge_format, pat.sentiment, pat.confidencePct), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, style = TabularNumericStyle)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(pat.explanation, fontSize = 10.5.sp, color = palette.textSecondary)
                }
            }
        }
    }
}

@Composable
fun SrAndVolumeProfilePanel(
    state: ChartWorkstationUiState,
    onToggleSrOverlay: (Boolean) -> Unit,
    onToggleVpvrOverlay: (Boolean) -> Unit
) {
    val palette = LocalChartPalette.current
    val vp = state.volumeProfile

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_sr_vpvr_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleCard(stringResource(R.string.inspector_show_sr), state.showSrLevels, onToggleSrOverlay, Modifier.weight(1f))
            ToggleCard(stringResource(R.string.inspector_show_vpvr), state.showVolumeProfile, onToggleVpvrOverlay, Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.goldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.inspector_vpvr_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
            Text(
                stringResource(
                    R.string.inspector_vpvr_levels_format,
                    String.format(Locale.US, "%,.2f", vp.pocPrice),
                    String.format(Locale.US, "%,.2f", vp.vahPrice),
                    String.format(Locale.US, "%,.2f", vp.valPrice)
                ),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                style = TabularNumericStyle
            )
            val volSourceLabel = if (vp.hasVolumeData) {
                stringResource(R.string.inspector_ohlc_vol_approx)
            } else {
                stringResource(R.string.inspector_index_pa_activity)
            }
            Text(
                stringResource(
                    R.string.inspector_vpvr_rvol_format,
                    String.format(Locale.US, "%.2f", vp.rvolRatio),
                    String.format(Locale.US, "%.0f", vp.upVolumePct),
                    volSourceLabel
                ),
                fontSize = 10.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
        }

        Text(stringResource(R.string.inspector_key_sr_header), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
        state.srLevels.forEach { lvl ->
            val col = if (lvl.isSupport) palette.bullGreen else palette.bearRed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.inspector_sr_label_format, lvl.label, lvl.source.label), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text(stringResource(R.string.inspector_sr_touches_format, lvl.touchCount, lvl.strengthScore), fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
                }
                Text(stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.2f", lvl.price)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col, style = TabularNumericStyle)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalChartPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accentCyan else palette.surfaceVariant)
            .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.Black else palette.textSecondary)
    }
}

@Composable
private fun ToggleCard(title: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalChartPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
