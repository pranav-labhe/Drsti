package com.pranav.drsti.ai.provider

import com.pranav.drsti.ai.NativeIntelligenceCoordinator
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
    private val stateDao: ConversationStateDao
) : AiAstrologyService by base {

    private val coordinator = NativeIntelligenceCoordinator(context)

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis = withContext(Dispatchers.Default) {
        val dasha = context.dasha
        val lang = "en" 

        val options = request.options.mapIndexed { idx, opt ->
            val summary = VedicInferenceEngine.synthesize(context, DetailLevel.SUMMARY, lang)
            val seed = (HashUtil.sha256(opt.description + (dasha?.currentMahadasha?.planet?.name ?: "")).take(6).sumOf { it.code })
            val baseScore = 50 + (seed % 30)
            val boost = if (opt.description.lowercase().contains("wait") || opt.description.lowercase().contains("later")) -10 else 5
            val astro = (baseScore + boost).coerceIn(15, 95)
            val timing = (baseScore + (idx * 2)).coerceIn(15, 95)
            
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
                strengths = listOfNotNull(
                    if (astro >= 65) "The current planetary configuration provides robust structural support for this path." else null,
                    if (dasha?.currentAntardasha?.planet == PlanetName.JUPITER) "Jupiter's sub-period favors growth and expansion." else null
                ),
                concerns = listOfNotNull(
                    if (astro < 45) "Fewer supporting factors; this path may require significantly more effort." else null,
                    if (context.planetaryPositions?.positions?.any { it.planet == PlanetName.SATURN && it.isRetrograde } == true) "Saturn's retrograde suggests a need to review long-term commitments first." else null
                ),
                supportingFactors = listOf("Favorable Dasha Lord influence", "Supportive house placement"),
                contradictingFactors = emptyList(),
                explanation = "Based on local Vedic laws, \"${opt.description}\" shows $strength astrological support. $summary"
            )
        }

        DecisionAnalysis(
            analysisSummary = "Compared ${options.size} path(s) using native Vedic interpretation laws.",
            options = options,
            preferredOptionId = options.maxByOrNull { it.astrologicalSupport + it.timingSupport }?.id,
            confidence = if (context.kundali != null && context.dasha != null) "medium" else "low",
            caveats = listOf("Derived from local expert rules; for guidance only."),
            provenance = Provenance(
                calculationVersion = "astrocalc-1.0", promptVersion = "decision-v1", model = "native",
                generatedAt = java.time.Instant.now().toString(), source = "NativeExpertSystem",
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
                DetailLevel.valueOf(it.detailLevel), it.lastFactsSnapshot
            )
        } ?: ConversationState(conversationId)

        val (reply, updatedState) = coordinator.processOffline(userMessage, context, currentState)
        
        stateDao.upsert(ConversationStateEntity(
            updatedState.conversationId, updatedState.activeTopic, updatedState.activeDecisionId,
            updatedState.languagePreference, updatedState.detailLevel.name,
            updatedState.lastFactsSnapshot, Instant.now().toString()
        ))

        reply
    }
}
