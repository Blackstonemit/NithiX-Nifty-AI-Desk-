package com.example.network

import com.example.model.McpAuthType
import com.example.model.McpConnectorConfig
import com.example.model.McpDiscoveredPrompt
import com.example.model.McpDiscoveredResource
import com.example.model.McpDiscoveredTool
import com.example.model.McpOAuthMetadata
import com.example.model.McpRpcLog
import com.example.model.McpToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class McpHandshakeResult(
    val isSuccess: Boolean,
    val authRequired: Boolean = false,
    val oauthMetadata: McpOAuthMetadata? = null,
    val refreshedAccessToken: String? = null,
    val refreshedRefreshToken: String? = null,
    val serverInfoName: String? = null,
    val serverInfoVersion: String? = null,
    val protocolVersion: String = "2024-11-05",
    val sessionId: String? = null,
    val tools: List<McpDiscoveredTool> = emptyList(),
    val resources: List<McpDiscoveredResource> = emptyList(),
    val prompts: List<McpDiscoveredPrompt> = emptyList(),
    val latencyMs: Long = 0L,
    val errorMessage: String? = null,
    val logs: List<McpRpcLog> = emptyList()
)

data class McpToolCallOutcome(
    val executionResult: McpToolExecutionResult,
    val logs: List<McpRpcLog>
)

/**
 * Generic Model Context Protocol (MCP) JSON-RPC 2.0 client plugin supporting
 * Streamable HTTP and SSE endpoints, optional Bearer / API Key authentication,
 * `initialize`, `notifications/initialized`, `tools/list`, `resources/list`,
 * `prompts/list`, and live `tools/call` invocation.
 */
class McpPluginClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val oauthClient: McpOAuthClient = McpOAuthClient(client)
) {

    fun validateUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return "Server URL is required"
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return "Enter a valid http:// or https:// MCP endpoint URL"
        if (parsed.scheme != "https" && parsed.scheme != "http") {
            return "Only http:// or https:// MCP URLs are supported"
        }
        if (parsed.host.isBlank()) {
            return "Server URL must include a valid host"
        }
        if (parsed.scheme == "http") {
            val h = parsed.host.lowercase()
            val isLoopback = h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "10.0.2.2" || h.endsWith(".local")
            if (!isLoopback) {
                return "Remote MCP connectors require https:// (cleartext http:// is allowed only for local loopback hosts)"
            }
        }
        return null
    }

    suspend fun connectAndDiscover(connector: McpConnectorConfig): McpHandshakeResult = withContext(Dispatchers.IO) {
        val urlError = validateUrl(connector.url)
        if (urlError != null) {
            return@withContext McpHandshakeResult(
                isSuccess = false,
                errorMessage = urlError
            )
        }

        val logs = mutableListOf<McpRpcLog>()
        val startMs = System.currentTimeMillis()
        var activeConnector = connector
        var newAccessToken: String? = null
        var newRefreshToken: String? = null

        // 1. JSON-RPC initialize handshake
        val initParams = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())
                put("resources", JSONObject())
                put("prompts", JSONObject())
            })
            put("clientInfo", JSONObject().apply {
                put("name", "Nithix NIFTY Desk")
                put("version", "1.0.0")
            })
        }

        var initResponse = executeJsonRpc(
            connector = activeConnector,
            sessionId = activeConnector.sessionId,
            method = "initialize",
            params = initParams,
            logs = logs
        )

        // If 401 and we have an OAuth refresh token, attempt silent refresh once before prompting sign-in
        if (!initResponse.isSuccess && initResponse.httpCode == 401 &&
            activeConnector.oauthRefreshToken.isNotBlank() &&
            activeConnector.oauthMetadata?.tokenEndpoint != null
        ) {
            val tokenEndpoint = activeConnector.oauthMetadata!!.tokenEndpoint!!
            val clientId = activeConnector.oauthClientId.ifBlank {
                activeConnector.oauthMetadata?.discoveredClientId.orEmpty()
            }
            val refreshed = oauthClient.refreshAccessToken(
                tokenEndpoint = tokenEndpoint,
                refreshToken = activeConnector.oauthRefreshToken,
                clientId = clientId,
                resourceUrl = activeConnector.oauthMetadata?.resourceUrl ?: activeConnector.url
            )
            if (refreshed.isSuccess && !refreshed.accessToken.isNullOrBlank()) {
                newAccessToken = refreshed.accessToken
                newRefreshToken = refreshed.refreshToken ?: activeConnector.oauthRefreshToken
                activeConnector = activeConnector.copy(
                    authType = McpAuthType.OAUTH_PKCE,
                    authSecret = refreshed.accessToken,
                    oauthRefreshToken = newRefreshToken
                )
                initResponse = executeJsonRpc(
                    connector = activeConnector,
                    sessionId = activeConnector.sessionId,
                    method = "initialize",
                    params = initParams,
                    logs = logs
                )
            }
        }

        if (!initResponse.isSuccess) {
            val isAuthChallenge = initResponse.httpCode == 401 ||
                (initResponse.httpCode == 403 && !initResponse.wwwAuthenticateHeader.isNullOrBlank())
            val discoveredMeta = if (isAuthChallenge) {
                oauthClient.discoverOAuthMetadata(activeConnector.url, initResponse.wwwAuthenticateHeader)
                    ?: activeConnector.oauthMetadata
            } else {
                activeConnector.oauthMetadata
            }
            val msg = if (isAuthChallenge) {
                "Sign-in required (HTTP ${initResponse.httpCode}) — tap Sign In to authorize via OAuth 2.1 or Bearer token."
            } else {
                initResponse.errorMessage ?: "MCP initialize handshake failed"
            }
            return@withContext McpHandshakeResult(
                isSuccess = false,
                authRequired = isAuthChallenge,
                oauthMetadata = discoveredMeta,
                latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                errorMessage = msg,
                logs = logs
            )
        }

        val activeSessionId = initResponse.sessionId ?: activeConnector.sessionId
        val initResultObj = initResponse.resultObject
        val protocolVersion = initResultObj?.optString("protocolVersion")?.takeIf { it.isNotBlank() }
            ?: "2024-11-05"
        val serverInfoObj = initResultObj?.optJSONObject("serverInfo")
        val serverName = serverInfoObj?.optString("name")?.takeIf { it.isNotBlank() }
            ?: connector.name
        val serverVersion = serverInfoObj?.optString("version")?.takeIf { it.isNotBlank() }

        // 2. Best-effort notifications/initialized (per MCP specification)
        sendNotification(
            connector = activeConnector,
            sessionId = activeSessionId,
            method = "notifications/initialized"
        )

        // 3. Discover Tools (tools/list)
        val toolsResponse = executeJsonRpc(
            connector = activeConnector,
            sessionId = activeSessionId,
            method = "tools/list",
            params = JSONObject(),
            logs = logs
        )
        val discoveredTools = if (toolsResponse.isSuccess) {
            parseToolsList(toolsResponse.resultObject)
        } else {
            emptyList()
        }

        // 4. Discover Resources (best-effort)
        val resourcesResponse = executeJsonRpc(
            connector = activeConnector,
            sessionId = activeSessionId,
            method = "resources/list",
            params = JSONObject(),
            logs = logs
        )
        val discoveredResources = if (resourcesResponse.isSuccess) {
            parseResourcesList(resourcesResponse.resultObject)
        } else {
            emptyList()
        }

        // 5. Discover Prompts (best-effort)
        val promptsResponse = executeJsonRpc(
            connector = activeConnector,
            sessionId = activeSessionId,
            method = "prompts/list",
            params = JSONObject(),
            logs = logs
        )
        val discoveredPrompts = if (promptsResponse.isSuccess) {
            parsePromptsList(promptsResponse.resultObject)
        } else {
            emptyList()
        }

        val totalLatency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        McpHandshakeResult(
            isSuccess = true,
            oauthMetadata = activeConnector.oauthMetadata,
            refreshedAccessToken = newAccessToken,
            refreshedRefreshToken = newRefreshToken,
            serverInfoName = serverName,
            serverInfoVersion = serverVersion,
            protocolVersion = protocolVersion,
            sessionId = activeSessionId,
            tools = discoveredTools,
            resources = discoveredResources,
            prompts = discoveredPrompts,
            latencyMs = totalLatency,
            errorMessage = null,
            logs = logs
        )
    }

    suspend fun callTool(
        connector: McpConnectorConfig,
        toolName: String,
        argumentsJson: String
    ): McpToolCallOutcome = withContext(Dispatchers.IO) {
        val logs = mutableListOf<McpRpcLog>()
        val startMs = System.currentTimeMillis()

        val argsObj = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            val errResult = McpToolExecutionResult(
                connectorId = connector.id,
                toolName = toolName,
                isSuccess = false,
                outputText = "Invalid JSON arguments: ${e.message}",
                rawJson = "{\"error\": \"Invalid JSON arguments\"}",
                latencyMs = 1L
            )
            return@withContext McpToolCallOutcome(errResult, logs)
        }

        val params = JSONObject().apply {
            put("name", toolName)
            put("arguments", argsObj)
        }

        val rpcResponse = executeJsonRpc(
            connector = connector,
            sessionId = connector.sessionId,
            method = "tools/call",
            params = params,
            logs = logs
        )
        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        if (!rpcResponse.isSuccess) {
            return@withContext McpToolCallOutcome(
                executionResult = McpToolExecutionResult(
                    connectorId = connector.id,
                    toolName = toolName,
                    isSuccess = false,
                    outputText = rpcResponse.errorMessage ?: "Tool execution failed",
                    rawJson = rpcResponse.rawPayload,
                    latencyMs = latency
                ),
                logs = logs
            )
        }

        val resultObj = rpcResponse.resultObject ?: JSONObject()
        val isToolError = resultObj.optBoolean("isError", false)
        val formattedText = extractToolOutputText(resultObj, rpcResponse.rawPayload)

        McpToolCallOutcome(
            executionResult = McpToolExecutionResult(
                connectorId = connector.id,
                toolName = toolName,
                isSuccess = !isToolError,
                outputText = formattedText,
                rawJson = rpcResponse.rawPayload,
                latencyMs = latency
            ),
            logs = logs
        )
    }

    private data class RpcCallResult(
        val isSuccess: Boolean,
        val httpCode: Int = 0,
        val wwwAuthenticateHeader: String? = null,
        val resultObject: JSONObject? = null,
        val sessionId: String? = null,
        val rawPayload: String = "",
        val errorMessage: String? = null
    )

    private fun executeJsonRpc(
        connector: McpConnectorConfig,
        sessionId: String?,
        method: String,
        params: JSONObject,
        logs: MutableList<McpRpcLog>
    ): RpcCallResult {
        val requestId = UUID.randomUUID().toString().take(8)
        val startMs = System.currentTimeMillis()
        val requestJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            put("params", params)
        }.toString(2)

        logs.add(
            McpRpcLog(
                id = "REQ-$requestId",
                timestamp = startMs,
                direction = "OUTGOING_REQUEST",
                method = method,
                payloadJson = requestJson,
                isSuccess = true
            )
        )

        return try {
            val builder = Request.Builder()
                .url(connector.url.trim())
                .addHeader("User-Agent", "NithixNiftyDesk-MCP-Client/1.0 (Android)")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader("Content-Type", "application/json")

            if (!sessionId.isNullOrBlank()) {
                builder.addHeader("Mcp-Session-Id", sessionId)
            }

            applyAuthAndCustomHeaders(builder, connector)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = builder.post(requestJson.toRequestBody(mediaType)).build()

            client.newCall(request).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val bodyStr = response.body?.string().orEmpty()
                val respSessionId = response.header("Mcp-Session-Id") ?: sessionId
                val wwwAuth = response.header("WWW-Authenticate")

                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code} ${response.message}".trim()
                    logs.add(
                        McpRpcLog(
                            id = "ERR-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "$method (HTTP ${response.code}, ${elapsed}ms)",
                            payloadJson = bodyStr.ifBlank { "{\"error\": \"$msg\"}" },
                            isSuccess = false
                        )
                    )
                    return RpcCallResult(
                        isSuccess = false,
                        httpCode = response.code,
                        wwwAuthenticateHeader = wwwAuth,
                        sessionId = respSessionId,
                        rawPayload = bodyStr,
                        errorMessage = msg
                    )
                }

                if (bodyStr.isBlank()) {
                    logs.add(
                        McpRpcLog(
                            id = "ERR-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "$method (Empty Response, ${elapsed}ms)",
                            payloadJson = "{\"error\": \"Empty HTTP body\"}",
                            isSuccess = false
                        )
                    )
                    return RpcCallResult(
                        isSuccess = false,
                        sessionId = respSessionId,
                        errorMessage = "Server returned empty response body"
                    )
                }

                val jsonEnvelope = unwrapJsonRpcEnvelope(bodyStr, requestId)
                if (jsonEnvelope == null) {
                    logs.add(
                        McpRpcLog(
                            id = "ERR-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "$method (Invalid JSON-RPC, ${elapsed}ms)",
                            payloadJson = bodyStr,
                            isSuccess = false
                        )
                    )
                    return RpcCallResult(
                        isSuccess = false,
                        sessionId = respSessionId,
                        rawPayload = bodyStr,
                        errorMessage = "Response is not a valid JSON-RPC 2.0 / SSE envelope"
                    )
                }

                val errorObj = jsonEnvelope.optJSONObject("error")
                if (errorObj != null) {
                    val code = errorObj.optInt("code", -32000)
                    val errMsg = errorObj.optString("message", "JSON-RPC Error $code")
                    logs.add(
                        McpRpcLog(
                            id = "ERR-$requestId",
                            timestamp = System.currentTimeMillis(),
                            direction = "INCOMING_RESPONSE",
                            method = "$method:error ($code, ${elapsed}ms)",
                            payloadJson = jsonEnvelope.toString(2),
                            isSuccess = false
                        )
                    )
                    return RpcCallResult(
                        isSuccess = false,
                        sessionId = respSessionId,
                        rawPayload = jsonEnvelope.toString(2),
                        errorMessage = "MCP Error ($code): $errMsg"
                    )
                }

                val resultObj = jsonEnvelope.optJSONObject("result")
                logs.add(
                    McpRpcLog(
                        id = "RES-$requestId",
                        timestamp = System.currentTimeMillis(),
                        direction = "INCOMING_RESPONSE",
                        method = "$method (HTTP ${response.code}, ${elapsed}ms)",
                        payloadJson = jsonEnvelope.toString(2),
                        isSuccess = true
                    )
                )
                RpcCallResult(
                    isSuccess = true,
                    resultObject = resultObj,
                    sessionId = respSessionId,
                    rawPayload = jsonEnvelope.toString(2)
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val errJson = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("error", JSONObject().apply {
                    put("code", -32000)
                    put("message", e.message ?: e.javaClass.simpleName)
                })
            }.toString(2)
            logs.add(
                McpRpcLog(
                    id = "ERR-$requestId",
                    timestamp = System.currentTimeMillis(),
                    direction = "INCOMING_RESPONSE",
                    method = "$method:offline (${e.javaClass.simpleName}, ${elapsed}ms)",
                    payloadJson = errJson,
                    isSuccess = false
                )
            )
            RpcCallResult(
                isSuccess = false,
                rawPayload = errJson,
                errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Connection failed"}"
            )
        }
    }

    private fun sendNotification(
        connector: McpConnectorConfig,
        sessionId: String?,
        method: String
    ) {
        runCatching {
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", method)
            }.toString()
            val builder = Request.Builder()
                .url(connector.url.trim())
                .addHeader("User-Agent", "NithixNiftyDesk-MCP-Client/1.0 (Android)")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader("Content-Type", "application/json")
            if (!sessionId.isNullOrBlank()) {
                builder.addHeader("Mcp-Session-Id", sessionId)
            }
            applyAuthAndCustomHeaders(builder, connector)
            val request = builder.post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().close()
        }
    }

    private fun applyAuthAndCustomHeaders(builder: Request.Builder, connector: McpConnectorConfig) {
        when (connector.authType) {
            McpAuthType.NONE -> Unit
            McpAuthType.BEARER_TOKEN, McpAuthType.OAUTH_PKCE -> {
                val token = connector.authSecret.trim()
                if (token.isNotEmpty()) {
                    val value = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                    builder.addHeader("Authorization", value)
                }
            }
            McpAuthType.API_KEY_HEADER -> {
                val headerName = connector.authHeaderName.trim().ifBlank { "X-API-Key" }
                val secret = connector.authSecret.trim()
                if (secret.isNotEmpty()) {
                    builder.addHeader(headerName, secret)
                }
            }
        }
        connector.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank()) {
                builder.addHeader(k.trim(), v.trim())
            }
        }
    }

    internal fun unwrapJsonRpcEnvelope(rawBody: String, expectedId: String): JSONObject? {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed) }.getOrNull()
        }
        if (trimmed.startsWith("[")) {
            val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return null
            repeat(arr.length()) { i ->
                val obj = arr.optJSONObject(i) ?: return@repeat
                if (obj.optString("id") == expectedId || obj.has("result") || obj.has("error")) {
                    return obj
                }
            }
            return null
        }

        // Parse Server-Sent Events (SSE): collect each `data:` block
        val candidates = mutableListOf<JSONObject>()
        val dataBuffer = StringBuilder()
        for (line in rawBody.lines()) {
            val cleanLine = line.trimEnd('\r')
            if (cleanLine.startsWith("data:")) {
                val content = cleanLine.substringAfter("data:").trimStart()
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(content)
            } else if (cleanLine.isBlank() && dataBuffer.isNotEmpty()) {
                runCatching { JSONObject(dataBuffer.toString()) }.getOrNull()?.let { candidates.add(it) }
                dataBuffer.clear()
            }
        }
        if (dataBuffer.isNotEmpty()) {
            runCatching { JSONObject(dataBuffer.toString()) }.getOrNull()?.let { candidates.add(it) }
        }

        return candidates.firstOrNull { it.optString("id") == expectedId }
            ?: candidates.lastOrNull { it.has("result") || it.has("error") }
            ?: candidates.firstOrNull()
    }

    internal fun parseToolsList(resultObj: JSONObject?): List<McpDiscoveredTool> {
        val toolsArr = resultObj?.optJSONArray("tools") ?: return emptyList()
        return buildList {
            repeat(toolsArr.length()) { i ->
                val item = toolsArr.optJSONObject(i) ?: return@repeat
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@repeat
                val description = item.optString("description", "MCP Tool")
                val inputSchema = item.optJSONObject("inputSchema")
                val requiredList = buildList {
                    inputSchema?.optJSONArray("required")?.let { reqArr ->
                        repeat(reqArr.length()) { r ->
                            reqArr.optString(r).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                val propsMap = linkedMapOf<String, String>().apply {
                    inputSchema?.optJSONObject("properties")?.let { propsObj ->
                        propsObj.keys().forEach { key ->
                            val propDef = propsObj.optJSONObject(key)
                            val type = propDef?.optString("type", "string").orEmpty()
                            val desc = propDef?.optString("description", "").orEmpty()
                            this[key] = if (desc.isNotBlank()) "$type — $desc" else type
                        }
                    }
                }
                add(
                    McpDiscoveredTool(
                        name = name,
                        description = description,
                        inputSchemaJson = inputSchema?.toString(2) ?: "{}",
                        requiredParams = requiredList,
                        parameterProperties = propsMap
                    )
                )
            }
        }
    }

    internal fun parseResourcesList(resultObj: JSONObject?): List<McpDiscoveredResource> {
        val arr = resultObj?.optJSONArray("resources") ?: return emptyList()
        return buildList {
            repeat(arr.length()) { i ->
                val item = arr.optJSONObject(i) ?: return@repeat
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@repeat
                add(
                    McpDiscoveredResource(
                        uri = uri,
                        name = item.optString("name", uri),
                        mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                        description = item.optString("description").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    internal fun parsePromptsList(resultObj: JSONObject?): List<McpDiscoveredPrompt> {
        val arr = resultObj?.optJSONArray("prompts") ?: return emptyList()
        return buildList {
            repeat(arr.length()) { i ->
                val item = arr.optJSONObject(i) ?: return@repeat
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@repeat
                add(
                    McpDiscoveredPrompt(
                        name = name,
                        description = item.optString("description").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    internal fun extractToolOutputText(resultObj: JSONObject, rawFallback: String): String {
        val contentArr = resultObj.optJSONArray("content")
        if (contentArr != null && contentArr.length() > 0) {
            val parts = buildList {
                repeat(contentArr.length()) { i ->
                    val item = contentArr.optJSONObject(i) ?: return@repeat
                    when (item.optString("type", "text")) {
                        "text" -> item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                        "resource" -> {
                            val res = item.optJSONObject("resource")
                            val txt = res?.optString("text")?.takeIf { it.isNotBlank() }
                                ?: res?.optString("uri")
                            if (!txt.isNullOrBlank()) add(txt)
                        }
                        else -> add(item.toString(2))
                    }
                }
            }
            if (parts.isNotEmpty()) return parts.joinToString("\n\n")
        }
        val structured = resultObj.opt("structuredContent")
        if (structured != null) return structured.toString()
        return resultObj.toString(2).ifBlank { rawFallback }
    }
}
