package com.pranav.drsti.ai

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import com.pranav.drsti.ai.provider.VedicInferenceEngine
import com.pranav.drsti.model.*
import java.time.Instant

/**
 * Orchestrates offline intelligence using native Android capabilities.
 */
class NativeIntelligenceCoordinator(private val context: Context) {

    private val textClassifier: TextClassifier by lazy {
        val manager = context.getSystemService(TextClassificationManager::class.java)
        manager?.textClassifier ?: TextClassifier.NO_OP
    }

    /**
     * Processes a user message offline and returns a reply.
     */
    suspend fun processOffline(
        userMessage: String,
        context: AiRequestContext,
        state: ConversationState
    ): Pair<ChatReply, ConversationState> {
        val m = userMessage.lowercase()
        val intent = recognizeIntent(userMessage)
        
        // Handle language switch
        val updatedLanguage = when {
            "hindi" in m || "हिंदी" in userMessage -> "hi"
            "marathi" in m || "मराठी" in userMessage -> "mr"
            "english" in m -> "en"
            else -> state.languagePreference
        }

        // Contextual resolution: Handle "that", "the first one", "it"
        val resolvedTopic = when {
            "that" in m || " it " in m || "first one" in m || "other one" in m -> state.activeTopic
            intent == NativeIntent.DECISION -> extractDecisionTopic(userMessage)
            else -> state.activeTopic
        }

        // Handle elaboration
        val finalDetailLevel = if (intent == NativeIntent.ELABORATE) DetailLevel.ELABORATE else DetailLevel.SUMMARY

        val responseText = VedicInferenceEngine.synthesize(
            context,
            finalDetailLevel,
            updatedLanguage
        )

        val updatedState = state.copy(
            activeTopic = resolvedTopic,
            languagePreference = updatedLanguage,
            detailLevel = finalDetailLevel,
            lastFactsSnapshot = "Snapshot:${context.kundali?.provenance?.outputHash ?: "none"}"
        )

        val reply = ChatReply(
            text = responseText,
            intent = if (intent == NativeIntent.GENERAL) "OFFLINE_GENERAL" else "OFFLINE_${intent.name}",
            provenance = Provenance(
                calculationVersion = "astrocalc-1.0",
                generatedAt = Instant.now().toString(),
                source = "NativeExpertSystem",
                sourceVersion = "1.0",
                inputHash = "",
                outputHash = ""
            )
        )

        return reply to updatedState
    }

    private fun recognizeIntent(message: String): NativeIntent {
        val m = message.lowercase()
        return when {
            "elaborate" in m || "explain more" in m || "detail" in m || "विस्तार" in m -> NativeIntent.ELABORATE
            "translate" in m || "speak in" in m || "sang" in m || "सांग" in m -> NativeIntent.TRANSLATE
            "should i" in m || "or" in m || "decision" in m || "निवड" in m -> NativeIntent.DECISION
            else -> NativeIntent.GENERAL
        }
    }

    private fun extractDecisionTopic(message: String): String? {
        // Simple extraction for now
        return if (" join " in message) message.substringAfter(" join ").take(30)
        else if (" switch " in message) "Job Change"
        else null
    }

    enum class NativeIntent {
        GENERAL, ELABORATE, TRANSLATE, DECISION, QUERY
    }
}
