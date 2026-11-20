package com.example.network

import android.net.Uri
import android.util.Base64
import com.example.model.McpOAuthMetadata
import com.example.model.McpPendingOAuthFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

data class McpOAuthTokenResult(
    val isSuccess: Boolean,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresInSec: Long? = null,
    val errorMessage: String? = null
)

data class McpOAuthFlowPreparationResult(
    val isSuccess: Boolean,
    val pendingFlow: McpPendingOAuthFlow? = null,
    val updatedMetadata: McpOAuthMetadata? = null,
    val errorMessage: String? = null
)

class LoopbackOAuthServer(
    private val serverSocket: ServerSocket,
    private val job: Job
) {
    val port: Int get() = serverSocket.localPort
    val redirectUri: String get() = "http://127.0.0.1:${serverSocket.localPort}/callback"

    fun close() {
        runCatching { job.cancel() }
        runCatching { serverSocket.close() }
    }
}

/**
 * Implements MCP Authorization (OAuth 2.1 + PKCE S256 + RFC 9728 Protected Resource Metadata +
 * RFC 8414 Authorization Server Discovery + RFC 7591 Dynamic Client Registration).
 */
class McpOAuthClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val CUSTOM_APP_REDIRECT_URI = "niftydesk://oauth/callback"
    }

    fun parseWwwAuthenticateResourceMetadata(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val regex = Regex("""resource_metadata\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        return regex.find(header)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun parseWwwAuthenticateScope(header: String?): List<String> {
        if (header.isNullOrBlank()) return emptyList()
        val regex = Regex("""scope\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val raw = regex.find(header)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return raw.split(Regex("""\s+""")).filter { it.isNotBlank() }
    }

    suspend fun discoverOAuthMetadata(
        serverUrl: String,
        wwwAuthenticateHeader: String? = null
    ): McpOAuthMetadata? = withContext(Dispatchers.IO) {
        val parsedServer = serverUrl.trim().toHttpUrlOrNull() ?: return@withContext null
        val origin = buildString {
            append(parsedServer.scheme).append("://").append(parsedServer.host)
            val isDefaultPort = (parsedServer.scheme == "https" && parsedServer.port == 443) ||
                (parsedServer.scheme == "http" && parsedServer.port == 80)
            if (!isDefaultPort) append(":").append(parsedServer.port)
        }
        val path = parsedServer.encodedPath.trimEnd('/')

        val prmCandidates = buildList {
            parseWwwAuthenticateResourceMetadata(wwwAuthenticateHeader)?.let { add(it) }
            if (path.isNotBlank() && path != "/") {
                add("$origin/.well-known/oauth-protected-resource$path")
            }
            add("$origin/.well-known/oauth-protected-resource")
        }.distinct()

        var prmJson: JSONObject? = null
        for (candidate in prmCandidates) {
            prmJson = fetchJsonGet(candidate)
            if (prmJson != null) break
        }

        val resourceUrl = prmJson?.optString("resource")?.takeIf { it.isNotBlank() } ?: serverUrl.trim()
        val prmScopes = parseScopesArray(prmJson?.optJSONArray("scopes_supported"))
        val headerScopes = parseWwwAuthenticateScope(wwwAuthenticateHeader)

        val authServers = prmJson?.optJSONArray("authorization_servers")
        val authServerUrl = (0 until (authServers?.length() ?: 0))
            .firstNotNullOfOrNull { idx -> authServers?.optString(idx)?.takeIf { it.isNotBlank() } }
            ?: prmJson?.optString("authorization_server")?.takeIf { it.isNotBlank() }
            ?: origin

        val parsedAuth = authServerUrl.toHttpUrlOrNull()
        val authOrigin = if (parsedAuth != null) {
            buildString {
                append(parsedAuth.scheme).append("://").append(parsedAuth.host)
                val isDefault = (parsedAuth.scheme == "https" && parsedAuth.port == 443) ||
                    (parsedAuth.scheme == "http" && parsedAuth.port == 80)
                if (!isDefault) append(":").append(parsedAuth.port)
            }
        } else {
            origin
        }
        val authPath = parsedAuth?.encodedPath?.trimEnd('/').orEmpty()

        val asCandidates = buildList {
            if (authPath.isNotBlank() && authPath != "/") {
                add("$authOrigin/.well-known/oauth-authorization-server$authPath")
                add("$authOrigin/.well-known/openid-configuration$authPath")
                add("${authServerUrl.trimEnd('/')}/.well-known/oauth-authorization-server")
                add("${authServerUrl.trimEnd('/')}/.well-known/openid-configuration")
            }
            add("$authOrigin/.well-known/oauth-authorization-server")
            add("$authOrigin/.well-known/openid-configuration")
        }.distinct()

        var asJson: JSONObject? = null
        for (candidate in asCandidates) {
            val fetched = fetchJsonGet(candidate)
            if (fetched != null && fetched.optString("authorization_endpoint").isNotBlank()) {
                asJson = fetched
                break
            }
        }

        if (asJson == null && prmJson == null) {
            return@withContext null
        }

        val asScopes = parseScopesArray(asJson?.optJSONArray("scopes_supported"))
        val combinedScopes = (headerScopes + prmScopes + asScopes).distinct()

        McpOAuthMetadata(
            resourceUrl = resourceUrl,
            authorizationEndpoint = asJson?.optString("authorization_endpoint")?.takeIf { it.isNotBlank() }
                ?: "$authOrigin/authorize",
            tokenEndpoint = asJson?.optString("token_endpoint")?.takeIf { it.isNotBlank() }
                ?: "$authOrigin/token",
            registrationEndpoint = asJson?.optString("registration_endpoint")?.takeIf { it.isNotBlank() },
            issuer = asJson?.optString("issuer")?.takeIf { it.isNotBlank() } ?: authServerUrl,
            scopesSupported = combinedScopes
        )
    }

    fun startLoopbackCallbackServer(onRedirectUriReceived: (Uri) -> Unit): LoopbackOAuthServer {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val job = CoroutineScope(Dispatchers.IO).launch {
            while (!socket.isClosed) {
                val clientSocket = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    clientSocket.use { conn ->
                        val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        val targetPath = requestLine.split(" ").getOrNull(1) ?: "/callback"
                        val fullUri = Uri.parse("http://127.0.0.1:${socket.localPort}$targetPath")

                        val html = """
                            <!doctype html>
                            <html>
                            <head><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                            <body style="font-family:sans-serif;background:#080D16;color:#E6EDF3;padding:32px;text-align:center;">
                              <h2 style="color:#00E5FF;">MCP Connector Authorized</h2>
                              <p>Authorization code received by Nithix NIFTY Desk. You can return to the app now.</p>
                            </body>
                            </html>
                        """.trimIndent()
                        val responseBytes = html.toByteArray(Charsets.UTF_8)
                        val headers = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        conn.getOutputStream().apply {
                            write(headers.toByteArray(Charsets.UTF_8))
                            write(responseBytes)
                            flush()
                        }

                        if (!fullUri.getQueryParameter("code").isNullOrBlank() ||
                            !fullUri.getQueryParameter("error").isNullOrBlank()
                        ) {
                            onRedirectUriReceived(fullUri)
                        }
                    }
                }
            }
        }
        return LoopbackOAuthServer(socket, job)
    }

    suspend fun prepareOAuthFlow(
        connectorId: String,
        connectorName: String,
        serverUrl: String,
        existingMetadata: McpOAuthMetadata?,
        explicitClientId: String,
        redirectUri: String
    ): McpOAuthFlowPreparationResult = withContext(Dispatchers.IO) {
        val metadata = existingMetadata?.takeIf { !it.authorizationEndpoint.isNullOrBlank() && !it.tokenEndpoint.isNullOrBlank() }
            ?: discoverOAuthMetadata(serverUrl)
            ?: return@withContext McpOAuthFlowPreparationResult(
                isSuccess = false,
                errorMessage = "Could not discover OAuth endpoints (/.well-known/oauth-authorization-server). Paste a Bearer token manually or verify server URL."
            )

        val authEndpoint = metadata.authorizationEndpoint
            ?: return@withContext McpOAuthFlowPreparationResult(
                isSuccess = false,
                updatedMetadata = metadata,
                errorMessage = "Authorization server metadata is missing authorization_endpoint."
            )
        val tokenEndpoint = metadata.tokenEndpoint
            ?: return@withContext McpOAuthFlowPreparationResult(
                isSuccess = false,
                updatedMetadata = metadata,
                errorMessage = "Authorization server metadata is missing token_endpoint."
            )

        var resolvedClientId = explicitClientId.trim()
            .ifBlank { metadata.discoveredClientId.orEmpty() }

        if (resolvedClientId.isBlank() && !metadata.registrationEndpoint.isNullOrBlank()) {
            val regOutcome = registerDynamicClient(
                registrationEndpoint = metadata.registrationEndpoint,
                redirectUris = listOf(redirectUri, CUSTOM_APP_REDIRECT_URI).distinct()
            )
            if (regOutcome != null) {
                resolvedClientId = regOutcome
            }
        }

        if (resolvedClientId.isBlank()) {
            return@withContext McpOAuthFlowPreparationResult(
                isSuccess = false,
                updatedMetadata = metadata,
                errorMessage = "Dynamic Client Registration unavailable; enter a Client ID or paste an Access Token."
            )
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = computeS256CodeChallenge(codeVerifier)
        val state = UUID.randomUUID().toString().replace("-", "").take(18)

        val authHttpUrl = authEndpoint.toHttpUrlOrNull()
            ?: return@withContext McpOAuthFlowPreparationResult(
                isSuccess = false,
                updatedMetadata = metadata,
                errorMessage = "Invalid authorization_endpoint URL: $authEndpoint"
            )

        val urlBuilder = authHttpUrl.newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", resolvedClientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("resource", metadata.resourceUrl)

        if (metadata.scopesSupported.isNotEmpty()) {
            urlBuilder.addQueryParameter("scope", metadata.scopesSupported.joinToString(" "))
        }

        val updatedMeta = metadata.copy(discoveredClientId = resolvedClientId)
        val pendingFlow = McpPendingOAuthFlow(
            connectorId = connectorId,
            connectorName = connectorName,
            serverUrl = serverUrl,
            authorizationUrl = urlBuilder.build().toString(),
            tokenEndpoint = tokenEndpoint,
            clientId = resolvedClientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
            state = state,
            resourceUrl = metadata.resourceUrl
        )

        McpOAuthFlowPreparationResult(
            isSuccess = true,
            pendingFlow = pendingFlow,
            updatedMetadata = updatedMeta
        )
    }

    suspend fun exchangeCodeForToken(
        flow: McpPendingOAuthFlow,
        codeOrCallbackUrl: String
    ): McpOAuthTokenResult = withContext(Dispatchers.IO) {
        val raw = codeOrCallbackUrl.trim()
        if (raw.isBlank()) {
            return@withContext McpOAuthTokenResult(
                isSuccess = false,
                errorMessage = "Authorization code or callback URL is required"
            )
        }

        val extractedCode: String = if (raw.contains("://") || raw.contains("?")) {
            val uri = Uri.parse(raw)
            val err = uri.getQueryParameter("error")
            if (!err.isNullOrBlank()) {
                val desc = uri.getQueryParameter("error_description") ?: err
                return@withContext McpOAuthTokenResult(
                    isSuccess = false,
                    errorMessage = "OAuth authorization denied: $desc"
                )
            }
            val returnedState = uri.getQueryParameter("state")
            if (returnedState.isNullOrBlank() || returnedState != flow.state) {
                return@withContext McpOAuthTokenResult(
                    isSuccess = false,
                    errorMessage = "OAuth state missing or mismatched — please retry authorization"
                )
            }
            uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
                ?: return@withContext McpOAuthTokenResult(
                    isSuccess = false,
                    errorMessage = "Callback URL did not include an authorization code"
                )
        } else {
            raw
        }

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", extractedCode)
            .add("redirect_uri", flow.redirectUri)
            .add("client_id", flow.clientId)
            .add("code_verifier", flow.codeVerifier)
            .add("resource", flow.resourceUrl)
            .build()

        executeTokenRequest(flow.tokenEndpoint, form)
    }

    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        refreshToken: String,
        clientId: String,
        resourceUrl: String
    ): McpOAuthTokenResult = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
        if (clientId.isNotBlank()) formBuilder.add("client_id", clientId)
        if (resourceUrl.isNotBlank()) formBuilder.add("resource", resourceUrl)
        executeTokenRequest(tokenEndpoint, formBuilder.build())
    }

    private fun executeTokenRequest(tokenEndpoint: String, formBody: FormBody): McpOAuthTokenResult {
        return try {
            val request = Request.Builder()
                .url(tokenEndpoint)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "NithixNiftyDesk-MCP-Client/1.0 (Android)")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errDetail = runCatching {
                        val obj = JSONObject(body)
                        obj.optString("error_description").ifBlank { obj.optString("error") }
                    }.getOrNull().orEmpty()
                    return McpOAuthTokenResult(
                        isSuccess = false,
                        errorMessage = "Token exchange failed (HTTP ${response.code}): ${errDetail.ifBlank { response.message }}"
                    )
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: return McpOAuthTokenResult(
                        isSuccess = false,
                        errorMessage = "Token response missing access_token"
                    )
                McpOAuthTokenResult(
                    isSuccess = true,
                    accessToken = accessToken,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    tokenType = json.optString("token_type", "Bearer"),
                    expiresInSec = json.optLong("expires_in", 0L).takeIf { it > 0L }
                )
            }
        } catch (e: Exception) {
            McpOAuthTokenResult(
                isSuccess = false,
                errorMessage = "Token request error: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun registerDynamicClient(
        registrationEndpoint: String,
        redirectUris: List<String>
    ): String? {
        val candidates = if (redirectUris.size > 1) {
            listOf(redirectUris, listOf(redirectUris.first()))
        } else {
            listOf(redirectUris)
        }

        for (uriList in candidates) {
            val payload = JSONObject().apply {
                put("client_name", "Nithix NIFTY Desk")
                put("redirect_uris", JSONArray(uriList))
                put("grant_types", JSONArray(listOf("authorization_code", "refresh_token")))
                put("response_types", JSONArray(listOf("code")))
                put("token_endpoint_auth_method", "none")
            }.toString()

            val resultId = runCatching {
                val request = Request.Builder()
                    .url(registrationEndpoint)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    JSONObject(body).optString("client_id").takeIf { it.isNotBlank() }
                }
            }.getOrNull()

            if (resultId != null) return resultId
        }
        return null
    }

    private fun fetchJsonGet(url: String): JSONObject? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "NithixNiftyDesk-MCP-Client/1.0 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) null else JSONObject(body)
            }
        }.getOrNull()
    }

    private fun parseScopesArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    internal fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    internal fun computeS256CodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
