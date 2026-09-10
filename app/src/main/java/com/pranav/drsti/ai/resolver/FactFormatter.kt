package com.pranav.drsti.ai.resolver

import com.pranav.drsti.ai.provider.AstroInterpretationRenderer
import com.pranav.drsti.model.*

object FactFormatter {
    private val translationMap = mapOf(
        "SUN" to "Sun", "MOON" to "Moon", "MARS" to "Mars", "MERCURY" to "Mercury",
        "JUPITER" to "Jupiter", "VENUS" to "Venus", "SATURN" to "Saturn", "RAHU" to "Rahu", "KETU" to "Ketu",
        "Mahadasha" to "major period",
        "Antardasha" to "sub-period",
        "Rashi" to "Moon sign",
        "Lagna" to "Ascendant",
        "Tithi" to "lunar day",
        "Nakshatra" to "star constellation",
        "Vedic Identity" to "Astrological Identity",
        "Current Cosmic Cycle" to "Current Cycle"
    )

    fun format(concepts: Set<String>, context: AiRequestContext): List<String> {
        val facts = mutableListOf<String>()

        // 1. Conditional facts (Priority)
        val conditional = mutableListOf<String>()
        for (concept in concepts) {
            val raw = AstroInterpretationRenderer.renderFactsFromTags(concept, context)
            conditional.addAll(splitToSentences(raw))
        }
        
        val conditionalTranslated = conditional.map { translate(it) }.distinct()
        val limitedConditional = enforceBudget(conditionalTranslated, 65) // Priority given to user query
        facts.addAll(limitedConditional)

        // 2. Baseline facts (Fallback/Supplementary)
        // Use renderer for baseline too to ensure deduplication
        val baselineRaw = AstroInterpretationRenderer.renderFactsFromTags("IDENTITY,TIMING,VIBE", context)
        val baseline = splitToSentences(baselineRaw).map { translate(it) }.distinct()
        
        // Deduplicate against already added facts
        val uniqueBaseline = baseline.filter { b -> 
            facts.none { existing -> existing.equals(b, true) } 
        }
        
        val limitedBaseline = enforceBudget(uniqueBaseline, 35) // Total budget is ~100 tokens (105 for safety)
        facts.addAll(limitedBaseline)

        return facts
    }

    private fun translate(text: String): String {
        var result = text
        translationMap.forEach { (sanskrit, english) ->
            result = result.replace(Regex("(?i)\\b$sanskrit\\b"), english)
        }
        return result
    }

    private fun splitToSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim().removeSuffix(".") }
            .filter { it.isNotBlank() && it.length > 5 }
    }

    private fun enforceBudget(sentences: List<String>, maxTokens: Int): List<String> {
        val result = mutableListOf<String>()
        var currentTokens = 0.0
        for (s in sentences) {
            val tokens = estimateTokens(s)
            // Each fact sentence: ≤15 tokens
            if (tokens <= 15.0 && currentTokens + tokens <= maxTokens) {
                result.add("$s.")
                currentTokens += tokens
            }
        }
        return result
    }

    private fun estimateTokens(text: String): Double {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return words * 1.7 // Safety ratio P2.2
    }
}
