package com.example.privkeyboard

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * On-device encryption. Keys are generated inside the Android Keystore and never
 * leave it. Data is AES-256-GCM encrypted before it is written to the database.
 * (User-passphrase keys for end-to-end encrypted cloud sync come in a later phase.)
 */
object CryptoBox {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_ALIAS = "privkb_local_aes_v1"
    private const val MAC_ALIAS = "privkb_local_hmac_v1"
    private const val IV_SIZE = 12

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    private fun aesKey(): SecretKey {
        val existing = keyStore().getKey(AES_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    @Synchronized
    private fun macKey(): SecretKey {
        val existing = keyStore().getKey(MAC_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        kg.init(KeyGenParameterSpec.Builder(MAC_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return kg.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val iv = cipher.iv
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    fun encryptString(s: String): ByteArray = encrypt(s.toByteArray(Charsets.UTF_8))

    fun decryptString(blob: ByteArray): String = String(decrypt(blob), Charsets.UTF_8)

    /** Keyed fingerprint used only to detect duplicate clips without storing plaintext. */
    fun fingerprint(text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey())
        return mac.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
