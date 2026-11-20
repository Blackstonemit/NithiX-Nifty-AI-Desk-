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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Text("QUICK STRATEGY PRESETS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
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
            label = { Text("Search 25+ indicators (EMA, RSI, Ichimoku...)", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryChip("All", selectedCategory == null) { selectedCategory = null }
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
                        Text(
                            "${cfg.displayTag()} · ${if (cfg.type.isOverlay) "Price Overlay" else "Lower Pane"}",
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
                        Icon(Icons.Default.Edit, contentDescription = "Configure ${cfg.type.shortName}", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
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
            title = { Text("Tune ${cfg.type.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text("Primary Period") }, singleLine = true)
                    if (cfg.type.defaultSecondaryPeriod > 0) {
                        OutlinedTextField(value = p2, onValueChange = { p2 = it }, label = { Text("Secondary Period") }, singleLine = true)
                    }
                    if (cfg.type.defaultTertiaryPeriod > 0) {
                        OutlinedTextField(value = p3, onValueChange = { p3 = it }, label = { Text("Signal / Cloud Period") }, singleLine = true)
                    }
                    OutlinedTextField(value = mult, onValueChange = { mult = it }, label = { Text("Multiplier / Step") }, singleLine = true)
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
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { editingConfig = null }) { Text("Cancel") } }
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
                Text("Render Smart Money Overlays", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("Order Blocks, FVG, BOS/CHoCH on chart", fontSize = 10.sp, color = palette.textSecondary)
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
                Text("Candlestick Pattern Markers", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("${state.detectedPatterns.size} patterns detected in window", fontSize = 10.sp, color = palette.textSecondary)
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
            Text("DEALING RANGE & ORB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
            Text("Zone: ${smc.currentZoneLabel} (Eq: ${String.format(Locale.US, "%,.2f", smc.equilibriumPrice)})", fontSize = 11.sp, color = palette.textPrimary, style = TabularNumericStyle)
            Text("Active OB: ${smc.orderBlocks.count { !it.isMitigated }} unmitigated | Open FVG: ${smc.fvgZones.count { !it.isMitigated }} | Sweeps: ${smc.liquiditySweeps.size}", fontSize = 10.5.sp, color = palette.textSecondary, style = TabularNumericStyle)
            smc.orb15m?.let {
                Text("15m ORB: ${String.format(Locale.US, "%.1f", it.low)} – ${String.format(Locale.US, "%.1f", it.high)} (${it.status})", fontSize = 10.5.sp, color = palette.accentCyan, style = TabularNumericStyle)
            }
            smc.orb30m?.let {
                Text("30m ORB: ${String.format(Locale.US, "%.1f", it.low)} – ${String.format(Locale.US, "%.1f", it.high)} (${it.status})", fontSize = 10.5.sp, color = palette.textSecondary, style = TabularNumericStyle)
            }
        }

        Text("DETECTED CANDLESTICK PATTERNS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
        if (state.detectedPatterns.isEmpty()) {
            Text("No high-confidence reversal or continuation patterns detected in the current bars.", fontSize = 11.sp, color = palette.textMuted)
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
                        Text("${pat.sentiment} · ${pat.confidencePct}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, style = TabularNumericStyle)
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
            ToggleCard("Show S/R Lines", state.showSrLevels, onToggleSrOverlay, Modifier.weight(1f))
            ToggleCard("Show Volume Profile", state.showVolumeProfile, onToggleVpvrOverlay, Modifier.weight(1f))
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
            Text("VOLUME PROFILE & LIQUIDITY (VPVR)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent, style = TabularNumericStyle)
            Text(
                "POC: ₹${String.format(Locale.US, "%,.2f", vp.pocPrice)} | VAH: ₹${String.format(Locale.US, "%,.2f", vp.vahPrice)} | VAL: ₹${String.format(Locale.US, "%,.2f", vp.valPrice)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                style = TabularNumericStyle
            )
            Text(
                "RVOL: ${String.format(Locale.US, "%.2f", vp.rvolRatio)}x | Est. Up-Vol Delta: ${String.format(Locale.US, "%.0f", vp.upVolumePct)}% (${if (vp.hasVolumeData) "OHLC Volume Approx" else "Index Price-Action Activity"})",
                fontSize = 10.sp,
                color = palette.textSecondary,
                style = TabularNumericStyle
            )
        }

        Text("KEY SUPPORT & RESISTANCE CLUSTERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, style = TabularNumericStyle)
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
                    Text("${lvl.label} (${lvl.source.label})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text("${lvl.touchCount} touches · Strength ${lvl.strengthScore}/100", fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
                }
                Text("₹${String.format(Locale.US, "%,.2f", lvl.price)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col, style = TabularNumericStyle)
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
