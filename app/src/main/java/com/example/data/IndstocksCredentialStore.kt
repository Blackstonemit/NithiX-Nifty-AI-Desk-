package com.example.data

import android.content.Context
import com.example.model.ExecutionSafetyMode
import com.example.security.SecretCipherVault

interface IndstocksCredentialPersistence {
    fun loadAccessToken(): String
    fun saveAccessToken(token: String)
    fun loadClientId(): String
    fun saveClientId(clientId: String)
    fun loadExecutionMode(): ExecutionSafetyMode
    fun saveExecutionMode(mode: ExecutionSafetyMode)
    fun loadLiveRiskAcknowledged(): Boolean
    fun saveLiveRiskAcknowledged(acknowledged: Boolean)
    fun clearCredentials()
}

class InMemoryIndstocksCredentialStore : IndstocksCredentialPersistence {
    private var accessToken: String = ""
    private var clientId: String = ""
    private var executionMode: ExecutionSafetyMode = ExecutionSafetyMode.PAPER_SIMULATION
    private var liveRiskAcknowledged: Boolean = false

    override fun loadAccessToken(): String = accessToken

    override fun saveAccessToken(token: String) {
        accessToken = token.trim()
    }

    override fun loadClientId(): String = clientId

    override fun saveClientId(clientId: String) {
        this.clientId = clientId.trim()
    }

    override fun loadExecutionMode(): ExecutionSafetyMode = executionMode

    override fun saveExecutionMode(mode: ExecutionSafetyMode) {
        executionMode = mode
    }

    override fun loadLiveRiskAcknowledged(): Boolean = liveRiskAcknowledged

    override fun saveLiveRiskAcknowledged(acknowledged: Boolean) {
        liveRiskAcknowledged = acknowledged
    }

    override fun clearCredentials() {
        accessToken = ""
        executionMode = ExecutionSafetyMode.PAPER_SIMULATION
        liveRiskAcknowledged = false
    }
}

class SharedPreferencesIndstocksCredentialStore(
    context: Context
) : IndstocksCredentialPersistence {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadAccessToken(): String =
        SecretCipherVault.decrypt(prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty())

    override fun saveAccessToken(token: String) {
        val cleaned = token.trim()
        val stored = if (cleaned.isEmpty()) "" else SecretCipherVault.encrypt(cleaned)
        prefs.edit().putString(KEY_ACCESS_TOKEN, stored).apply()
    }

    override fun loadClientId(): String =
        SecretCipherVault.decrypt(prefs.getString(KEY_CLIENT_ID, "").orEmpty())

    override fun saveClientId(clientId: String) {
        val cleaned = clientId.trim()
        val stored = if (cleaned.isEmpty()) "" else SecretCipherVault.encrypt(cleaned)
        prefs.edit().putString(KEY_CLIENT_ID, stored).apply()
    }

    override fun loadExecutionMode(): ExecutionSafetyMode {
        val raw = prefs.getString(KEY_EXECUTION_MODE, ExecutionSafetyMode.PAPER_SIMULATION.name).orEmpty()
        return runCatching { ExecutionSafetyMode.valueOf(raw) }
            .getOrDefault(ExecutionSafetyMode.PAPER_SIMULATION)
    }

    override fun saveExecutionMode(mode: ExecutionSafetyMode) {
        prefs.edit().putString(KEY_EXECUTION_MODE, mode.name).apply()
    }

    override fun loadLiveRiskAcknowledged(): Boolean =
        prefs.getBoolean(KEY_LIVE_RISK_ACK, false)

    override fun saveLiveRiskAcknowledged(acknowledged: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_RISK_ACK, acknowledged).apply()
    }

    override fun clearCredentials() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .putString(KEY_EXECUTION_MODE, ExecutionSafetyMode.PAPER_SIMULATION.name)
            .putBoolean(KEY_LIVE_RISK_ACK, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "indstocks_credentials_prefs"
        private const val KEY_ACCESS_TOKEN = "indstocks_access_token"
        private const val KEY_CLIENT_ID = "indstocks_client_id"
        private const val KEY_EXECUTION_MODE = "indstocks_execution_mode"
        private const val KEY_LIVE_RISK_ACK = "indstocks_live_risk_ack"
    }
}
