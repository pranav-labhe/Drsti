package com.pranav.drsti.ai.provider

import com.pranav.drsti.ai.NativeIntelligenceCoordinator
import com.pranav.drsti.ai.SemanticIntelligenceManager
import com.pranav.drsti.ai.provider.VedicInferenceEngine
import com.pranav.drsti.ai.provider.ResponseRenderer
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.dao.ConversationStateDao
import com.pranav.drsti.database.entity.ConversationStateEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Phase 2 Attachment: Intelligent Offline Provider.
 * Wraps the base [MockAiProvider] with advanced reasoning and context tracking.
 * This class is isolated from the main AI providers to preserve the stability 
 * of rigorously tested flows like Gemini.
 */
class NativeVedicProvider(
    private val base: MockAiProvider,
    private val context: android.content.Context,
    private val stateDao: ConversationStateDao,
    private val decisionRepository: DecisionRepository,
    private val personRepository: PersonRepository,
    private val semanticManager: SemanticIntelligenceManager
) : AiAstrologyService by base {

    private val coordinator = NativeIntelligenceCoordinator(context, decisionRepository, personRepository, semanticManager)

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis = withContext(Dispatchers.Default) {
        val lang = "en" 

        val options = request.options.mapIndexed { idx, opt ->
            // Use VedicInferenceEngine for deeper scoring
            val findings = VedicInferenceEngine.evaluate(context)
            
            // Use SLM for "Better Wordings" in the explanation
            val summary = semanticManager.synthesizeInterpretation(findings, lang)
            
            val baseSupport = 60 
            val findingsDelta = findings.sumOf { it.supportDelta }
            
            // Removed noise logic to ensure astrological honesty
            val astro = (baseSupport + findingsDelta).coerceIn(15, 95)
            val timing = (baseSupport + findingsDelta + (idx * 2)).coerceIn(15, 95)
            
            val strength = when {
                astro >= 76 -> "very strong"
                astro >= 61 -> "strong"
                astro >= 41 -> "moderate"
                astro >= 21 -> "weak"
                else -> "very weak"
            }

            DecisionOptionAnalysis(
                id = opt.id,
                astrologicalSupport = astro,
                timingSupport = timing,
                strength = strength,
                strengths = findings.filter { it.supportDelta > 0 }.map { "Strong alignment in ${it.category.name.lowercase()}" },
                concerns = findings.filter { it.supportDelta < 0 }.map { "Check influence in ${it.category.name.lowercase()}" },
                supportingFactors = listOf("Consistent with local Vedic laws"),
                contradictingFactors = emptyList(),
                explanation = "Based on local expert rules, \"${opt.description}\" shows $strength alignment. $summary"
            )
        }

        DecisionAnalysis(
            analysisSummary = "Compared ${options.size} path(s) using native Vedic interpretation laws. The focus is on finding the path with the most stable planetary alignment.",
            options = options,
            preferredOptionId = options.maxByOrNull { it.astrologicalSupport + it.timingSupport }?.id,
            confidence = if (context.kundali != null && context.dasha != null) "medium" else "low",
            caveats = listOf("Derived from local expert rules; for guidance only."),
            provenance = Provenance(
                calculationVersion = "astrocalc-1.0", promptVersion = "decision-v1", model = "native",
                generatedAt = Instant.now().toString(), source = "SmolLM2-135M",
                sourceVersion = "1.0", inputHash = HashUtil.sha256(request.toString()), outputHash = HashUtil.sha256(options.toString())
            )
        )
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply = withContext(Dispatchers.Default) {
        if (conversationId == null) return@withContext base.chat(context, userMessage, conversationId)

        val entity = stateDao.getByConversationId(conversationId)
        val currentState = entity?.let {
            ConversationState(
                it.conversationId, it.activeTopic, it.activeDecisionId, it.languagePreference,
                DetailLevel.valueOf(it.detailLevel), it.lastIntent, it.lastActiveEntityId, it.lastFactsSnapshot
            )
        } ?: ConversationState(conversationId)

        val (reply, updatedState) = coordinator.processOffline(userMessage, context, currentState)
        
        stateDao.upsert(ConversationStateEntity(
            updatedState.conversationId, updatedState.activeTopic, updatedState.activeDecisionId,
            updatedState.languagePreference, updatedState.detailLevel.name,
            updatedState.lastIntent, updatedState.lastActiveEntityId,
            updatedState.lastFactsSnapshot, Instant.now().toString()
        ))

        reply
    }
}
