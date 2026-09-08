package com.pranav.drsti.model

import kotlinx.serialization.Serializable

/**
 * Persisted state for an ongoing conversation to support follow-ups and user preferences offline.
 */
@Serializable
data class ConversationState(
    val conversationId: Long,
    val activeTopic: String? = null,
    val activeDecisionId: Long? = null,
    val languagePreference: String = "en",
    val detailLevel: DetailLevel = DetailLevel.SUMMARY,
    val lastFactsSnapshot: String? = null // JSON snapshot of relevant Jyotish facts for follow-ups
)

@Serializable
enum class DetailLevel {
    SUMMARY, ELABORATE
}
