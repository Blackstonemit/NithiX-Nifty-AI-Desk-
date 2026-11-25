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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.IndstocksBrokerState
import com.example.model.IndstocksConnectionStatus
import com.example.ui.theme.LocalChartPalette
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun IndstocksBrokerSettingsCard(
    indstocksState: IndstocksBrokerState,
    onConnectWithToken: (token: String, clientId: String?) -> Unit,
    onGenerateTokenViaTotp: (clientId: String, mpin: String, totp: String) -> Unit,
    onSyncPortfolio: () -> Unit,
    onDisconnect: () -> Unit
) {
    val palette = LocalChartPalette.current
    var authTab by remember { mutableIntStateOf(0) } // 0: Daily Token, 1: TOTP Generator
    var tokenInput by remember(indstocksState.accessToken) { mutableStateOf(indstocksState.accessToken) }
    var showToken by remember { mutableStateOf(false) }

    var clientIdInput by remember(indstocksState.clientId) { mutableStateOf(indstocksState.clientId) }
    var mpinInput by remember { mutableStateOf("") }
    var showMpin by remember { mutableStateOf(false) }
    var totpInput by remember { mutableStateOf("") }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(indstocksState.totpCooldownUntilMs) {
        while (indstocksState.totpCooldownUntilMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }
    val cooldownSec = ((indstocksState.totpCooldownUntilMs - nowMs) / 1000L).coerceAtLeast(0L)

    val statusColor = when (indstocksState.status) {
        IndstocksConnectionStatus.CONNECTED -> palette.bullGreen
        IndstocksConnectionStatus.AUTHENTICATING -> palette.accentCyan
        IndstocksConnectionStatus.TOKEN_EXPIRED -> palette.goldAccent
        IndstocksConnectionStatus.ERROR -> palette.bearRed
        IndstocksConnectionStatus.DISCONNECTED -> palette.textMuted
    }
    val statusBg = when (indstocksState.status) {
        IndstocksConnectionStatus.CONNECTED -> palette.bullGreenDim
        IndstocksConnectionStatus.AUTHENTICATING -> palette.accentCyanDim
        IndstocksConnectionStatus.TOKEN_EXPIRED -> palette.goldAccentDim
        IndstocksConnectionStatus.ERROR -> palette.bearRedDim
        IndstocksConnectionStatus.DISCONNECTED -> palette.surfaceVariant
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentCyan,
        unfocusedBorderColor = palette.border,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        cursorColor = palette.accentCyan,
        focusedLabelColor = palette.accentCyan,
        unfocusedLabelColor = palette.textSecondary,
        focusedContainerColor = palette.surfaceVariant,
        unfocusedContainerColor = palette.surfaceVariant
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.2.dp,
                if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) palette.bullGreen.copy(alpha = 0.5f) else palette.border,
                RoundedCornerShape(12.dp)
            )
            .testTag("indstocks_broker_card"),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header + Status Badge
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) palette.bullGreenDim else palette.goldAccentDim)
                            .border(
                                1.dp,
                                if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) palette.bullGreen.copy(alpha = 0.6f) else palette.goldAccent.copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = stringResource(R.string.indstocks_title),
                            tint = if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) palette.bullGreen else palette.goldAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.indstocks_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = stringResource(
                                if (indstocksState.status == IndstocksConnectionStatus.CONNECTED) {
                                    R.string.indstocks_feed_connected
                                } else {
                                    R.string.indstocks_feed_fallback
                                }
                            ),
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = indstocksState.status.label.uppercase(),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Connected Profile & Funds Strip
            indstocksState.profile?.let { profile ->
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceHighlight)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.indstocks_profile_ucc_format,
                                profile.name,
                                profile.ucc.ifBlank { stringResource(R.string.indstocks_ucc_default) }
                            ),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.bullGreen,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(
                                if (profile.isDdpiActive) R.string.indstocks_ddpi_on else R.string.indstocks_ddpi_off
                            ),
                            fontSize = 9.5.sp,
                            color = if (profile.isDdpiActive) palette.bullGreen else palette.goldAccent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    indstocksState.funds?.let { funds ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.indstocks_funds_summary_format,
                                String.format(Locale.US, "%,.0f", funds.availableMargin),
                                indstocksState.holdings.size,
                                indstocksState.positions.size,
                                indstocksState.orderBook.size
                            ),
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.indstocks_streams_summary_format,
                                indstocksState.wsPriceStatus.label,
                                indstocksState.wsOrderStatus.label,
                                indstocksState.activeContractLotSize
                            ),
                            fontSize = 9.5.sp,
                            color = palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Switch: Daily Token vs TOTP Generator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    0 to stringResource(R.string.indstocks_tab_daily_token),
                    1 to stringResource(R.string.indstocks_tab_totp)
                ).forEach { (idx, label) ->
                    val selected = authTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) palette.accentCyanDim else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) palette.accentCyan else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { authTab = idx }
                            .padding(vertical = 8.dp)
                            .testTag("indstocks_auth_tab_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.accentCyan else palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (authTab == 0) {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(R.string.indstocks_token_label)) },
                    placeholder = { Text(stringResource(R.string.indstocks_token_placeholder), color = palette.textMuted) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showToken = !showToken },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showToken) R.string.cd_hide_token else R.string.cd_show_token
                                ),
                                tint = palette.textSecondary
                            )
                        }
                    },
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("indstocks_token_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onConnectWithToken(tokenInput, clientIdInput.ifBlank { null }) },
                    enabled = tokenInput.isNotBlank() && !indstocksState.isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("indstocks_connect_token_button")
                ) {
                    if (indstocksState.isBusy) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.indstocks_verifying), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.indstocks_verify_connect), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            } else {
                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = { clientIdInput = it },
                    label = { Text(stringResource(R.string.indstocks_client_id_label)) },
                    placeholder = { Text(stringResource(R.string.indstocks_client_id_placeholder), color = palette.textMuted) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("indstocks_client_id_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mpinInput,
                        onValueChange = { mpinInput = it },
                        label = { Text(stringResource(R.string.indstocks_mpin_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (showMpin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showMpin = !showMpin },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (showMpin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(
                                        if (showMpin) R.string.cd_hide_mpin else R.string.cd_show_mpin
                                    ),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("indstocks_mpin_input")
                    )
                    OutlinedTextField(
                        value = totpInput,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) totpInput = input
                        },
                        label = { Text(stringResource(R.string.indstocks_totp_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("indstocks_totp_input")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onGenerateTokenViaTotp(clientIdInput, mpinInput, totpInput) },
                    enabled = clientIdInput.isNotBlank() && mpinInput.isNotBlank() && totpInput.length == 6 && cooldownSec == 0L && !indstocksState.isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.goldAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("indstocks_generate_totp_button")
                ) {
                    Text(
                        text = if (cooldownSec > 0L) {
                            stringResource(R.string.indstocks_totp_cooldown_format, cooldownSec)
                        } else {
                            stringResource(R.string.indstocks_generate_token)
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Status / Error banners
            indstocksState.statusNotice?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    fontSize = 10.5.sp,
                    color = palette.bullGreen,
                    lineHeight = 14.sp
                )
            }
            indstocksState.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    fontSize = 10.5.sp,
                    color = palette.bearRed,
                    lineHeight = 14.sp
                )
            }

            if (indstocksState.accessToken.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSyncPortfolio,
                        enabled = !indstocksState.isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("indstocks_sync_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.indstocks_sync_portfolio), fontSize = 11.sp, color = palette.accentCyan, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("indstocks_disconnect_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = palette.bearRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.indstocks_disconnect), fontSize = 11.sp, color = palette.bearRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
