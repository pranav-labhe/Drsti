package com.pranav.drsti.ai

import com.pranav.drsti.model.ConversationState

enum class ResolvedIntent {
    GENERAL_CHAT,
    ASTROLOGY_QUERY,
    DECISION_START,
    DECISION_UPDATE,
    FOLLOW_UP,
    ELABORATE,
    TRANSLATE,
    UNKNOWN
}

/**
 * Scoring-based intent resolver to replace brittle keyword matching.
 * Provides semantic-like accuracy using deterministic logic and negative signals.
 */
object IntentResolver {
    fun resolve(message: String, state: ConversationState): ResolvedIntent {
        val m = message.lowercase()
        
        // 1. Precise Interactive Intents (highest priority)
        if (isElaborate(m)) return ResolvedIntent.ELABORATE
        if (isTranslate(m)) return ResolvedIntent.TRANSLATE
        
        // 2. Astrology Query Scoring
        val astroScore = calculateAstroScore(m)
        
        // 3. Decision Scoring
        val decisionScore = calculateDecisionScore(m)
        
        // 4. Contextual Boosts
        val hasActiveContext = state.activeDecisionId != null || state.activeTopic != null
        val contextBoost = if (hasActiveContext) 25 else 0
        
        // 5. Final Selection Logic
        return when {
            decisionScore > 45 -> {
                if (state.activeDecisionId != null) ResolvedIntent.DECISION_UPDATE 
                else ResolvedIntent.DECISION_START
            }
            isFollowUp(m, state) -> ResolvedIntent.FOLLOW_UP
            decisionScore + contextBoost > 50 -> ResolvedIntent.DECISION_UPDATE
            astroScore > 35 -> ResolvedIntent.ASTROLOGY_QUERY
            else -> ResolvedIntent.GENERAL_CHAT
        }
    }

    private fun isElaborate(m: String) = 
        "elaborate" in m || "explain more" in m || "detail" in m || "विस्तार" in m || "सांग अजून" in m

    private fun isTranslate(m: String) = 
        "translate" in m || "speak in" in m || "in hindi" in m || "in marathi" in m || 
        "हिंदीत" in m || "मराठीत" in m || "भाषा" in m

    private fun calculateAstroScore(m: String): Int {
        var score = 0
        if ("panchang" in m || "tithi" in m || "today" in m) score += 30
        if ("kundali" in m || "chart" in m || "lagna" in m || "birth" in m) score += 40
        if ("dasha" in m || "bhukti" in m || "period" in m) score += 40
        if ("transit" in m || "gochar" in m || "movement" in m) score += 40
        if ("jupiter" in m || "saturn" in m || "mars" in m || "venus" in m || "mercury" in m || "moon" in m || "sun" in m) score += 20
        return score
    }

    private fun calculateDecisionScore(m: String): Int {
        var score = 0
        // Positive Signals
        if ("should i" in m || "should we" in m || "could i" in m) score += 50
        if ("decision" in m || "choose" in m || "pick" in m || "select" in m || "निवड" in m) score += 40
        if ("between" in m && (" and " in m || " or " in m)) score += 30
        if ("option" in m || "path" in m || "choice" in m) score += 25
        if ("offer" in m || "joining" in m || "switch" in m || "leave" in m) score += 25
        
        // Contextual "or" handling
        if (" or " in m) {
            val isAstroCompare = m.contains(Regex("(jupiter|saturn|mars|venus|mercury|moon|sun).*or.*(jupiter|saturn|mars|venus|mercury|moon|sun)"))
            if (isAstroCompare) {
                score -= 40 // Heavy penalty for comparing planets - likely an astro query
            } else {
                score += 20 // Standard "or" is usually a decision signal
            }
        }
        
        return score
    }
    
    private fun isFollowUp(m: String, state: ConversationState): Boolean {
        if (state.activeTopic == null && state.activeDecisionId == null) return false
        val followUpKeywords = listOf(
            "that", " it ", "this", "first one", "second one", "other one", 
            "financially", "next month", "how about", "what if"
        )
        return followUpKeywords.any { it in m }
    }
}
