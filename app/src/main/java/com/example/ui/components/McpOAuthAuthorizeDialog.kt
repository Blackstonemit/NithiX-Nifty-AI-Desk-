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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.McpConnectorConfig
import com.example.model.McpPendingOAuthFlow
import com.example.ui.theme.LocalChartPalette

@Composable
fun McpOAuthAuthorizeDialog(
    connector: McpConnectorConfig,
    activeFlow: McpPendingOAuthFlow?,
    isBusy: Boolean,
    statusMessage: String?,
    onDiscoverMetadata: () -> Unit,
    onStartOAuthBrowser: (explicitClientId: String, useCustomSchemeRedirect: Boolean) -> Unit,
    onSubmitManualCodeOrCallbackUrl: (String) -> Unit,
    onApplyManualBearerToken: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var explicitClientId by remember(connector.id) { mutableStateOf(connector.oauthClientId) }
    var useCustomScheme by remember { mutableStateOf(false) }
    var manualCodeOrUrl by remember { mutableStateOf("") }
    var showTokenFallback by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }

    LaunchedEffect(connector.id) {
        if (connector.oauthMetadata == null) {
            onDiscoverMetadata()
        }
    }

    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentCyan,
        unfocusedBorderColor = palette.border,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        cursorColor = palette.accentCyan,
        focusedLabelColor = palette.accentCyan,
        unfocusedLabelColor = palette.textSecondary,
        focusedContainerColor = palette.surface,
        unfocusedContainerColor = palette.surface
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(1.5.dp, palette.goldAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("mcp_oauth_dialog")
        ) {
            // Header
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.goldAccentDim)
                            .border(1.dp, palette.goldAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = palette.goldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Authorize ${connector.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = connector.url,
                            fontSize = 10.sp,
                            color = palette.accentCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_oauth_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close authorization dialog",
                        tint = palette.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Discovery Metadata Summary
            val meta = connector.oauthMetadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MCP OAUTH 2.1 (PKCE S256)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.goldAccent,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = onDiscoverMetadata,
                        enabled = !isBusy,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh OAuth metadata",
                            tint = palette.accentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discover", fontSize = 10.sp, color = palette.accentCyan)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (meta != null) {
                    MetadataLine("Issuer", meta.issuer ?: meta.resourceUrl)
                    MetadataLine("Authorize", meta.authorizationEndpoint ?: "Not discovered")
                    MetadataLine("Token", meta.tokenEndpoint ?: "Not discovered")
                    MetadataLine(
                        "Client Reg",
                        meta.registrationEndpoint ?: "Manual Client ID / Pre-registered"
                    )
                    if (meta.scopesSupported.isNotEmpty()) {
                        MetadataLine("Scopes", meta.scopesSupported.joinToString(" "))
                    }
                } else {
                    Text(
                        text = "Remote MCP server returned HTTP 401. Tap Discover or launch OAuth 2.1 browser authorization, or paste a Bearer token below.",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 11.sp,
                        color = if (statusMessage.contains("failed", ignoreCase = true) ||
                            statusMessage.contains("denied", ignoreCase = true) ||
                            statusMessage.contains("error", ignoreCase = true)
                        ) palette.bearRed else palette.bullGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Optional Client ID & Redirect URI Mode
            OutlinedTextField(
                value = explicitClientId,
                onValueChange = { explicitClientId = it },
                label = { Text("OAuth Client ID (optional if auto-registered)") },
                placeholder = { Text("Auto-discovered via /register", color = palette.textMuted) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oauth_client_id_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "REDIRECT CALLBACK MODE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(false to "Loopback (127.0.0.1)", true to "App Deep Link").forEach { (customFlag, label) ->
                    val selected = useCustomScheme == customFlag
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyanDim else palette.surface)
                            .border(
                                1.dp,
                                if (selected) palette.accentCyan else palette.border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { useCustomScheme = customFlag }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.accentCyan else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onStartOAuthBrowser(explicitClientId.trim(), useCustomScheme) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentCyan,
                    contentColor = onAccent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("launch_oauth_browser_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = onAccent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorizing...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign In in Browser (OAuth 2.1)", fontWeight = FontWeight.ExtraBold)
                }
            }

            if (activeFlow != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.goldAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Browser opened — waiting for redirect (${activeFlow.redirectUri}). If your browser didn't auto-return, paste the redirected URL or code below:",
                        fontSize = 10.5.sp,
                        color = palette.textSecondary,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualCodeOrUrl,
                        onValueChange = { manualCodeOrUrl = it },
                        label = { Text("Redirect Callback URL or Auth Code") },
                        placeholder = { Text("Paste ?code=... URL here", color = palette.textMuted) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("oauth_manual_code_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onSubmitManualCodeOrCallbackUrl(manualCodeOrUrl) },
                        enabled = manualCodeOrUrl.isNotBlank() && !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("submit_oauth_code_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Exchange Code & Connect",
                            color = palette.goldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = palette.border)
            Spacer(modifier = Modifier.height(10.dp))

            // Direct Bearer Token Fallback
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .clickable { showTokenFallback = !showTokenFallback }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Or paste an existing Bearer Access Token",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary
                )
                Icon(
                    imageVector = if (showTokenFallback) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle manual bearer token",
                    tint = palette.textMuted
                )
            }

            if (showTokenFallback) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Bearer Access Token") },
                    placeholder = { Text("Paste session / OAuth access token", color = palette.textMuted) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("oauth_manual_token_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onApplyManualBearerToken(manualToken) },
                    enabled = manualToken.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("apply_bearer_token_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Save Token & Connect",
                        color = palette.accentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    val palette = LocalChartPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textMuted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(74.dp)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = palette.textPrimary,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
