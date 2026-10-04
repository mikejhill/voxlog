package com.mikejhill.voxlog.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores secrets such as API keys. Values never leave the device and are excluded from export. */
interface SecretStore {
    /** Returns the secret stored under [name], or null. */
    suspend fun read(name: String): String?

    /** Stores [value] under [name], or removes it when [value] is null or blank. */
    suspend fun write(name: String, value: String?)
}

/**
 * [SecretStore] that encrypts each value with an AES-256-GCM key held in the Android Keystore.
 * Ciphertext lives in `no_backup/secrets/`, which Android never includes in backups.
 */
@Singleton
class KeystoreSecretStore
@Inject
constructor(@param:ApplicationContext private val context: Context) : SecretStore {
    private val directory: File
        get() = File(context.noBackupFilesDir, "secrets").apply { mkdirs() }

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        val file = File(directory, fileNameFor(name))
        if (!file.exists()) return@withContext null
        val payload = Base64.decode(file.readText(), Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.doFinal(cipherText).decodeToString()
    }

    override suspend fun write(name: String, value: String?) = withContext(Dispatchers.IO) {
        val file = File(directory, fileNameFor(name))
        if (value.isNullOrBlank()) {
            file.delete()
            return@withContext
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        file.writeText(Base64.encodeToString(payload, Base64.NO_WRAP))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun fileNameFor(name: String): String = name.filter { it.isLetterOrDigit() || it == '_' } + ".bin"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "voxlog_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

/** Well-known secret names. */
object SecretNames {
    /** API key for the configured LLM provider. */
    const val LLM_API_KEY: String = "llm_api_key"
}
