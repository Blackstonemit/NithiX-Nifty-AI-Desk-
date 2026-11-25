package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.McpConnectionStatus
import com.example.model.McpConnectorConfig
import com.example.model.McpDiscoveredTool
import com.example.model.McpRpcLog
import com.example.model.McpToolExecutionResult
import com.example.ui.theme.LocalChartPalette
import org.json.JSONObject

@Composable
fun McpConnectorInspectorDialog(
    connector: McpConnectorConfig,
    logs: List<McpRpcLog>,
    lastResult: McpToolExecutionResult?,
    isExecutingTool: Boolean,
    onReconnect: () -> Unit,
    onExecuteTool: (toolName: String, argumentsJson: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Tools, 1: Resources/Prompts, 2: RPC Logs
    var selectedTool by remember(connector.id, connector.tools) {
        mutableStateOf(connector.tools.firstOrNull())
    }

    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(1.5.dp, palette.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("connector_inspector_dialog")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = connector.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
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

                IconButton(
                    onClick = onReconnect,
                    enabled = connector.status != McpConnectionStatus.CONNECTING,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("inspector_reconnect_button")
                ) {
                    if (connector.status == McpConnectionStatus.CONNECTING) {
                        CircularProgressIndicator(
                            color = palette.accentCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_reconnect_discover),
                            tint = palette.accentCyan
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_inspector_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_inspector),
                        tint = palette.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Connection Metadata Banner
            val statusColor = when (connector.status) {
                McpConnectionStatus.CONNECTED -> palette.bullGreen
                McpConnectionStatus.CONNECTING -> palette.accentCyan
                McpConnectionStatus.AUTH_REQUIRED -> palette.goldAccent
                McpConnectionStatus.ERROR -> palette.bearRed
                McpConnectionStatus.DISCONNECTED -> palette.goldAccent
            }
            val statusBg = when (connector.status) {
                McpConnectionStatus.CONNECTED -> palette.bullGreenDim
                McpConnectionStatus.CONNECTING -> palette.accentCyanDim
                McpConnectionStatus.AUTH_REQUIRED -> palette.goldAccentDim
                McpConnectionStatus.ERROR -> palette.bearRedDim
                McpConnectionStatus.DISCONNECTED -> palette.goldAccentDim
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusBg)
                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.mcp_status_transport_format,
                                connector.status.label.uppercase(),
                                connector.transportType.label
                            ),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontFamily = FontFamily.Monospace
                        )
                        if (connector.lastLatencyMs > 0L) {
                            Text(
                                text = stringResource(R.string.latency_ms_format, connector.lastLatencyMs),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    val infoLine = buildString {
                        append(connector.serverInfoName ?: connector.name)
                        if (!connector.serverInfoVersion.isNullOrBlank()) {
                            append(" v${connector.serverInfoVersion}")
                        }
                        append(" • MCP ${connector.protocolVersion}")
                    }
                    Text(
                        text = connector.lastError ?: infoLine,
                        fontSize = 10.sp,
                        color = if (connector.lastError != null) palette.bearRed else palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs
            val tabs = listOf(
                stringResource(R.string.mcp_tab_tools_format, connector.tools.size),
                stringResource(R.string.mcp_tab_schema_format, connector.resources.size + connector.prompts.size),
                stringResource(R.string.mcp_tab_rpc_logs_format, logs.size)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) palette.accentCyan else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) onAccent else palette.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> ToolsPlaygroundSection(
                        tools = connector.tools,
                        selectedTool = selectedTool,
                        onSelectTool = { selectedTool = it },
                        lastResult = lastResult,
                        isExecuting = isExecutingTool,
                        onExecuteTool = onExecuteTool
                    )
                    1 -> ResourcesAndPromptsSection(connector)
                    2 -> ConnectorRpcLogsSection(logs)
                }
            }
        }
    }
}

@Composable
private fun ToolsPlaygroundSection(
    tools: List<McpDiscoveredTool>,
    selectedTool: McpDiscoveredTool?,
    onSelectTool: (McpDiscoveredTool) -> Unit,
    lastResult: McpToolExecutionResult?,
    isExecuting: Boolean,
    onExecuteTool: (toolName: String, argumentsJson: String) -> Unit
) {
    val palette = LocalChartPalette.current
    if (tools.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.mcp_no_tools_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mcp_no_tools_hint),
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        }
        return
    }

    Text(
        text = stringResource(R.string.mcp_discovered_tools_header),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(6.dp))

    tools.forEach { tool ->
        val isSelected = selectedTool?.name == tool.name
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(vertical = 4.dp)
                .border(
                    1.dp,
                    if (isSelected) palette.accentCyan else palette.border,
                    RoundedCornerShape(10.dp)
                )
                .clickable { onSelectTool(tool) }
                .testTag("discovered_tool_${tool.name}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) palette.surfaceHighlight else palette.surface
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = if (isSelected) palette.accentCyan else palette.goldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) palette.accentCyan else palette.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = tool.description,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    selectedTool?.let { tool ->
        Spacer(modifier = Modifier.height(10.dp))
        ToolExecutionCard(
            tool = tool,
            isExecuting = isExecuting,
            lastResult = lastResult?.takeIf { it.toolName == tool.name },
            onExecute = { argsJson -> onExecuteTool(tool.name, argsJson) }
        )
    }
}

