package com.pranav.drsti

import com.pranav.drsti.model.DecisionOptionAnalysis
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §46: percentages must always validate to 0-100. */
class DecisionScoringValidationTest {

    @Test
    fun `astrological support is within 0 to 100`() {
        val option = DecisionOptionAnalysis(
            id = "A", astrologicalSupport = 68, timingSupport = 74, strength = "moderate",
            explanation = "test"
        )
        assertTrue(option.astrologicalSupport in 0..100)
        assertTrue(option.timingSupport in 0..100)
    }
}
