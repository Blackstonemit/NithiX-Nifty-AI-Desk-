package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MarketSessionStatus
import com.example.model.Candle
import com.example.model.ChartType
import com.example.model.FuturesData
import com.example.model.MarketBias
import com.example.model.NiftySpotData
import com.example.model.OptionsChainSnapshot
import com.example.model.SmartMoneySnapshot
import com.example.model.TechnicalAnalysisSnapshot
import com.example.model.Timeframe
import com.example.ui.viewmodel.ChartWorkstationUiState
import com.example.ui.theme.LocalChartPalette
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NiftyDashboardView(
    spot: NiftySpotData,
    futures: FuturesData,
    optionsChain: OptionsChainSnapshot,
    technicals: TechnicalAnalysisSnapshot,
    smartMoney: SmartMoneySnapshot,
    candles: List<Candle> = emptyList(),
    selectedTimeframe: Timeframe = Timeframe.M15,
    onSelectTimeframe: (Timeframe) -> Unit = {},
    sessionStatus: MarketSessionStatus? = null,
    isStreaming: Boolean = false,
    isLiveMarketData: Boolean = false,
    dataSourceLabel: String = "",
    latencyMs: Long,
    onNavigateToAgents: () -> Unit,
    onNavigateToOptions: () -> Unit,
    workstationState: ChartWorkstationUiState? = null,
    onExpandWorkstation: (() -> Unit)? = null,
    onSelectChartType: ((ChartType) -> Unit)? = null,
    onOpenAiAnalyst: (() -> Unit)? = null,
    onOpenOhlcvTerminal: (() -> Unit)? = null
) {
    val palette = LocalChartPalette.current
    val resolvedDataSourceLabel = dataSourceLabel.ifBlank {
        stringResource(R.string.dashboard_cached_nse_ref)
    }
    val isExchangeOpen = sessionStatus?.isExchangeOpen == true
    val isBullish = technicals.trendDirection == MarketBias.BULLISH
    val isBearish = technicals.trendDirection == MarketBias.BEARISH
    val trendColor = when {
        isBullish -> palette.bullGreen
        isBearish -> palette.bearRed
        else -> palette.goldAccent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nifty_main_dashboard")
    ) {
        // Hero Section: NIFTY 50 Spot + India VIX Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: NIFTY 50 Spot Card
            Card(
                modifier = Modifier
                    .weight(1.55f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val dotColor = when {
                        isExchangeOpen -> palette.bullGreen
                        isStreaming -> palette.accentCyan
                        isLiveMarketData -> palette.bullGreen
                        else -> palette.bearRed
                    }
                    val spotTitle = when {
                        isExchangeOpen -> stringResource(R.string.dashboard_nifty_50_live)
                        isStreaming -> stringResource(R.string.dashboard_nifty_50_sim)
                        else -> stringResource(R.string.dashboard_nifty_50)
                    }
                    val badgeText = when {
                        isStreaming && !isExchangeOpen -> stringResource(R.string.dashboard_badge_replay)
                        !isExchangeOpen -> stringResource(R.string.dashboard_badge_closed)
                        else -> spot.gapType
                    }

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
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = spotTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        !isExchangeOpen && !isStreaming -> palette.bearRedDim
                                        else -> palette.surfaceHighlight
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (!isExchangeOpen && !isStreaming) palette.bearRed.copy(alpha = 0.5f) else palette.border,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isStreaming && !isExchangeOpen -> palette.accentCyan
                                    !isExchangeOpen -> palette.bearRed
                                    spot.gapType.contains("UP") -> palette.bullGreen
                                    spot.gapType.contains("DOWN") -> palette.bearRed
                                    else -> palette.goldAccent
                                },
                                maxLines = 1,
                                softWrap = false,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = String.format(Locale.US, "%,.2f", spot.spotPrice),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        color = palette.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (spot.change >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (spot.change >= 0) palette.bullGreen else palette.bearRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${if (spot.change >= 0) "+" else ""}${String.format(Locale.US, "%.2f", spot.change)} (${String.format(Locale.US, "%.2f", spot.changePct)}%)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (spot.change >= 0) palette.bullGreen else palette.bearRed,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!isExchangeOpen && isStreaming) {
                            stringResource(R.string.dashboard_synthetic_replay_active)
                        } else {
                            resolvedDataSourceLabel
                        },
                        fontSize = 9.sp,
                        color = if (isLiveMarketData) palette.bullGreen else palette.textMuted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Range Bar: Day Low to Day High
                    val dayRange = maxOf(spot.dayHigh - spot.dayLow, 1.0)
                    val progress = ((spot.spotPrice - spot.dayLow) / dayRange).toFloat().coerceIn(0f, 1f)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_day_low_format, spot.dayLow.toInt()),
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = stringResource(R.string.dashboard_day_high_format, spot.dayHigh.toInt()),
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = palette.accentCyan,
                            trackColor = palette.surfaceHighlight
                        )
                    }
                }
            }

            // Card 2: India VIX Card
            Card(
                modifier = Modifier
                    .weight(0.95f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_india_vix),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = String.format(Locale.US, "%.2f", spot.indiaVix),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = palette.goldAccent,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${if (spot.vixChangePct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", spot.vixChangePct)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (spot.vixChangePct >= 0) palette.bearRed else palette.bullGreen,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Volatility Regime Badge
                    val vixRegime = stringResource(
                        when {
                            spot.indiaVix < 13.0 -> R.string.dashboard_vix_low
                            spot.indiaVix > 18.0 -> R.string.dashboard_vix_high
                            else -> R.string.dashboard_vix_normal
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.goldAccentDim)
                            .padding(vertical = 3.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = vixRegime,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.goldAccent,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Lightweight Native Compose CandlestickChart + Volume Bar Chart
        if (candles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            CandlestickChart(
                candles = candles,
                vwapValue = technicals.vwap,
                emaValue = technicals.ema20,
                selectedTimeframe = selectedTimeframe,
                onSelectTimeframe = onSelectTimeframe,
                isStreaming = isStreaming,
                isMarketOpen = isExchangeOpen,
                workstationState = workstationState,
                onExpandWorkstation = onExpandWorkstation,
                onSelectChartType = onSelectChartType,
                onOpenAiAnalyst = onOpenAiAnalyst,
                onOpenOhlcvTerminal = onOpenOhlcvTerminal
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Market Structure & Trend Confluence Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, trendColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_trend_summary_header),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBullish) palette.bullGreenDim else if (isBearish) palette.bearRedDim else palette.goldAccentDim)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = technicals.trendDirection.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = trendColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(
                        R.string.dashboard_structure_format,
                        spot.marketStructure,
                        technicals.momentumStrength
                    ),
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Key Market Trend Pillars (Grid)
                val vwapRel = stringResource(
                    if (spot.spotPrice > technicals.vwap) R.string.dashboard_pillar_above else R.string.dashboard_pillar_below
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PillarChip(
                        label = stringResource(R.string.dashboard_pillar_vwap),
                        value = stringResource(R.string.dashboard_pillar_vwap_val_format, technicals.vwap.toInt(), vwapRel),
                        isPositive = spot.spotPrice > technicals.vwap
                    )
                    PillarChip(
                        label = stringResource(R.string.dashboard_pillar_rsi_14),
                        value = String.format(Locale.US, "%.1f", technicals.rsi14),
                        isPositive = technicals.rsi14 > 50.0
                    )
                    PillarChip(
                        label = stringResource(R.string.options_metric_pcr_oi),
                        value = String.format(Locale.US, "%.2f", optionsChain.pcr),
                        isPositive = optionsChain.pcr > 1.0
                    )
                    PillarChip(
                        label = stringResource(R.string.dashboard_pillar_supertrend),
                        value = stringResource(if (technicals.supertrendBullish) R.string.dashboard_bullish else R.string.dashboard_bearish),
                        isPositive = technicals.supertrendBullish
                    )
                    PillarChip(
                        label = stringResource(R.string.dashboard_pillar_fii_long),
                        value = stringResource(R.string.agents_pct_format, futures.fiiNetRatio.toInt()),
                        isPositive = futures.fiiNetRatio > 50.0
                    )
                    PillarChip(
                        label = stringResource(R.string.dashboard_pillar_atm_straddle),
                        value = stringResource(R.string.label_inr_amount_format, optionsChain.atmStraddlePremium.toInt().toString()),
                        isPositive = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Options Chain Key Strikes Summary (Support vs Resistance)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_call_wall_res),
                            fontSize = 9.sp,
                            color = palette.bearRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.report_ce_strike_format, optionsChain.maxCallOiStrike.toInt()),
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accentCyanDim)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_atm_format, optionsChain.atmStrike.toInt()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.dashboard_put_wall_supp),
                            fontSize = 9.sp,
                            color = palette.bullGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.report_pe_strike_format, optionsChain.maxPutOiStrike.toInt()),
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillarChip(label: String, value: String, isPositive: Boolean) {
    val palette = LocalChartPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                fontSize = 10.sp,
                color = palette.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                fontSize = 10.sp,
                color = if (isPositive) palette.bullGreen else palette.bearRed,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
