package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.OptionStrike
import com.example.model.OptionsChainSnapshot
import com.example.ui.theme.LocalChartPalette
import java.util.Locale
import kotlin.math.abs

@Composable
fun OptionsChainScreen(
    optionsChain: OptionsChainSnapshot,
    spotPrice: Double,
    availableExpiries: List<String> = emptyList(),
    selectedExpiry: String? = null,
    isBrokerConnected: Boolean = false,
    onSelectExpiry: (String) -> Unit = {}
) {
    val palette = LocalChartPalette.current
    var selectedStrikeForGreeks by remember { mutableStateOf<OptionStrike?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(12.dp)
            .testTag("options_chain_screen")
    ) {
        // Options Chain Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NIFTY OPTIONS CHAIN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isBrokerConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.bullGreenDim)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "INDSTOCKS",
                                    fontSize = 8.5.sp,
                                    color = palette.bullGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Text(
                        text = optionsChain.expiryDate,
                        fontSize = 11.sp,
                        color = palette.goldAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (availableExpiries.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableExpiries.take(6)) { expiry ->
                            val isSelected = expiry == (selectedExpiry ?: optionsChain.expiryDate)
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelectExpiry(expiry) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) palette.accentCyanDim else palette.surfaceHighlight)
                                        .border(
                                            1.dp,
                                            if (isSelected) palette.accentCyan else palette.border,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = expiry,
                                        fontSize = 10.sp,
                                        color = if (isSelected) palette.accentCyan else palette.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMini(label = "PCR (OI)", value = String.format(Locale.US, "%.2f", optionsChain.pcr), color = if (optionsChain.pcr > 1.0) palette.bullGreen else palette.bearRed)
                    MetricMini(label = "MAX CALL (RES)", value = "${optionsChain.maxCallOiStrike.toInt()}", color = palette.bearRed)
                    MetricMini(label = "MAX PUT (SUPP)", value = "${optionsChain.maxPutOiStrike.toInt()}", color = palette.bullGreen)
                    MetricMini(label = "ATM STRADDLE", value = "₹${optionsChain.atmStraddlePremium.toInt()}", color = palette.accentCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surfaceHighlight)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "CALL LTP | CHG OI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.bearRed, modifier = Modifier.weight(1.3f))
            Text(text = "STRIKE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = palette.accentCyan, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
            Text(text = "CHG OI | PUT LTP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.bullGreen, modifier = Modifier.weight(1.3f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Options Strikes Lazy List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(optionsChain.strikes) { strike ->
                StrikeRowItem(
                    strike = strike,
                    isAtm = strike.isAtm,
                    onClick = { selectedStrikeForGreeks = strike }
                )
            }
        }
    }

    // Strike Greeks Modal Dialog
    selectedStrikeForGreeks?.let { strike ->
        GreeksDialog(strike = strike, onDismiss = { selectedStrikeForGreeks = null })
    }
}

@Composable
private fun StrikeRowItem(
    strike: OptionStrike,
    isAtm: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalChartPalette.current
    val rowBg = when {
        isAtm -> palette.accentCyanDim.copy(alpha = 0.4f)
        strike.isMaxCallOi -> palette.bearRedDim.copy(alpha = 0.3f)
        strike.isMaxPutOi -> palette.bullGreenDim.copy(alpha = 0.3f)
        else -> palette.surface
    }

    val borderColor = when {
        isAtm -> palette.accentCyan
        strike.isMaxCallOi -> palette.bearRed
        strike.isMaxPutOi -> palette.bullGreen
        else -> palette.border
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Call Side: LTP & OI Change
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = "₹${String.format(Locale.US, "%.1f", strike.callLtp)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${if (strike.callOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", strike.callOiChange / 1000)}k",
                fontSize = 9.sp,
                color = if (strike.callOiChange >= 0) palette.bearRed else palette.bullGreen,
                fontFamily = FontFamily.Monospace
            )
        }

        // Center Strike Badge
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isAtm) palette.accentCyan else palette.surfaceHighlight)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${strike.strikePrice.toInt()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isAtm) Color.Black else palette.textPrimary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Put Side: OI Change & LTP
        Column(modifier = Modifier.weight(1.3f), horizontalAlignment = Alignment.End) {
            Text(
                text = "₹${String.format(Locale.US, "%.1f", strike.putLtp)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${if (strike.putOiChange >= 0) "+" else ""}${String.format(Locale.US, "%,d", strike.putOiChange / 1000)}k",
                fontSize = 9.sp,
                color = if (strike.putOiChange >= 0) palette.bullGreen else palette.bearRed,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun GreeksDialog(strike: OptionStrike, onDismiss: () -> Unit) {
    val palette = LocalChartPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.accentCyan, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STRIKE ${strike.strikePrice.toInt()} GREEKS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accentCyan,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "CALL (CE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bearRed)
                    GreekItem(label = "Delta", value = String.format(Locale.US, "%.2f", strike.callDelta))
                    GreekItem(label = "Gamma", value = String.format(Locale.US, "%.4f", strike.callGamma))
                    GreekItem(label = "Theta / Day", value = "-₹${String.format(Locale.US, "%.1f", abs(strike.callTheta))}")
                    GreekItem(label = "Vega", value = "₹${String.format(Locale.US, "%.2f", strike.callVega)}")
                    GreekItem(label = "IV", value = "${String.format(Locale.US, "%.1f", strike.callIv)}%")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(text = "PUT (PE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.bullGreen)
                    GreekItem(label = "Delta", value = String.format(Locale.US, "%.2f", strike.putDelta), alignEnd = true)
                    GreekItem(label = "Gamma", value = String.format(Locale.US, "%.4f", strike.putGamma), alignEnd = true)
                    GreekItem(label = "Theta / Day", value = "-₹${String.format(Locale.US, "%.1f", abs(strike.putTheta))}", alignEnd = true)
                    GreekItem(label = "Vega", value = "₹${String.format(Locale.US, "%.2f", strike.putVega)}", alignEnd = true)
                    GreekItem(label = "IV", value = "${String.format(Locale.US, "%.1f", strike.putIv)}%", alignEnd = true)
                }
            }
        }
    }
}

@Composable
private fun GreekItem(label: String, value: String, alignEnd: Boolean = false) {
    val palette = LocalChartPalette.current
    Spacer(modifier = Modifier.height(6.dp))
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(text = label, fontSize = 9.sp, color = palette.textMuted)
        Text(text = value, fontSize = 11.sp, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MetricMini(label: String, value: String, color: Color) {
    val palette = LocalChartPalette.current
    Column {
        Text(text = label, fontSize = 9.sp, color = palette.textMuted, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
