package com.samueldev.monitorbateriapro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM helper for encrypting sensitive app payloads before storage or handoff.
 *
 * The key is generated and kept in Android Keystore. The returned envelope is:
 * 1-byte format version | 12-byte GCM nonce | ciphertext + 16-byte authentication tag.
 *
 * This is local device-key encryption. A remote server cannot decrypt this envelope
 * unless a separate, authenticated key-agreement/provisioning design is implemented.
 */
internal object SecurePayloadCipher {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "monitor_bateria_payload_aes256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val FORMAT_VERSION: Byte = 1

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypts bytes with AES-256-GCM and returns a versioned binary envelope. */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        associatedData?.let(cipher::updateAAD)
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        require(nonce.size == NONCE_SIZE_BYTES) { "Unexpected AES-GCM nonce size" }
        return ByteBuffer.allocate(1 + nonce.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(nonce)
            .put(ciphertext)
            .array()
    }

    /** Decrypts an envelope created on this same installation/device. */
    fun decrypt(envelope: ByteArray, associatedData: ByteArray? = null): ByteArray {
        require(envelope.size > 1 + NONCE_SIZE_BYTES + 16) { "Invalid encrypted envelope" }
        val buffer = ByteBuffer.wrap(envelope)
        require(buffer.get() == FORMAT_VERSION) { "Unsupported encrypted envelope version" }
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_SIZE_BITS, nonce)
        )
        associatedData?.let(cipher::updateAAD)
        return cipher.doFinal(ciphertext)
    }
}
