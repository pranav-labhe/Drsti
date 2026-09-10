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

        // 1. Baseline facts
        val baseline = mutableListOf<String>()
        context.kundali?.let { k ->
            val moonSign = k.planets.find { it.planet == PlanetName.MOON }?.sign?.displayName ?: "Unknown"
            baseline.add("Your Moon sign is $moonSign.")
            baseline.add("Your Ascendant is ${k.ascendant.sign.displayName}.")
        }
        context.dasha?.let { d ->
            d.currentMahadasha?.let { m ->
                baseline.add("You are in ${m.planet.name} major period.")
            }
            d.currentAntardasha?.let { a ->
                baseline.add("You are in ${a.planet.name} sub-period.")
            }
        }
        
        val baselineTranslated = baseline.map { translate(it) }
        val limitedBaseline = enforceBudget(baselineTranslated, 60)
        facts.addAll(limitedBaseline)

        // 2. Conditional facts
        val conditional = mutableListOf<String>()
        // Prioritize detected concepts
        for (concept in concepts) {
            val raw = AstroInterpretationRenderer.renderFactsFromTags(concept, context)
            conditional.addAll(splitToSentences(raw))
        }

        // Add some variety if we have space and it's relevant (e.g. VIBE if not detected)
        if (concepts.isEmpty()) {
            val raw = AstroInterpretationRenderer.renderFactsFromTags("VIBE", context)
            conditional.addAll(splitToSentences(raw))
        }
        
        val conditionalTranslated = conditional.map { translate(it) }.distinct()
        // Deduplicate with baseline
        val uniqueConditional = conditionalTranslated.filter { cond -> 
            facts.none { baselineFact -> baselineFact.equals(cond, true) } 
        }
        
        val limitedConditional = enforceBudget(uniqueConditional, 45)
        facts.addAll(limitedConditional)

        return facts
    }

    private fun translate(text: String): String {
        var result = text
        translationMap.forEach { (sanskrit, english) ->
            // Use word boundary to avoid partial matches like "SUN" in "SUNDAY" if it was there
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
        return words * 1.3
    }
}
