package com.example.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.local.SavedChartLayout
import com.example.model.MarketSymbol
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle

@Composable
fun WatchlistPickerDialog(
    watchlist: List<MarketSymbol>,
    activeSymbol: MarketSymbol,
    onSelectSymbol: (MarketSymbol) -> Unit,
    onAddCustomSymbol: (String) -> Unit,
    onRemoveSymbol: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalChartPalette.current
    var customInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watchlist_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text(stringResource(R.string.watchlist_symbol_hint), fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("watchlist_custom_symbol_input")
                    )
                    Button(
                        onClick = {
                            if (customInput.isNotBlank()) {
                                onAddCustomSymbol(customInput)
                                customInput = ""
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_nse_symbol), modifier = Modifier.size(16.dp))
                    }
                }

                watchlist.forEach { sym ->
                    val selected = sym.symbol == activeSymbol.symbol
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyanDim else palette.surfaceVariant)
                            .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectSymbol(sym)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("watchlist_item_${sym.symbol}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sym.symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) palette.accentCyan else palette.textPrimary, style = TabularNumericStyle)
                            Text(stringResource(R.string.watchlist_item_sub_format, sym.displayName, sym.exchange), fontSize = 10.sp, color = palette.textSecondary)
                        }
                        if (sym.symbol != "NIFTY 50") {
                            IconButton(
                                onClick = { onRemoveSymbol(sym.symbol) },
                                modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_remove_symbol_format, sym.symbol), tint = palette.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
fun SavedLayoutsDialog(
    layouts: List<SavedChartLayout>,
    onSaveLayout: (String) -> Unit,
    onApplyLayout: (SavedChartLayout) -> Unit,
    onDeleteLayout: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalChartPalette.current
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layouts_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.layouts_new_name_label), fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onSaveLayout(newName)
                                newName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save_layout), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(2.dp))
                layouts.forEach { layout ->
                    val enabledCount = layout.indicators.count { it.enabled }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceVariant)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable {
                                onApplyLayout(layout)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(layout.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text(
                                stringResource(R.string.layouts_item_meta_format, layout.chartType.shortLabel, layout.themeMode.label, enabledCount),
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                style = TabularNumericStyle
                            )
                        }
                        if (!layout.isDefault) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDeleteLayout(layout.id) },
                                modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_layout), tint = palette.bearRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}
