package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.DeskState
import com.example.data.MarketSessionStatus
import com.example.model.FuturesData
import com.example.model.NiftySpotData
import com.example.ui.theme.LocalChartPalette

@Composable
fun HeaderTicker(
    spot: NiftySpotData,
    futures: FuturesData,
    isStreaming: Boolean,
    selectedScenario: String,
    sessionStatus: MarketSessionStatus,
    onToggleStreaming: () -> Unit,
    onSelectScenario: (String) -> Unit,
    latencyMs: Long,
    onOpenMcpDesk: () -> Unit = {},
    isLiveMarketData: Boolean = false,
    isRefreshing: Boolean = false,
    lastSyncLabel: String = "",
    onRefreshLive: () -> Unit = {}
) {
    val palette = LocalChartPalette.current
    var scenarioDropdownOpen by remember { mutableStateOf(false) }

    val scenarios = listOf(
        DeskState.SCENARIO_LIVE_FEED,
        "Bullish Breakout (CE Expansion)",
        "Bearish Breakdown (PE Rally)",
        "Expiry Day Pinning (Range-Bound)",
        "Conflicting Signals (No-Trade Trap)"
    )

    val statusColor = Color(sessionStatus.statusColorHex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("header_ticker")
    ) {
        // Row 1: Exchange Session Pill & IST Clock (no clipping)
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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (sessionStatus.isExchangeOpen) palette.bullGreen else statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (sessionStatus.isExchangeOpen) palette.bullGreenDim else palette.bearRedDim)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sessionStatus.sessionLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = sessionStatus.currentTimeIst,
                fontSize = 10.sp,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: Action Controls (Feed Mode, NSE MCP, Latency, Refresh, Play/Pause, Preset Picker)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live vs Cached Data Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLiveMarketData) palette.bullGreenDim else palette.goldAccentDim)
                        .border(
                            1.dp,
                            if (isLiveMarketData) palette.bullGreen.copy(alpha = 0.5f) else palette.goldAccent.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isLiveMarketData) "LIVE DATA" else "CACHED REF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLiveMarketData) palette.bullGreen else palette.goldAccent,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Official NSE MCP Badge
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenMcpDesk() }
                        .testTag("open_mcp_badge_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accentCyanDim)
                            .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentCyan)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NSE MCP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentCyan,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Sub-50ms Latency Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.bullGreenDim)
                        .border(1.dp, palette.bullGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${latencyMs}ms",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.bullGreen,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live Refresh Button (accessible touch target)
                IconButton(
                    onClick = onRefreshLive,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_live_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = palette.accentCyan,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Live Market Data",
                            tint = palette.accentCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Play / Pause Replay Button
                IconButton(
                    onClick = onToggleStreaming,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_stream_button")
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Live Simulation",
                        tint = if (isStreaming) palette.accentCyan else palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Scenario / Mode Dropdown Button (always fits cleanly)
                Box {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { scenarioDropdownOpen = true }
                            .testTag("scenario_picker_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.surfaceHighlight)
                                .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Change Scenario",
                                tint = palette.goldAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Preset",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = scenarioDropdownOpen,
                        onDismissRequest = { scenarioDropdownOpen = false },
                        modifier = Modifier.background(palette.surfaceHighlight)
                    ) {
                        scenarios.forEach { scenario ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = scenario,
                                        fontSize = 12.sp,
                                        color = if (scenario == selectedScenario) palette.accentCyan else palette.textPrimary,
                                        fontWeight = if (scenario == selectedScenario) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectScenario(scenario)
                                    scenarioDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Market Closed / Live Sync Notice
        if (!sessionStatus.isExchangeOpen) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isStreaming) palette.accentCyanDim else palette.goldAccentDim)
                    .border(
                        1.dp,
                        if (isStreaming) palette.accentCyan.copy(alpha = 0.4f) else palette.goldAccent.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Market Closed Notice",
                        tint = if (isStreaming) palette.accentCyan else palette.goldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isStreaming) {
                            "Off-hours Replay Simulator active. NIFTY 50 ticks are synthetic for strategy testing."
                        } else if (isLiveMarketData) {
                            "NSE is closed (Mon-Fri 09:15-15:30 IST). Showing latest official NIFTY 50 close. Tap ▶ to test Replay Simulator."
                        } else {
                            "NSE is closed (09:15-15:30 IST). Showing cached NSE close reference. Tap ↻ to retry live sync or ▶ for Replay."
                        },
                        fontSize = 10.sp,
                        color = if (isStreaming) palette.accentCyan else palette.goldAccent,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active Mode / Sync Timestamp & Next Session Schedule
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isStreaming -> "Replay: $selectedScenario"
                    lastSyncLabel.isNotBlank() -> "$selectedScenario • $lastSyncLabel"
                    else -> "Mode: $selectedScenario"
                },
                fontSize = 9.5.sp,
                color = if (isStreaming) palette.accentCyan else palette.textMuted,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sessionStatus.nextSessionText,
                fontSize = 9.sp,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
