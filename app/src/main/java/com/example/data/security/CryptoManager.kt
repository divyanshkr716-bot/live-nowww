package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(private val context: Context) {

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }
    } catch (e: Exception) {
        null
    }

    private val fallbackKey: SecretKey by lazy {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        keyGen.generateKey()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = keyStore ?: return fallbackKey
        return try {
            val existingKey = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingKey != null) {
                return existingKey.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            fallbackKey
        }
    }

    /**
     * Encrypts plaintext stream key with AES-256 GCM.
     * Returns a base64 encoded string containing the IV prepended to the ciphertext.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts base64 encoded string containing IV + AES-GCM ciphertext.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "yt_loop_live_stream_key_alias"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        /**
         * Masks a stream key for safe UI display (e.g. "****-****-****-1234").
         */
        fun maskStreamKey(key: String): String {
            if (key.length <= 4) return "****"
            val lastFour = key.takeLast(4)
            return "••••-••••-••••-$lastFour"
        }

        /**
         * Redacts any stream key patterns or URLs from diagnostic log lines.
         */
        fun redactLog(message: String): String {
            var sanitized = message
            // Redact RTMP URL endings where keys might be appended
            sanitized = sanitized.replace(
                Regex("""(rtmp[s]?://[^/\s]+/[^/\s]+/)([a-zA-Z0-9_\-\.]{4,})"""),
                "$1[REDACTED_STREAM_KEY]"
            )
            // Redact key parameters
            sanitized = sanitized.replace(
                Regex("""(key|streamkey|token|secret)=([a-zA-Z0-9_\-\.]{4,})""", RegexOption.IGNORE_CASE),
                "$1=[REDACTED]"
            )
            return sanitized
        }
    }
}
