package io.github.arashiyama11.a_larm.infra.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.arashiyama11.a_larm.infra.repository.LlmApiKeyRepositoryImpl.Companion.STORE_NAME
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

@OptIn(ExperimentalEncodingApi::class)
class LlmApiKeyRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LlmApiKeyRepository {

    companion object {
        const val STORE_NAME = "secure_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "llm_api_key_master_aes" // アプリ固有で変更可
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12 // GCM推奨
        private const val PREF_NAME = "llm_api_key"
    }

    // Preferences DataStore（ファイル単位暗号化はしない。値を自前で暗号化）
    private val prefKey = stringPreferencesKey(PREF_NAME)

    override suspend fun getKey(): String? = withContext(Dispatchers.IO) {
        val enc = context.dataStore.data.map { it.get(prefKey) }.first() ?: return@withContext null
        try {
            decrypt(Base64.decode(enc))
        } catch (_: Throwable) {
            // 壊れてたら読めないのでnull扱い
            clearKey()
            null
        }
    }

    override suspend fun setKey(key: String?): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(prefKey)
            } else {
                val encrypted = encrypt(key)

                prefs[prefKey] = Base64.encode(encrypted)
            }
        }
    }

    override suspend fun clearKey(): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { it.remove(prefKey) }
    }

    // ====== Crypto ======

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // .setUserAuthenticationRequired(true) // 端末ロック認証を必須にしたい場合は有効化
            .build()

        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encrypt(plainText: String): ByteArray {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_MODE)
        // ランダムIVはcipher.init(ENCRYPT_MODE, key)で内部生成される
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // 12バイト
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // [IV(12bytes)] + [Cipher+Tag]
        return ByteArray(IV_LENGTH_BYTES + cipherBytes.size).apply {
            System.arraycopy(iv, 0, this, 0, IV_LENGTH_BYTES)
            System.arraycopy(cipherBytes, 0, this, IV_LENGTH_BYTES, cipherBytes.size)
        }
    }

    private fun decrypt(encrypted: ByteArray): String {
        require(encrypted.size > IV_LENGTH_BYTES) { "cipher too short" }
        val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
        val body = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plain = cipher.doFinal(body)
        return plain.toString(Charsets.UTF_8)
    }
}