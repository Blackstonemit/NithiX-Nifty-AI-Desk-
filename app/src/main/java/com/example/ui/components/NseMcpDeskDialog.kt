package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.NseMcpState
import com.example.ui.theme.LocalChartPalette
import java.util.Locale

@Composable
fun NseMcpDeskDialog(
    mcpState: NseMcpState,
    onSyncNow: () -> Unit,
    onOpenConnectorsSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview, 1: Tools Catalog, 2: JSON-RPC Console, 3: Config

    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(1.5.dp, palette.accentCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("nse_mcp_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.accentCyanDim)
                                .border(1.dp, palette.accentCyan, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = palette.accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NSE INDIA MODEL CONTEXT PROTOCOL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = palette.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "nseindia.com/nse-mcp",
                                    fontSize = 9.5.sp,
                                    color = palette.accentCyan,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (mcpState.isConnected) palette.bullGreenDim else palette.goldAccentDim)
                                        .border(
                                            1.dp,
                                            if (mcpState.isConnected) palette.bullGreen.copy(alpha = 0.5f) else palette.goldAccent.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (mcpState.isConnected) "LIVE MCP" else "CACHED / OFFLINE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mcpState.isConnected) palette.bullGreen else palette.goldAccent,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_mcp_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                val tabTitles = listOf("Bhavcopy Data", "Index Tracker", "MCP Tools (5)", "RPC Logs", "Client Config")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) palette.accentCyan else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) onAccent else palette.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    when (selectedTab) {
                        0 -> BhavcopyOverviewSection(mcpState = mcpState, onSyncNow = onSyncNow)
                        1 -> IndexTrackerSection(indexTracker = mcpState.indexTracker)
                        2 -> McpToolsSection(mcpState = mcpState)
                        3 -> McpRpcLogsSection(mcpState = mcpState)
                        4 -> McpConfigSection(
                            mcpState = mcpState,
                            onOpenConnectorsSettings = onOpenConnectorsSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BhavcopyOverviewSection(
    mcpState: NseMcpState,
    onSyncNow: () -> Unit
) {
    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary

    // Endpoints Status Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "REGISTERED REMOTE MCP ENDPOINTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Endpoint 1: Bhavcopy
            EndpointRow(
                name = "nse-bhavcopy",
                url = mcpState.endpointUrl,
                status = if (mcpState.isConnected) "LIVE (${mcpState.latencyMs}ms)" else "OFFLINE / CACHED",
                isGreen = mcpState.isConnected
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Endpoint 2: CM Market
            EndpointRow(
                name = "cm-market",
                url = mcpState.secondaryEndpointUrl,
                status = if (mcpState.isConnected) "CONNECTED" else "STANDBY",
                isGreen = mcpState.isConnected
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mcpState.syncStatusMessage,
                fontSize = 9.sp,
                color = if (mcpState.isConnected) palette.bullGreen else palette.goldAccent,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Official Bhavcopy Summary Card
    val record = mcpState.latestBhavcopy
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "OFFICIAL NSE BHAVCOPY SETTLEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.goldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Source: ${mcpState.endpointUrl}",
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onSyncNow,
                    enabled = !mcpState.isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentCyan,
                        contentColor = onAccent
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sync_bhavcopy_button")
                ) {
                    if (mcpState.isSyncing) {
                        CircularProgressIndicator(
                            color = onAccent,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (mcpState.isSyncing) "Syncing..." else "Sync Bhavcopy",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (record != null) {
                val delta = record.close - record.prevClose
                val deltaPct = if (record.prevClose > 0.0) (delta / record.prevClose) * 100.0 else 0.0
                val deltaColor = if (delta >= 0) palette.bullGreen else palette.bearRed
                val deltaSign = if (delta >= 0) "+" else ""
                val tradedCr = record.totalTradedVal / 10_000_000.0

                // Price Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "CLOSE (LTP)",
                        value = "₹${String.format(Locale.US, "%,.2f", record.close)}",
                        sub = "$deltaSign${String.format(Locale.US, "%.2f", delta)} ($deltaSign${String.format(Locale.US, "%.2f", deltaPct)}%)",
                        valueColor = deltaColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "PREV CLOSE",
                        value = "₹${String.format(Locale.US, "%,.2f", record.prevClose)}",
                        sub = "Previous Session",
                        valueColor = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "DAY HIGH / LOW",
                        value = "${record.high.toInt()} / ${record.low.toInt()}",
                        sub = "Range: ${(record.high - record.low).toInt()} pts",
                        valueColor = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "DELIVERY %",
                        value = "${String.format(Locale.US, "%.2f", record.deliveryPct)}%",
                        sub = "${record.deliveryQty / 1000000}M Shares",
                        valueColor = palette.bullGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Traded Value row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceHighlight)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Date: ${record.tradeDate}",
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Total Value: ₹${String.format(Locale.US, "%,.0f", tradedCr)} Cr",
                            fontSize = 10.sp,
                            color = palette.goldAccent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Protocol & Compliance Notice
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.goldAccentDim)
            .border(1.dp, palette.goldAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = "NSE INDIA MODEL CONTEXT PROTOCOL (MCP) COMPLIANCE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.goldAccent,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Official endpoint provided by NSE India under standard terms for informational & research purposes. Integrates with AI assistants, multi-agent research desks, and autonomous trading decision engines.",
                fontSize = 9.sp,
                color = palette.textSecondary,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun IndexTrackerSection(indexTracker: com.example.model.NseIndexTrackerSnapshot) {
    val palette = LocalChartPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NSE INDEX TRACKER: ${indexTracker.indexName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = indexTracker.officialTrackerUrl,
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val idxDeltaColor = if (indexTracker.change >= 0) palette.bullGreen else palette.bearRed
                val idxSign = if (indexTracker.change >= 0) "+" else ""
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (indexTracker.change >= 0) palette.bullGreenDim else palette.bearRedDim)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$idxSign${String.format(Locale.US, "%.2f", indexTracker.change)} ($idxSign${String.format(Locale.US, "%.2f", indexTracker.changePct)}%)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = idxDeltaColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advances vs Declines Market Breadth
            Text(
                text = "MARKET BREADTH (NIFTY 50 CONSTITUENTS)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${indexTracker.advances} Advances",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.bullGreen,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${indexTracker.declines} Declines",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.bearRed,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Breadth Progress Bar
            val advanceRatio = (indexTracker.advances.toFloat() / (indexTracker.advances + indexTracker.declines).toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(advanceRatio)
                        .fillMaxHeight()
                        .background(palette.bullGreen)
                )
                Box(
                    modifier = Modifier
                        .weight(1f - advanceRatio)
                        .fillMaxHeight()
                        .background(palette.bearRed)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Valuation Metrics: P/E, P/B, Div Yield
            Text(
                text = "INDEX VALUATION RATIOS (OFFICIAL NSE)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    title = "P/E RATIO",
                    value = "${indexTracker.peRatio}",
                    sub = "Fair: 20-22",
                    valueColor = palette.goldAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "P/B RATIO",
                    value = "${indexTracker.pbRatio}",
                    sub = "Book Mult",
                    valueColor = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "DIV YIELD",
                    value = "${indexTracker.divYield}%",
                    sub = "Annual Yield",
                    valueColor = palette.bullGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 52-Week Range Bar
            Text(
                text = "52-WEEK RANGE (21,281 - 26,277)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Low: ${indexTracker.yearLow.toInt()}", fontSize = 9.sp, color = palette.textMuted, fontFamily = FontFamily.Monospace)
                Text(text = "LTP: ${indexTracker.ltp.toInt()}", fontSize = 9.sp, color = palette.accentCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(text = "High: ${indexTracker.yearHigh.toInt()}", fontSize = 9.sp, color = palette.textMuted, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Top Weightage Point Contributors
            Text(
                text = "TOP INDEX POINT CONTRIBUTORS (HEAVYWEIGHTS)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            indexTracker.topContributors.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceHighlight)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.symbol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "wt ${item.weightPct}%",
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = item.companyName,
                            fontSize = 9.sp,
                            color = palette.textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${String.format(Locale.US, "%,.2f", item.ltp)} (${if (item.changePct >= 0) "+" else ""}${item.changePct}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.changePct >= 0) palette.bullGreen else palette.bearRed,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${if (item.pointContribution >= 0) "+" else ""}${item.pointContribution} pts",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.pointContribution >= 0) palette.bullGreen else palette.bearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpToolsSection(mcpState: NseMcpState) {
    val palette = LocalChartPalette.current
    Column {
        Text(
            text = "DISCOVERED MCP TOOLS ON ${mcpState.endpointUrl}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        mcpState.activeTools.forEach { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.accentCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tool.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tool.description,
                        fontSize = 10.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Params: ${tool.parametersDescription}",
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun McpRpcLogsSection(mcpState: NseMcpState) {
    val palette = LocalChartPalette.current
    Column {
        Text(
            text = "STREAMABLE HTTP / JSON-RPC 2.0 AUDIT LOGS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (mcpState.logs.isEmpty()) {
            Text(
                text = "No RPC logs captured yet. Tap 'Sync Bhavcopy' to trigger an MCP call.",
                fontSize = 11.sp,
                color = palette.textMuted
            )
        } else {
            mcpState.logs.take(8).forEach { log ->
                val isReq = log.direction == "OUTGOING_REQUEST"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isReq) "→ ${log.method}" else "← ${log.method}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isReq) palette.goldAccent else palette.bullGreen,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = log.id,
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.canvasBackground)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = log.payloadJson,
                                fontSize = 8.5.sp,
                                color = palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpConfigSection(
    mcpState: NseMcpState,
    onOpenConnectorsSettings: (() -> Unit)? = null
) {
    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Column {
        if (onOpenConnectorsSettings != null) {
            Button(
                onClick = onOpenConnectorsSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("mcp_dialog_open_connectors_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentCyan,
                    contentColor = onAccent
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Manage Custom MCP Plugins (Settings → Connectors)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text = "MCP CLIENT CONFIGURATION (CLAUDE / CUSTOM AGENTS)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "To configure official NSE MCP in Claude Desktop or external agents, paste the following JSON config according to https://www.nseindia.com/nse-mcp:",
            fontSize = 10.sp,
            color = palette.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        val configSnippet = """
{
  "mcpServers": {
    "nse-bhavcopy": {
      "url": "https://mcp.nseindia.in/bhavcopy/cm/mcp"
    },
    "cm-market": {
      "url": "https://mcp.nseindia.in/cmmkt/mcp"
    }
  }
}
        """.trimIndent()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.canvasBackground)
                .border(1.dp, palette.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = configSnippet,
                fontSize = 10.sp,
                color = palette.accentCyan,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun EndpointRow(name: String, url: String, status: String, isGreen: Boolean) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceHighlight)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = url,
                fontSize = 9.sp,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isGreen) palette.bullGreenDim else palette.goldAccentDim)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = status,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGreen) palette.bullGreen else palette.goldAccent,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalChartPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceVariant)
            .padding(8.dp)
    ) {
        Column {
            Text(text = title, fontSize = 9.sp, color = palette.textMuted, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = valueColor, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = sub, fontSize = 9.sp, color = palette.textSecondary)
        }
    }
}
