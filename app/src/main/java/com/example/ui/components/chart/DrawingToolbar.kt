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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DrawingToolType
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle

/**
 * Interactive 12-tool drawing bar with Magnet OHLC snap, Undo/Redo, Hide-All, Color Swatches, and Clear.
 */
@Composable
fun DrawingToolbar(
    activeTool: DrawingToolType,
    hasPendingAnchor: Boolean,
    magnetEnabled: Boolean,
    hideAllDrawings: Boolean,
    activeColorHex: Long,
    drawingsCount: Int,
    onSelectTool: (DrawingToolType) -> Unit,
    onSelectColor: (Long) -> Unit,
    onToggleMagnet: () -> Unit,
    onToggleHideAll: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalChartPalette.current
    val swatches = listOf(0xFF00E5FFL, 0xFFFFB300L, 0xFF00E676L, 0xFFFF334BL, 0xFFA855F7L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("drawing_toolbar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawingToolType.entries.forEach { tool ->
                val selected = tool == activeTool
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) palette.accentCyan else palette.surface)
                        .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                        .clickable { onSelectTool(tool) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("draw_tool_${tool.name.lowercase()}")
                ) {
                    Text(
                        text = tool.shortLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.Black else palette.textPrimary,
                        style = TabularNumericStyle
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                swatches.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (hex == activeColorHex) 2.dp else 1.dp,
                                color = if (hex == activeColorHex) palette.textPrimary else palette.border,
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(hex) }
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when {
                        activeTool == DrawingToolType.NONE -> stringResource(R.string.draw_saved_count_format, drawingsCount)
                        hasPendingAnchor -> stringResource(R.string.draw_tap_second_anchor)
                        else -> stringResource(R.string.draw_tap_chart_format, activeTool.requiredAnchors)
                    },
                    fontSize = 10.sp,
                    color = if (activeTool != DrawingToolType.NONE) palette.goldAccent else palette.textSecondary,
                    style = TabularNumericStyle
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleMagnet,
                    modifier = Modifier.minimumInteractiveComponentSize().size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.cd_toggle_magnet),
                        tint = if (magnetEnabled) palette.accentCyan else palette.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onUndo,
                    modifier = Modifier.minimumInteractiveComponentSize().size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.cd_undo_drawing), tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onRedo,
                    modifier = Modifier.minimumInteractiveComponentSize().size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.cd_redo_drawing), tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onToggleHideAll,
                    modifier = Modifier.minimumInteractiveComponentSize().size(36.dp)
                ) {
                    Icon(
                        if (hideAllDrawings) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_hide_drawings),
                        tint = if (hideAllDrawings) palette.bearRed else palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier.minimumInteractiveComponentSize().size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.cd_clear_all_drawings), tint = palette.bearRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
