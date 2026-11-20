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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeskState
import com.example.model.IndstocksBrokerState
import com.example.model.McpAuthType
import com.example.model.McpConnectionStatus
import com.example.model.McpConnectorConfig
import com.example.model.McpConnectorsUiState
import com.example.model.McpTransportType
import com.example.model.TradingStyle
import com.example.model.TradingWorkflow
import com.example.model.LlmProviderConfig
import com.example.ui.components.AddCustomConnectorDialog
import com.example.ui.components.IndstocksBrokerSettingsCard
import com.example.ui.components.McpConnectorInspectorDialog
import com.example.ui.components.McpOAuthAuthorizeDialog
import com.example.ui.components.SettingsLlmAndThemeSection
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.LocalChartPalette
import com.example.ui.viewmodel.ChartWorkstationUiState
import com.example.ui.viewmodel.SettingsPage

@Composable
fun SettingsScreen(
    settingsPage: SettingsPage,
    deskState: DeskState,
    connectorsState: McpConnectorsUiState,
    onNavigatePage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onSelectTradingStyle: (TradingStyle) -> Unit,
    onSelectWorkflow: (TradingWorkflow) -> Unit,
    onRefreshLive: () -> Unit,
    validateConnectorUrl: (String) -> String?,
    onAddCustomConnector: (
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String>,
        connectImmediately: Boolean
    ) -> Unit,
    onUpdateCustomConnector: (
        connectorId: String,
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String>,
        connectImmediately: Boolean
    ) -> Unit,
    onDeleteCustomConnector: (String) -> Unit,
    onToggleConnectorEnabled: (String) -> Unit,
    onConnectConnector: (String) -> Unit,
    onExecuteConnectorTool: (connectorId: String, toolName: String, argumentsJson: String) -> Unit,
    onAddCustomConnectorWithClientId: ((
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String>,
        connectImmediately: Boolean,
        oauthClientId: String
    ) -> Unit)? = null,
    onUpdateCustomConnectorWithClientId: ((
        connectorId: String,
        name: String,
        url: String,
        transportType: McpTransportType,
        authType: McpAuthType,
        authHeaderName: String,
        authSecret: String,
        customHeaders: Map<String, String>,
        connectImmediately: Boolean,
        oauthClientId: String
    ) -> Unit)? = null,
    onDiscoverOAuth: (connectorId: String) -> Unit = {},
    onStartOAuthBrowser: (connectorId: String, explicitClientId: String, useCustomSchemeRedirect: Boolean) -> Unit = { _, _, _ -> },
    onSubmitOAuthManualInput: (String) -> Unit = {},
    onApplyManualBearerToken: (connectorId: String, token: String) -> Unit = { _, _ -> },
    onDismissOAuth: () -> Unit = {},
    indstocksState: IndstocksBrokerState = IndstocksBrokerState(),
    onConnectIndstocksToken: (token: String, clientId: String?) -> Unit = { _, _ -> },
    onGenerateIndstocksTotp: (clientId: String, mpin: String, totp: String) -> Unit = { _, _, _ -> },
    onSyncIndstocksPortfolio: () -> Unit = {},
    onDisconnectIndstocks: () -> Unit = {},
    chartState: ChartWorkstationUiState = ChartWorkstationUiState(),
    onSelectChartTheme: (ChartThemeMode) -> Unit = {},
    onSelectLlmProvider: (String) -> Unit = {},
    onSaveLlmConfig: (LlmProviderConfig) -> Unit = {},
    onTestLlmConnection: (LlmProviderConfig) -> Unit = {}
) {
    var showAddConnectorDialog by remember { mutableStateOf(false) }
    var editingConnector by remember { mutableStateOf<McpConnectorConfig?>(null) }
    var inspectedConnectorId by remember { mutableStateOf<String?>(null) }
    var oauthTargetConnectorId by remember { mutableStateOf<String?>(null) }
    val palette = LocalChartPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("settings_screen")
    ) {
        // Top Settings Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .border(1.dp, palette.border)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.textPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (settingsPage) {
                        SettingsPage.CONNECTORS -> "Settings → Connectors"
                        SettingsPage.LLM_AND_THEME -> "Settings → AI LLMs & Theme"
                        SettingsPage.ROOT -> "Settings"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = when (settingsPage) {
                        SettingsPage.CONNECTORS -> "Model Context Protocol (MCP) plugins & integrations"
                        SettingsPage.LLM_AND_THEME -> "Multi-LLM AI analyst endpoints & Dark/Light workstation theme"
                        SettingsPage.ROOT -> "Terminal preferences, broker & MCP connectors"
                    },
                    fontSize = 10.5.sp,
                    color = palette.textSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            when (settingsPage) {
                SettingsPage.ROOT -> SettingsRootContent(
                    deskState = deskState,
                    connectors = connectorsState.connectors,
                    indstocksState = indstocksState,
                    chartState = chartState,
                    onOpenConnectors = { onNavigatePage(SettingsPage.CONNECTORS) },
                    onOpenLlmAndTheme = { onNavigatePage(SettingsPage.LLM_AND_THEME) },
                    onSelectTradingStyle = onSelectTradingStyle,
                    onSelectWorkflow = onSelectWorkflow,
                    onRefreshLive = onRefreshLive,
                    onConnectIndstocksToken = onConnectIndstocksToken,
                    onGenerateIndstocksTotp = onGenerateIndstocksTotp,
                    onSyncIndstocksPortfolio = onSyncIndstocksPortfolio,
                    onDisconnectIndstocks = onDisconnectIndstocks
                )
                SettingsPage.LLM_AND_THEME -> SettingsLlmAndThemeSection(
                    themeMode = chartState.themeMode,
                    llmConfigs = chartState.llmConfigs,
                    isTestingLlm = chartState.isTestingLlm,
                    llmTestStatus = chartState.llmTestStatus,
                    onSelectTheme = onSelectChartTheme,
                    onSelectActiveProvider = onSelectLlmProvider,
                    onSaveConfig = onSaveLlmConfig,
                    onTestConnection = onTestLlmConnection
                )
                SettingsPage.CONNECTORS -> ConnectorsSettingsContent(
                    connectors = connectorsState.connectors,
                    onOpenAddCustomConnector = {
                        editingConnector = null
                        showAddConnectorDialog = true
                    },
                    onInspectConnector = { inspectedConnectorId = it.id },
                    onEditConnector = {
                        editingConnector = it
                        showAddConnectorDialog = true
                    },
                    onDeleteConnector = onDeleteCustomConnector,
                    onToggleConnectorEnabled = onToggleConnectorEnabled,
                    onConnectConnector = onConnectConnector,
                    onAuthorizeConnector = { oauthTargetConnectorId = it.id }
                )
            }
        }
    }

    if (showAddConnectorDialog) {
        AddCustomConnectorDialog(
            initialConnector = editingConnector,
            validateUrl = validateConnectorUrl,
            onConfirm = { name, url, transport, auth, headerName, secret, customHeaders, connectNow, clientId ->
                val targetEdit = editingConnector
                if (targetEdit != null) {
                    if (onUpdateCustomConnectorWithClientId != null) {
                        onUpdateCustomConnectorWithClientId(
                            targetEdit.id,
                            name,
                            url,
                            transport,
                            auth,
                            headerName,
                            secret,
                            customHeaders,
                            connectNow,
                            clientId
                        )
                    } else {
                        onUpdateCustomConnector(
                            targetEdit.id,
                            name,
                            url,
                            transport,
                            auth,
                            headerName,
                            secret,
                            customHeaders,
                            connectNow
                        )
                    }
                } else {
                    if (onAddCustomConnectorWithClientId != null) {
                        onAddCustomConnectorWithClientId(
                            name,
                            url,
                            transport,
                            auth,
                            headerName,
                            secret,
                            customHeaders,
                            connectNow,
                            clientId
                        )
                    } else {
                        onAddCustomConnector(
                            name,
                            url,
                            transport,
                            auth,
                            headerName,
                            secret,
                            customHeaders,
                            connectNow
                        )
                    }
                }
                showAddConnectorDialog = false
                editingConnector = null
            },
            onDismiss = {
                showAddConnectorDialog = false
                editingConnector = null
            }
        )
    }

    inspectedConnectorId?.let { targetId ->
        val latestConnector = connectorsState.connectors.firstOrNull { it.id == targetId }
        if (latestConnector != null) {
            McpConnectorInspectorDialog(
                connector = latestConnector,
                logs = connectorsState.rpcLogsByConnector[targetId].orEmpty(),
                lastResult = connectorsState.lastToolResultsByConnector[targetId],
                isExecutingTool = connectorsState.executingToolConnectorId == targetId,
                onReconnect = { onConnectConnector(targetId) },
                onExecuteTool = { toolName, args ->
                    onExecuteConnectorTool(targetId, toolName, args)
                },
                onDismiss = { inspectedConnectorId = null }
            )
        }
    }

    val activeOAuthConnectorId = oauthTargetConnectorId ?: connectorsState.activeOAuthFlow?.connectorId
    activeOAuthConnectorId?.let { targetId ->
        val oauthConnector = connectorsState.connectors.firstOrNull { it.id == targetId }
        if (oauthConnector != null && oauthConnector.status != McpConnectionStatus.CONNECTED) {
            McpOAuthAuthorizeDialog(
                connector = oauthConnector,
                activeFlow = connectorsState.activeOAuthFlow?.takeIf { it.connectorId == targetId },
                isBusy = connectorsState.oauthBusyConnectorId == targetId,
                statusMessage = connectorsState.oauthStatusMessage,
                onDiscoverMetadata = { onDiscoverOAuth(targetId) },
                onStartOAuthBrowser = { clientId, customScheme ->
                    onStartOAuthBrowser(targetId, clientId, customScheme)
                },
                onSubmitManualCodeOrCallbackUrl = onSubmitOAuthManualInput,
                onApplyManualBearerToken = { token -> onApplyManualBearerToken(targetId, token) },
                onDismiss = {
                    oauthTargetConnectorId = null
                    onDismissOAuth()
                }
            )
        } else if (oauthConnector?.status == McpConnectionStatus.CONNECTED) {
            oauthTargetConnectorId = null
        }
    }
}

