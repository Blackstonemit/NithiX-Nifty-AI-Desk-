package com.example.model

enum class McpTransportType(val label: String) {
    STREAMABLE_HTTP("Streamable HTTP"),
    SSE("SSE (HTTP + Events)")
}

enum class McpAuthType(val label: String) {
    NONE("None"),
    OAUTH_PKCE("OAuth 2.1"),
    BEARER_TOKEN("Bearer Token"),
    API_KEY_HEADER("API Key Header")
}

enum class McpConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    AUTH_REQUIRED("Auth Required"),
    CONNECTED("Connected"),
    ERROR("Error")
}

data class McpOAuthMetadata(
    val resourceUrl: String,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val registrationEndpoint: String? = null,
    val issuer: String? = null,
    val scopesSupported: List<String> = emptyList(),
    val discoveredClientId: String? = null
)

data class McpDiscoveredTool(
    val name: String,
    val description: String,
    val inputSchemaJson: String = "{}",
    val requiredParams: List<String> = emptyList(),
    val parameterProperties: Map<String, String> = emptyMap()
)

data class McpDiscoveredResource(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val description: String? = null
)

data class McpDiscoveredPrompt(
    val name: String,
    val description: String? = null
)

data class McpToolExecutionResult(
    val connectorId: String,
    val toolName: String,
    val isSuccess: Boolean,
    val outputText: String,
    val rawJson: String,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class McpConnectorConfig(
    val id: String,
    val name: String,
    val url: String,
    val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val authType: McpAuthType = McpAuthType.NONE,
    val authHeaderName: String = "Authorization",
    val authSecret: String = "",
    val oauthClientId: String = "",
    val oauthRefreshToken: String = "",
    val oauthMetadata: McpOAuthMetadata? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true,
    val status: McpConnectionStatus = McpConnectionStatus.DISCONNECTED,
    val serverInfoName: String? = null,
    val serverInfoVersion: String? = null,
    val protocolVersion: String = "2024-11-05",
    val sessionId: String? = null,
    val lastLatencyMs: Long = 0L,
    val lastError: String? = null,
    val lastConnectedTimestamp: Long? = null,
    val tools: List<McpDiscoveredTool> = emptyList(),
    val resources: List<McpDiscoveredResource> = emptyList(),
    val prompts: List<McpDiscoveredPrompt> = emptyList()
)

data class McpPendingOAuthFlow(
    val connectorId: String,
    val connectorName: String,
    val serverUrl: String,
    val authorizationUrl: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val codeVerifier: String,
    val state: String,
    val resourceUrl: String
)

data class McpConnectorsUiState(
    val connectors: List<McpConnectorConfig> = emptyList(),
    val rpcLogsByConnector: Map<String, List<McpRpcLog>> = emptyMap(),
    val lastToolResultsByConnector: Map<String, McpToolExecutionResult> = emptyMap(),
    val executingToolConnectorId: String? = null,
    val activeOAuthFlow: McpPendingOAuthFlow? = null,
    val oauthBusyConnectorId: String? = null,
    val oauthStatusMessage: String? = null
)
