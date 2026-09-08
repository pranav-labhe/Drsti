package com.pranav.drsti.ai

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import com.pranav.drsti.ai.provider.VedicInferenceEngine
import com.pranav.drsti.ai.provider.ResponseRenderer
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.model.*
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant

/**
 * Orchestrates offline intelligence using native Android capabilities.
 */
class NativeIntelligenceCoordinator(
    private val context: Context,
    private val decisionRepository: DecisionRepository,
    private val personRepository: PersonRepository
) {

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
        val intent = IntentResolver.resolve(userMessage, state)
        
        // 1. Resolve Language Preference
        val updatedLanguage = when {
            "hindi" in m || "हिंदी" in userMessage -> "hi"
            "marathi" in m || "मराठी" in userMessage -> "mr"
            "english" in m -> "en"
            else -> state.languagePreference
        }

        // 2. Resolve Detail Level & Specialized Domains
        var finalDetailLevel = if (intent == ResolvedIntent.ELABORATE) DetailLevel.ELABORATE else state.detailLevel
        if ("financially" in m || "money" in m || "पैसे" in m) {
            finalDetailLevel = DetailLevel.ELABORATE // Domain-specific follow-ups trigger elaboration
        }

        // 3. Resolve Topic & Decision Context (Repository Linkage & Pronoun Resolution)
        var activeDecisionId = state.activeDecisionId
        var resolvedTopic = state.activeTopic
        var lastActiveEntityId = state.lastActiveEntityId
        
        when (intent) {
            ResolvedIntent.DECISION_START -> {
                val topic = extractDecisionTopic(userMessage)
                resolvedTopic = topic
                // Auto-save new decision to repository
                val person = personRepository.observeActive().firstOrNull()
                if (person != null) {
                    activeDecisionId = decisionRepository.createDecision(
                        personId = person.id,
                        question = userMessage,
                        options = extractOptions(userMessage), 
                        context = "Identified via offline semantic matching",
                        desiredDate = null,
                        aiContext = context,
                        conversationId = state.conversationId
                    )
                }
            }
            ResolvedIntent.DECISION_UPDATE -> {
                if (activeDecisionId != null) {
                    val decision = decisionRepository.getDecision(activeDecisionId)
                    resolvedTopic = decision?.question ?: state.activeTopic
                    
                    // Update decision with new options if found
                    val newOptions = extractOptions(userMessage)
                    if (newOptions.isNotEmpty() && decision != null) {
                        decisionRepository.createDecision(
                            personId = decision.personId,
                            question = decision.question,
                            options = newOptions, // createDecision handles merging/updating internally
                            context = decision.context,
                            desiredDate = decision.desiredDecisionDateIso,
                            aiContext = context,
                            conversationId = state.conversationId
                        )
                    }
                }
            }
            ResolvedIntent.FOLLOW_UP -> {
                // Pronoun resolution logic: "it", "that", "this"
                val isPronounFollowUp = listOf("it", "that", "this", "it's").any { it in m }
                if (isPronounFollowUp && activeDecisionId != null) {
                    val decision = decisionRepository.getDecision(activeDecisionId)
                    resolvedTopic = decision?.question ?: state.activeTopic
                }
                
                // Entity mapping: "the first one", "other one"
                if ("first one" in m) lastActiveEntityId = "option_1"
                if ("other one" in m || "second one" in m) lastActiveEntityId = "option_2"
            }
            else -> {}
        }

        // 4. Execute Structured Reasoning
        val findings = VedicInferenceEngine.evaluate(context)
        val responseText = ResponseRenderer.render(
            findings,
            finalDetailLevel,
            updatedLanguage
        )

        // 5. Update Persisted State
        val updatedState = state.copy(
            activeTopic = resolvedTopic,
            activeDecisionId = activeDecisionId,
            languagePreference = updatedLanguage,
            detailLevel = finalDetailLevel,
            lastIntent = intent.name,
            lastActiveEntityId = lastActiveEntityId,
            lastFactsSnapshot = "Findings:${findings.size}"
        )

        val reply = ChatReply(
            text = responseText,
            intent = "OFFLINE_${intent.name}",
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

    private fun extractDecisionTopic(message: String): String? {
        val m = message.lowercase()
        return when {
            " join " in m -> "Joining " + message.substringAfter(" join ").take(20).trim()
            " switch " in m -> "Career Switch"
            " leave " in m -> "Leaving " + message.substringAfter(" leave ").take(20).trim()
            " or " in m && ("should i" in m || "could i" in m) -> "Choice Analysis"
            else -> "Decision"
        }
    }

    private fun extractOptions(message: String): List<DecisionOptionInput> {
        val m = message.lowercase()
        val options = mutableListOf<DecisionOptionInput>()
        
        // 1. "A or B" pattern
        if (" or " in m) {
            val parts = m.split(" or ")
            parts.forEachIndexed { index, s ->
                val desc = s.split(" ").takeLast(3).joinToString(" ").trim()
                if (desc.isNotEmpty()) options.add(DecisionOptionInput("option_${index + 1}", desc))
            }
        }
        
        // 2. Specific entities
        if (" join " in m) {
            val company = message.substringAfter(" join ").split(" ").firstOrNull()?.trim()
            if (company != null) options.add(DecisionOptionInput("option_join", "Join $company"))
        }

        return options.distinctBy { it.description }
    }
}
