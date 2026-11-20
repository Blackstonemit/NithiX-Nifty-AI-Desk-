package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FinalTradeRecommendation
import com.example.model.SignalType
import com.example.ui.theme.LocalChartPalette

@Composable
fun SignalCard(
    recommendation: FinalTradeRecommendation,
    onAuthorizeTrade: () -> Unit
) {
    val palette = LocalChartPalette.current
    val isBullish = recommendation.signal == SignalType.BUY_CE || recommendation.signal == SignalType.BULLISH_FUTURES
    val isBearish = recommendation.signal == SignalType.BUY_PE || recommendation.signal == SignalType.BEARISH_FUTURES
    val isNoTrade = recommendation.signal == SignalType.NO_TRADE

    val accentColor = when {
        isBullish -> palette.bullGreen
        isBearish -> palette.bearRed
        isNoTrade -> palette.goldAccent
        else -> palette.accentCyan
    }

    val bannerBg = when {
        isBullish -> palette.bullGreenDim
        isBearish -> palette.bearRedDim
        isNoTrade -> palette.goldAccentDim
        else -> palette.surfaceHighlight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.5.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("signal_card")
    ) {
        // Top Ribbon: Signal Banner & Confidence Gauge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                isBullish -> Icons.Default.ArrowUpward
                                isBearish -> Icons.Default.ArrowDownward
                                else -> Icons.Default.Block
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = recommendation.signal.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when {
                        recommendation.confidenceScore >= 85 -> "VERY STRONG"
                        recommendation.confidenceScore >= 75 -> "STRONG"
                        recommendation.confidenceScore >= 60 -> "MODERATE"
                        else -> "CAPITAL DEFENSE"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Circular Confidence Score Gauge
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { recommendation.confidenceScore / 100f },
                    modifier = Modifier.size(46.dp),
                    color = accentColor,
                    trackColor = palette.border,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${recommendation.confidenceScore}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Contract details
        Text(
            text = recommendation.instrument,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Strategy: ${recommendation.strategyName}",
            fontSize = 11.sp,
            color = palette.accentCyan
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Levels Grid: Entry, SL, T1, T2, T3
        if (!isNoTrade) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LevelItem(label = "ENTRY ZONE", value = recommendation.entryZone, color = palette.textPrimary)
                LevelItem(label = "STOP-LOSS", value = recommendation.stopLoss, color = palette.bearRed)
                LevelItem(label = "TARGET 1", value = recommendation.target1, color = palette.bullGreen)
                LevelItem(label = "R : R", value = recommendation.riskToReward, color = palette.goldAccent)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LevelItem(label = "TARGET 2", value = recommendation.target2, color = palette.bullGreen)
                LevelItem(label = "TARGET 3", value = recommendation.target3, color = palette.bullGreen)
                LevelItem(label = "HORIZON", value = recommendation.expectedHoldingPeriod, color = palette.textSecondary)
            }
        } else {
            // No trade box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(10.dp)
            ) {
                Text(
                    text = recommendation.tradeRationale,
                    fontSize = 12.sp,
                    color = palette.goldAccent,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Setup Invalidation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Invalidation: ${recommendation.setupInvalidationLevel}",
                fontSize = 11.sp,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary Action: Authorize Order (Institutional Order Execution Requirement)
        if (!isNoTrade) {
            Button(
                onClick = onAuthorizeTrade,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("authorize_order_button"),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Authorize",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTHORIZE ORDER (${recommendation.signal.label})",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelItem(label: String, value: String, color: Color) {
    val palette = LocalChartPalette.current
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            color = palette.textMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
