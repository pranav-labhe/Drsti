package com.pranav.drsti.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device storage for the OpenAI API key (spec §49 — never
 * hard-coded, never logged, never included in export). Everything else
 * (astrology system config, cache settings, etc.) lives in AppSettingsEntity
 * via Room instead, since it isn't sensitive.
 */
class SecureCredentialStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "drsti_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENAI_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_OPENAI_API_KEY, null)

    fun clearApiKey() {
        prefs.edit().remove(KEY_OPENAI_API_KEY).apply()
    }

    fun saveGeminiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }

    fun getGeminiApiKey(): String? = prefs.getString(KEY_GEMINI_API_KEY, null)

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
