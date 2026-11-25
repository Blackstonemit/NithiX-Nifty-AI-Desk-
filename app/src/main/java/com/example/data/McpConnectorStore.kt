package com.example.data

import android.content.Context
import androidx.core.content.edit
import com.example.model.McpAuthType
import com.example.model.McpConnectorConfig
import com.example.model.McpTransportType
import com.example.security.SecretCipherVault
import org.json.JSONArray
import org.json.JSONObject

interface McpConnectorPersistence {
    fun loadCustomConnectors(): List<McpConnectorConfig>
    fun saveCustomConnectors(connectors: List<McpConnectorConfig>)
    fun loadBuiltInEnabledStates(): Map<String, Boolean>
    fun saveBuiltInEnabledState(connectorId: String, enabled: Boolean)
}

class InMemoryMcpConnectorStore : McpConnectorPersistence {
    private var customList: List<McpConnectorConfig> = emptyList()
    private val builtInStates = mutableMapOf<String, Boolean>()

    override fun loadCustomConnectors(): List<McpConnectorConfig> = customList

    override fun saveCustomConnectors(connectors: List<McpConnectorConfig>) {
        customList = connectors.filter { !it.isBuiltIn }
    }

    override fun loadBuiltInEnabledStates(): Map<String, Boolean> = builtInStates.toMap()

    override fun saveBuiltInEnabledState(connectorId: String, enabled: Boolean) {
        builtInStates[connectorId] = enabled
    }
}

class SharedPreferencesMcpConnectorStore(
    context: Context
) : McpConnectorPersistence {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadCustomConnectors(): List<McpConnectorConfig> {
        val raw = prefs.getString(KEY_CUSTOM_CONNECTORS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                repeat(arr.length()) { i ->
                    val obj = arr.optJSONObject(i) ?: return@repeat
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@repeat
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@repeat
                    val url = obj.optString("url").takeIf { it.isNotBlank() } ?: return@repeat
                    val transport = runCatching {
                        McpTransportType.valueOf(obj.optString("transportType", McpTransportType.STREAMABLE_HTTP.name))
                    }.getOrDefault(McpTransportType.STREAMABLE_HTTP)
                    val authType = runCatching {
                        McpAuthType.valueOf(obj.optString("authType", McpAuthType.NONE.name))
                    }.getOrDefault(McpAuthType.NONE)
                    val headersObj = obj.optJSONObject("customHeaders")
                    val headers = buildMap {
                        headersObj?.let { jsonHeaders ->
                            jsonHeaders.keys().forEach { k ->
                                put(k, jsonHeaders.optString(k))
                            }
                        }
                    }
                    add(
                        McpConnectorConfig(
                            id = id,
                            name = name,
                            url = url,
                            transportType = transport,
                            authType = authType,
                            authHeaderName = obj.optString("authHeaderName", "Authorization"),
                            authSecret = SecretCipherVault.decrypt(obj.optString("authSecret", "")),
                            oauthClientId = obj.optString("oauthClientId", ""),
                            oauthRefreshToken = SecretCipherVault.decrypt(obj.optString("oauthRefreshToken", "")),
                            customHeaders = headers,
                            isBuiltIn = false,
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveCustomConnectors(connectors: List<McpConnectorConfig>) {
        val arr = JSONArray()
        connectors.filter { !it.isBuiltIn }.forEach { connector ->
            val obj = JSONObject().apply {
                put("id", connector.id)
                put("name", connector.name)
                put("url", connector.url)
                put("transportType", connector.transportType.name)
                put("authType", connector.authType.name)
                put("authHeaderName", connector.authHeaderName)
                put("authSecret", SecretCipherVault.encrypt(connector.authSecret))
                put("oauthClientId", connector.oauthClientId)
                put("oauthRefreshToken", SecretCipherVault.encrypt(connector.oauthRefreshToken))
                put("isEnabled", connector.isEnabled)
                put("customHeaders", JSONObject().apply {
                    connector.customHeaders.forEach { (k, v) -> put(k, v) }
                })
            }
            arr.put(obj)
        }
        prefs.edit { putString(KEY_CUSTOM_CONNECTORS, arr.toString()) }
    }

    override fun loadBuiltInEnabledStates(): Map<String, Boolean> {
        val raw = prefs.getString(KEY_BUILTIN_STATES, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, obj.optBoolean(key, true))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun saveBuiltInEnabledState(connectorId: String, enabled: Boolean) {
        val current = loadBuiltInEnabledStates().toMutableMap().apply {
            this[connectorId] = enabled
        }
        val obj = JSONObject().apply {
            current.forEach { (k, v) -> put(k, v) }
        }
        prefs.edit { putString(KEY_BUILTIN_STATES, obj.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "mcp_connectors_prefs"
        private const val KEY_CUSTOM_CONNECTORS = "custom_connectors_json"
        private const val KEY_BUILTIN_STATES = "builtin_enabled_states_json"
    }
}