@Composable
private fun ToolExecutionCard(
    tool: McpDiscoveredTool,
    isExecuting: Boolean,
    lastResult: McpToolExecutionResult?,
    onExecute: (String) -> Unit
) {
    val palette = LocalChartPalette.current
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val paramValues = remember(tool.name) { mutableStateMapOf<String, String>() }
    var useRawJson by remember(tool.name) { mutableStateOf(tool.parameterProperties.isEmpty()) }
    var rawJsonArgs by remember(tool.name) { mutableStateOf("{}") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentCyan,
        unfocusedBorderColor = palette.border,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        cursorColor = palette.accentCyan,
        focusedLabelColor = palette.accentCyan,
        unfocusedLabelColor = palette.textSecondary
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mcp_run_tool_format, tool.name),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.goldAccent,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                if (tool.parameterProperties.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { useRawJson = !useRawJson }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                if (useRawJson) R.string.mcp_mode_form else R.string.mcp_mode_raw_json
                            ),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!useRawJson && tool.parameterProperties.isNotEmpty()) {
                tool.parameterProperties.forEach { (paramName, paramHint) ->
                    val isReq = tool.requiredParams.contains(paramName)
                    OutlinedTextField(
                        value = paramValues[paramName] ?: "",
                        onValueChange = { paramValues[paramName] = it },
                        label = {
                            Text(
                                if (isReq) stringResource(R.string.mcp_required_param_format, paramName) else paramName
                            )
                        },
                        placeholder = { Text(paramHint, color = palette.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("tool_param_$paramName")
                    )
                }
            } else {
                OutlinedTextField(
                    value = rawJsonArgs,
                    onValueChange = { rawJsonArgs = it },
                    label = { Text(stringResource(R.string.mcp_args_json_label)) },
                    minLines = 2,
                    maxLines = 5,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_raw_json_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val jsonPayload = if (useRawJson) {
                        rawJsonArgs
                    } else {
                        val obj = JSONObject()
                        paramValues.forEach { (k, v) ->
                            if (v.isNotBlank()) obj.put(k, v.trim())
                        }
                        obj.toString()
                    }
                    onExecute(jsonPayload)
                },
                enabled = !isExecuting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentCyan,
                    contentColor = onAccent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("execute_mcp_tool_button")
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        color = onAccent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mcp_executing_tool), fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.mcp_execute_tool), fontWeight = FontWeight.Bold)
                }
            }

            lastResult?.let { result ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.canvasBackground)
                        .border(
                            1.dp,
                            if (result.isSuccess) palette.bullGreen.copy(alpha = 0.5f) else palette.bearRed.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                        .testTag("mcp_tool_output_box")
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    if (result.isSuccess) R.string.mcp_result_latency_format else R.string.mcp_error_latency_format,
                                    result.latencyMs
                                ),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (result.isSuccess) palette.bullGreen else palette.bearRed,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.outputText,
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesAndPromptsSection(connector: McpConnectorConfig) {
    val palette = LocalChartPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.mcp_resources_header_format, connector.resources.size),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        if (connector.resources.isEmpty()) {
            Text(
                text = stringResource(R.string.mcp_no_resources),
                fontSize = 11.sp,
                color = palette.textMuted
            )
        } else {
            connector.resources.forEach { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(res.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(res.uri, fontSize = 10.sp, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
                        res.description?.let { Text(it, fontSize = 11.sp, color = palette.textSecondary) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.mcp_prompts_header_format, connector.prompts.size),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        if (connector.prompts.isEmpty()) {
            Text(
                text = stringResource(R.string.mcp_no_prompts),
                fontSize = 11.sp,
                color = palette.textMuted
            )
        } else {
            connector.prompts.forEach { prompt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(prompt.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
                        prompt.description?.let { Text(it, fontSize = 11.sp, color = palette.textSecondary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectorRpcLogsSection(logs: List<McpRpcLog>) {
    val palette = LocalChartPalette.current
    if (logs.isEmpty()) {
        Text(
            text = stringResource(R.string.mcp_no_rpc_logs),
            fontSize = 11.sp,
            color = palette.textMuted
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        logs.take(16).forEach { log ->
            val isReq = log.direction == "OUTGOING_REQUEST"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                shape = RoundedCornerShape(8.dp)
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
                            color = when {
                                isReq -> palette.goldAccent
                                log.isSuccess -> palette.bullGreen
                                else -> palette.bearRed
                            },
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
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
                            .clip(RoundedCornerShape(6.dp))
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
