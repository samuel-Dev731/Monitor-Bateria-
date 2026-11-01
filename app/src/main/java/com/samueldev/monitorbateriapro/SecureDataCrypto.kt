package com.samueldev.monitorbateriapro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM helper for data the app may send to an external service.
 *
 * Call encrypt() before placing app-owned sensitive data in a network request.
 * Use HTTPS as well: encryption here does not replace TLS.
 * The AES key is generated and kept in Android Keystore; it is not embedded
 * in source code or transmitted to a server.
 */
object SecureDataCrypto {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "monitor_bateria_pro_aes256_gcm"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE_BITS = 256
    private const val GCM_TAG_SIZE_BITS = 128

    data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(AES_KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(plainText: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedPayload(cipher.iv, cipher.doFinal(plainText))
    }

    fun decrypt(payload: EncryptedPayload): ByteArray {
        require(payload.iv.isNotEmpty()) { "IV de criptografia ausente." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, payload.iv)
        )
        return cipher.doFinal(payload.ciphertext)
    }
}
