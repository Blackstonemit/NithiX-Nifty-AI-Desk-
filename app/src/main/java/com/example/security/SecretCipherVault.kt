package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM cipher helper for encrypting persisted
 * broker tokens, OAuth refresh tokens, and third-party LLM API keys at rest.
 *
 * Falls back to an ephemeral process-scoped key strictly inside JVM/Robolectric unit test
 * sandboxes where the AndroidKeyStore security provider is unavailable.
 */
object SecretCipherVault {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "nithix_nifty_desk_vault_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_V1 = "enc:v1:"

    @Volatile
    private var cachedRobolectricKey: SecretKey? = null

    fun encrypt(plainText: String): String {
        val trimmed = plainText.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith(PREFIX_V1)) return trimmed

        return runCatching {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$PREFIX_V1$ivB64:$cipherB64"
        }.getOrElse {
            // Never silently swallow failure on real Android; only fallback in Robolectric
            if (isRobolectric()) trimmed else throw IllegalStateException("Vault encryption failed", it)
        }
    }

    fun decrypt(storedValue: String): String {
        val raw = storedValue.trim()
        if (raw.isEmpty()) return ""
        if (!raw.startsWith(PREFIX_V1)) {
            // Backward compatibility for pre-existing or test plaintext values
            return raw
        }
        val payload = raw.removePrefix(PREFIX_V1)
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        if (isRobolectric()) {
            return cachedRobolectricKey ?: synchronized(this) {
                cachedRobolectricKey ?: run {
                    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    SecretKeySpec(bytes, "AES").also { cachedRobolectricKey = it }
                }
            }
        }
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
}
