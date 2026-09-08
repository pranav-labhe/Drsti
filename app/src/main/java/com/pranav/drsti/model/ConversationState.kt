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
    val lastIntent: String? = null,
    val lastActiveEntityId: String? = null, // e.g. "option_A"
    val lastFactsSnapshot: String? = null // Reference to key facts for follow-ups
)

@Serializable
enum class DetailLevel {
    SUMMARY, ELABORATE
}