@Composable
private fun SettingsRootContent(
    deskState: DeskState,
    connectors: List<McpConnectorConfig>,
    indstocksState: IndstocksBrokerState,
    chartState: ChartWorkstationUiState,
    onOpenConnectors: () -> Unit,
    onOpenLlmAndTheme: () -> Unit,
    onSelectTradingStyle: (TradingStyle) -> Unit,
    onSelectWorkflow: (TradingWorkflow) -> Unit,
    onRefreshLive: () -> Unit,
    onConnectIndstocksToken: (token: String, clientId: String?) -> Unit,
    onGenerateIndstocksTotp: (clientId: String, mpin: String, totp: String) -> Unit,
    onSyncIndstocksPortfolio: () -> Unit,
    onDisconnectIndstocks: () -> Unit
) {
    val palette = LocalChartPalette.current
    val customCount = connectors.count { !it.isBuiltIn }
    val connectedCount = connectors.count { it.status == McpConnectionStatus.CONNECTED }

    Text(
        text = "INTEGRATIONS & PLUGINS",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Primary Navigation Entry: Connectors (MCP Plugins)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, palette.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onOpenConnectors() }
            .testTag("settings_connectors_item"),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.accentCyanDim)
                    .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "Connectors",
                    tint = palette.accentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Connectors",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (connectedCount > 0) palette.bullGreenDim else palette.accentCyanDim)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MCP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (connectedCount > 0) palette.bullGreen else palette.accentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${connectors.count { it.isBuiltIn }} built-in • $customCount custom connectors • $connectedCount connected",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Connectors",
                tint = palette.accentCyan
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Navigation Entry: Chart Theme & Multi-LLM AI Providers
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, palette.goldAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onOpenLlmAndTheme() }
            .testTag("settings_llm_theme_card"),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.goldAccentDim)
                    .border(1.dp, palette.goldAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Chart Theme & AI LLMs",
                    tint = palette.goldAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chart Theme & AI LLMs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${chartState.themeMode.label} • ${chartState.activeLlmConfig.providerType.displayName}",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Chart Theme & AI LLMs",
                tint = palette.goldAccent
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    IndstocksBrokerSettingsCard(
        indstocksState = indstocksState,
        onConnectWithToken = onConnectIndstocksToken,
        onGenerateTokenViaTotp = onGenerateIndstocksTotp,
        onSyncPortfolio = onSyncIndstocksPortfolio,
        onDisconnect = onDisconnectIndstocks
    )

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "TRADING DESK PREFERENCES",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            SettingsActionHeader(
                icon = Icons.Default.Tune,
                title = "Execution Style",
                subtitle = "Active mode: ${deskState.selectedTradingStyle.title}"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TradingStyle.values().forEach { style ->
                    val selected = deskState.selectedTradingStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyanDim else palette.surfaceVariant)
                            .border(
                                1.dp,
                                if (selected) palette.accentCyan else palette.border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectTradingStyle(style) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = style.title,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.accentCyan else palette.textPrimary
                            )
                            Text(
                                text = style.description,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentCyan)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = palette.border)
            Spacer(modifier = Modifier.height(14.dp))

            SettingsActionHeader(
                icon = Icons.Default.Schedule,
                title = "Analysis Workflow",
                subtitle = "Current session workflow: ${deskState.selectedWorkflow.title}"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TradingWorkflow.values().forEach { wf ->
                    val selected = deskState.selectedWorkflow == wf
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyanDim else palette.surfaceVariant)
                            .border(
                                1.dp,
                                if (selected) palette.accentCyan else palette.border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectWorkflow(wf) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wf.title,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.accentCyan else palette.textPrimary
                            )
                            Text(
                                text = wf.hours,
                                fontSize = 10.sp,
                                color = palette.textSecondary
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentCyan)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = palette.border)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    SettingsActionHeader(
                        icon = Icons.Default.Settings,
                        title = "Market Data Feed",
                        subtitle = "${deskState.dataSourceLabel} • ${deskState.lastSyncLabel}"
                    )
                }
                OutlinedButton(
                    onClick = onRefreshLive,
                    enabled = !deskState.isRefreshing,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Now",
                        tint = palette.accentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync", color = palette.accentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsActionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val palette = LocalChartPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.goldAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun ConnectorsSettingsContent(
    connectors: List<McpConnectorConfig>,
    onOpenAddCustomConnector: () -> Unit,
    onInspectConnector: (McpConnectorConfig) -> Unit,
    onEditConnector: (McpConnectorConfig) -> Unit,
    onDeleteConnector: (String) -> Unit,
    onToggleConnectorEnabled: (String) -> Unit,
    onConnectConnector: (String) -> Unit,
    onAuthorizeConnector: (McpConnectorConfig) -> Unit
) {
    val palette = LocalChartPalette.current
    val customConnectors = connectors.filter { !it.isBuiltIn }
    val builtInConnectors = connectors.filter { it.isBuiltIn }

    Column(modifier = Modifier.testTag("connectors_screen")) {
        // Overview Banner + Primary "Add custom connector" Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MCP Connectors",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Allow Nithix NIFTY Desk to connect to external Model Context Protocol (MCP) servers over Streamable HTTP or SSE to discover tools, inspect schemas, and run live RPC calls.",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAddCustomConnector,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("add_custom_connector_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add custom connector",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Connectors Section
        Text(
            text = "CUSTOM CONNECTORS (${customConnectors.size})",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (customConnectors.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No custom connectors added yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap \"Add custom connector\" above to register a remote MCP server URL.",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                customConnectors.forEach { connector ->
                    ConnectorCard(
                        connector = connector,
                        onInspect = { onInspectConnector(connector) },
                        onEdit = { onEditConnector(connector) },
                        onDelete = { onDeleteConnector(connector.id) },
                        onToggleEnabled = { onToggleConnectorEnabled(connector.id) },
                        onConnect = { onConnectConnector(connector.id) },
                        onAuthorize = { onAuthorizeConnector(connector) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Built-in NSE Connectors Section
        Text(
            text = "BUILT-IN NSE CONNECTORS (${builtInConnectors.size})",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            builtInConnectors.forEach { connector ->
                ConnectorCard(
                    connector = connector,
                    onInspect = { onInspectConnector(connector) },
                    onEdit = {},
                    onDelete = {},
                    onToggleEnabled = { onToggleConnectorEnabled(connector.id) },
                    onConnect = { onConnectConnector(connector.id) },
                    onAuthorize = { onAuthorizeConnector(connector) }
                )
            }
        }
    }
}

@Composable
private fun ConnectorCard(
    connector: McpConnectorConfig,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onConnect: () -> Unit,
    onAuthorize: () -> Unit
) {
    val palette = LocalChartPalette.current
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (connector.status == McpConnectionStatus.CONNECTED) palette.bullGreen.copy(alpha = 0.45f) else palette.border,
                RoundedCornerShape(12.dp)
            )
            .testTag("connector_card_${connector.id}"),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = connector.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusBg)
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = connector.status.label.uppercase(),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = connector.url,
                        fontSize = 10.sp,
                        color = palette.accentCyan,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = connector.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = palette.accentCyan,
                        uncheckedThumbColor = palette.textMuted,
                        uncheckedTrackColor = palette.surfaceVariant
                    ),
                    modifier = Modifier.testTag("connector_toggle_${connector.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capabilities & Error Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surfaceHighlight)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${connector.tools.size} tools",
                        fontSize = 9.5.sp,
                        color = palette.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surfaceHighlight)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = connector.authType.label,
                        fontSize = 9.5.sp,
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (connector.lastLatencyMs > 0L) {
                    Text(
                        text = "${connector.lastLatencyMs}ms",
                        fontSize = 9.5.sp,
                        color = palette.bullGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            connector.lastError?.let { err ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = err,
                    fontSize = 10.sp,
                    color = palette.bearRed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onConnect,
                        enabled = connector.status != McpConnectionStatus.CONNECTING && connector.isEnabled,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("connector_connect_${connector.id}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (connector.status == McpConnectionStatus.CONNECTING) {
                            CircularProgressIndicator(
                                color = palette.accentCyan,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Connect",
                                tint = palette.accentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (connector.status == McpConnectionStatus.CONNECTED) "Reconnect" else "Connect",
                            fontSize = 11.sp,
                            color = palette.accentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (connector.status == McpConnectionStatus.AUTH_REQUIRED ||
                        connector.authType == McpAuthType.OAUTH_PKCE
                    ) {
                        Button(
                            onClick = onAuthorize,
                            enabled = connector.isEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.goldAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("connector_authorize_${connector.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sign In",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onInspect,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("connector_inspect_${connector.id}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Inspect tools",
                            tint = palette.goldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tools",
                            fontSize = 11.sp,
                            color = palette.goldAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!connector.isBuiltIn) {
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("connector_edit_${connector.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit custom connector",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("connector_delete_${connector.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete custom connector",
                                tint = palette.bearRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
