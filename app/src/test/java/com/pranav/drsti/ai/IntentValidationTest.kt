package com.pranav.drsti.ai

import com.pranav.drsti.model.ConversationState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Benchmark for offline intent recognition accuracy.
 * Verifies handling of "out of domain" queries and domain-specific life decisions.
 */
class IntentValidationTest {

    @Test
    fun `distinguish life decisions from IoT commands`() {
        val state = ConversationState(conversationId = 1L)
        
        // IoT / Out of domain should be GENERAL or UNKNOWN (not DECISION)
        assertEquals(ResolvedIntent.GENERAL_CHAT, IntentResolver.resolve("switch off the light", state))
        assertEquals(ResolvedIntent.GENERAL_CHAT, IntentResolver.resolve("play some music", state))
        
        // Life Decisions
        assertEquals(ResolvedIntent.DECISION_START, IntentResolver.resolve("should i join google or microsoft", state))
        assertEquals(ResolvedIntent.DECISION_START, IntentResolver.resolve("is it a good time to switch job", state))
        
        // Astrology Queries
        assertEquals(ResolvedIntent.ASTROLOGY_QUERY, IntentResolver.resolve("what is my current mahadasha", state))
        assertEquals(ResolvedIntent.ASTROLOGY_QUERY, IntentResolver.resolve("show my kundali", state))
    }
}
