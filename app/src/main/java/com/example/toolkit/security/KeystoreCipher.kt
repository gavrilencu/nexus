package com.example.toolkit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption backed by a **non-exportable** key held in the
 * AndroidKeyStore (hardware-backed where available).
 *
 * Used to protect secrets at rest — the MITM root-CA private key and saved API
 * keys — so they are never written to disk in the clear and are useless if a
 * device backup or the raw files are ever exfiltrated: the wrapping key never
 * leaves the Keystore and cannot be backed up.
 *
 * Blob layout: `iv(12) || ciphertext+tag`.
 */
object KeystoreCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun secretKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypts [plaintext], returning `iv || ciphertext+tag`. */
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /** Reverses [encrypt]. Throws if the blob is malformed or the tag fails. */
    fun decrypt(alias: String, blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "ciphertext blob too short" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ciphertext = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(alias: String, value: String): String =
        Base64.encodeToString(encrypt(alias, value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun decryptString(alias: String, encoded: String): String =
        String(decrypt(alias, Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
}
