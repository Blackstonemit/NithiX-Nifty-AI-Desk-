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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.McpAuthType
import com.example.model.McpConnectorConfig
import com.example.model.McpTransportType
import com.example.ui.theme.LocalChartPalette

@Composable
fun AddCustomConnectorDialog(
    initialConnector: McpConnectorConfig? = null,
    validateUrl: (String) -> String?,
    onConfirm: (
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String>,
        connectImmediately: Boolean,
        oauthClientId: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = initialConnector != null
    var name by remember { mutableStateOf(initialConnector?.name ?: "") }
    var serverUrl by remember { mutableStateOf(initialConnector?.url ?: "") }
    var transportType by remember {
        mutableStateOf(initialConnector?.transportType ?: McpTransportType.STREAMABLE_HTTP)
    }
    var authType by remember {
        mutableStateOf(initialConnector?.authType ?: McpAuthType.NONE)
    }
    val defaultHeaderName = stringResource(R.string.add_connector_header_name_placeholder)
    var authHeaderName by remember {
        mutableStateOf(initialConnector?.authHeaderName ?: defaultHeaderName)
    }
    var authSecret by remember {
        mutableStateOf(initialConnector?.authSecret ?: "")
    }
    var oauthClientId by remember {
        mutableStateOf(initialConnector?.oauthClientId ?: "")
    }
    var showSecret by remember { mutableStateOf(false) }
    var showAdvanced by remember {
        mutableStateOf(initialConnector?.customHeaders?.isNotEmpty() == true)
    }
    var customHeadersText by remember {
        mutableStateOf(
            initialConnector?.customHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: ""
        )
    }
    var connectImmediately by remember { mutableStateOf(true) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val nameRequiredMessage = stringResource(R.string.add_connector_name_required)

    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentCyan,
        unfocusedBorderColor = palette.border,
        errorBorderColor = palette.bearRed,
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
                .border(1.5.dp, palette.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("add_custom_connector_dialog")
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.accentCyanDim)
                            .border(1.dp, palette.accentCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = palette.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (isEditing) R.string.add_connector_edit_title else R.string.add_connector_add_title
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.add_connector_subtitle),
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_add_connector_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_add_connector),
                        tint = palette.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (nameError != null) nameError = null
                },
                label = { Text(stringResource(R.string.add_connector_name_label)) },
                placeholder = { Text(stringResource(R.string.add_connector_name_placeholder), color = palette.textMuted) },
                isError = nameError != null,
                supportingText = nameError?.let { err -> { Text(err, color = palette.bearRed, fontSize = 11.sp) } },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connector_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Remote MCP Server URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    if (urlError != null) urlError = null
                },
                label = { Text(stringResource(R.string.add_connector_url_label)) },
                placeholder = { Text(stringResource(R.string.add_connector_url_placeholder), color = palette.textMuted) },
                isError = urlError != null,
                supportingText = urlError?.let { err -> { Text(err, color = palette.bearRed, fontSize = 11.sp) } }
                    ?: {
                        Text(
                            stringResource(R.string.add_connector_url_support_hint),
                            color = palette.textMuted,
                            fontSize = 10.sp
                        )
                    },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connector_url_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Transport Type
            Text(
                text = stringResource(R.string.add_connector_transport_header),
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
                McpTransportType.values().forEach { mode ->
                    val selected = transportType == mode
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
                            .clickable { transportType = mode }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.accentCyan else palette.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Authentication Section
            Text(
                text = stringResource(R.string.add_connector_auth_header),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                McpAuthType.values().toList().chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { auth ->
                            val selected = authType == auth
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
                                    .clickable { authType = auth }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                                    .testTag("connector_auth_${auth.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = auth.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) palette.accentCyan else palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (authType == McpAuthType.OAUTH_PKCE) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = oauthClientId,
                    onValueChange = { oauthClientId = it },
                    label = { Text(stringResource(R.string.add_connector_oauth_client_label)) },
                    placeholder = { Text(stringResource(R.string.add_connector_oauth_client_placeholder), color = palette.textMuted) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.add_connector_oauth_client_hint),
                            color = palette.textMuted,
                            fontSize = 10.sp
                        )
                    },
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connector_oauth_client_id_input")
                )
            }

            if (authType == McpAuthType.API_KEY_HEADER) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = authHeaderName,
                    onValueChange = { authHeaderName = it },
                    label = { Text(stringResource(R.string.add_connector_header_name_label)) },
                    placeholder = { Text(stringResource(R.string.add_connector_header_name_placeholder), color = palette.textMuted) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connector_auth_header_input")
                )
            }

            if (authType == McpAuthType.BEARER_TOKEN || authType == McpAuthType.API_KEY_HEADER) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = authSecret,
                    onValueChange = { authSecret = it },
                    label = {
                        Text(
                            stringResource(
                                if (authType == McpAuthType.BEARER_TOKEN) {
                                    R.string.add_connector_bearer_token_label
                                } else {
                                    R.string.add_connector_api_key_label
                                }
                            )
                        )
                    },
                    placeholder = { Text(stringResource(R.string.add_connector_secret_placeholder), color = palette.textMuted) },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showSecret = !showSecret },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showSecret) R.string.cd_hide_secret else R.string.cd_show_secret
                                ),
                                tint = palette.textSecondary
                            )
                        }
                    },
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connector_auth_secret_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advanced Settings Accordion (Custom Headers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_connector_advanced_toggle),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_toggle_advanced),
                    tint = palette.textMuted
                )
            }

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customHeadersText,
                    onValueChange = { customHeadersText = it },
                    label = { Text(stringResource(R.string.add_connector_custom_headers_label)) },
                    placeholder = { Text(stringResource(R.string.add_connector_custom_headers_placeholder), color = palette.textMuted) },
                    minLines = 2,
                    maxLines = 4,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-connect checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { connectImmediately = !connectImmediately }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = connectImmediately,
                    onCheckedChange = { connectImmediately = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = palette.accentCyan,
                        uncheckedColor = palette.textMuted,
                        checkmarkColor = onAccent
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.add_connector_auto_connect),
                    fontSize = 12.sp,
                    color = palette.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedUrl = serverUrl.trim()
                        val errName = if (trimmedName.isBlank()) nameRequiredMessage else null
                        val errUrl = validateUrl(trimmedUrl)
                        nameError = errName
                        urlError = errUrl
                        if (errName == null && errUrl == null) {
                            val parsedHeaders = parseCustomHeaders(customHeadersText)
                            onConfirm(
                                trimmedName,
                                trimmedUrl,
                                transportType,
                                authType,
                                authHeaderName,
                                authSecret,
                                parsedHeaders,
                                connectImmediately,
                                oauthClientId.trim()
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_add_connector_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentCyan,
                        contentColor = onAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(if (isEditing) R.string.action_save else R.string.action_add),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun parseCustomHeaders(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return buildMap {
        raw.lines().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) {
                    put(k, v)
                }
            }
        }
    }
}
