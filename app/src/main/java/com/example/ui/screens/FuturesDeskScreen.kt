package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FuturesData
import com.example.model.MarketBias
import com.example.ui.theme.LocalChartPalette
import java.util.Locale

@Composable
fun FuturesDeskScreen(
    futures: FuturesData,
    spotPrice: Double
) {
    val palette = LocalChartPalette.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(12.dp)
            .testTag("futures_desk_screen")
    ) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.futures_desk_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Contract Basis & Price Comparison Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = stringResource(R.string.futures_contracts_header), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = stringResource(R.string.futures_near_month_format, futures.currentMonthExpiry), fontSize = 10.sp, color = palette.textMuted)
                        Text(
                            text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.2f", futures.currentMonthPrice)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        val basisFormatted = "${if (futures.basis >= 0) "+" else ""}${String.format(Locale.US, "%.1f", futures.basis)}"
                        val basisKind = stringResource(if (futures.basis > 0) R.string.futures_premium else R.string.futures_discount)
                        Text(
                            text = stringResource(R.string.futures_basis_format, basisFormatted, basisKind),
                            fontSize = 11.sp,
                            color = if (futures.basis >= 0) palette.bullGreen else palette.bearRed,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = stringResource(R.string.futures_next_month_format, futures.nextMonthExpiry), fontSize = 10.sp, color = palette.textMuted)
                        Text(
                            text = stringResource(R.string.label_inr_amount_format, String.format(Locale.US, "%,.2f", futures.nextMonthPrice)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(R.string.futures_roll_spread_format, String.format(Locale.US, "%.1f", futures.nextMonthPrice - futures.currentMonthPrice)),
                            fontSize = 11.sp,
                            color = palette.accentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Buildup badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceHighlight)
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = stringResource(R.string.futures_buildup_header), fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
                            Text(text = futures.currentMonthBuildup.label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (futures.currentMonthBuildup.name.contains("LONG")) palette.bullGreen else palette.bearRed)
                        }
                        Text(
                            text = stringResource(R.string.futures_oi_contracts_format, String.format(Locale.US, "%,d", futures.currentMonthOi)),
                            fontSize = 11.sp,
                            color = palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // FII / DII Institutional Flow Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.futures_fii_dii_header), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FII Long vs Short Bar
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.futures_fii_long_pct_format, String.format(Locale.US, "%.1f", futures.fiiNetRatio)),
                        fontSize = 11.sp,
                        color = palette.bullGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.futures_fii_short_pct_format, String.format(Locale.US, "%.1f", 100.0 - futures.fiiNetRatio)),
                        fontSize = 11.sp,
                        color = palette.bearRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (futures.fiiNetRatio / 100f).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = palette.bullGreen,
                    trackColor = palette.bearRed
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.futures_fii_long_contracts_format, String.format(Locale.US, "%,d", futures.fiiLongContracts)),
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.futures_fii_short_contracts_format, String.format(Locale.US, "%,d", futures.fiiShortContracts)),
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DII Flow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceHighlight)
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.futures_dii_net_flow_label), fontSize = 11.sp, color = palette.textSecondary)
                        Text(
                            text = stringResource(
                                R.string.futures_dii_net_flow_value_format,
                                if (futures.diiNetActivityCr >= 0) "+" else "",
                                String.format(Locale.US, "%,.1f", futures.diiNetActivityCr)
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (futures.diiNetActivityCr >= 0) palette.bullGreen else palette.bearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
