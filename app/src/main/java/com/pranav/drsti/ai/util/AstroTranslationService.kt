package com.pranav.drsti.ai.util

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Provides offline translation services using ML Kit Translate.
 * Supports English <-> Hindi and English <-> Marathi.
 */
class AstroTranslationService {
    
    private val translators = mutableMapOf<String, Translator>()

    /**
     * Translates text from English to the target language (hi or mr).
     * Downloads models if necessary.
     */
    suspend fun translate(text: String, targetLanguageCode: String): String {
        if (targetLanguageCode.lowercase() == "en" || targetLanguageCode.isBlank()) {
            return text
        }

        val targetLang = when (targetLanguageCode.lowercase()) {
            "hi" -> TranslateLanguage.HINDI
            "mr" -> TranslateLanguage.MARATHI
            else -> return text // Fallback to original
        }

        return try {
            val translator = getTranslator(TranslateLanguage.ENGLISH, targetLang)
            
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            translator.downloadModelIfNeeded(conditions).await()
            
            // Perform translation
            translator.translate(text).await()
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed for $targetLanguageCode", e)
            text // Fallback to original on error
        }
    }

    private fun getTranslator(source: String, target: String): Translator {
        val key = "$source-$target"
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            Translation.getClient(options)
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    companion object {
        private const val TAG = "AstroTranslationService"
    }
}
