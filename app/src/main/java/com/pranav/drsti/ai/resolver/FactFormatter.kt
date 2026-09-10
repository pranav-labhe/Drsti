package com.pranav.drsti.ai.resolver

import com.pranav.drsti.ai.provider.AstroInterpretationRenderer
import com.pranav.drsti.model.*

object FactFormatter {
    private val translationMap = mapOf(
        "Mahadasha" to "Major Period",
        "Antardasha" to "Sub-period",
        "Rashi" to "Moon sign",
        "Lagna" to "Ascendant",
        "Tithi" to "lunar day",
        "Nakshatra" to "star constellation"
    )

    private val semanticMap = mapOf(
        "Sun" to "Soul Purpose",
        "Moon" to "Emotions",
        "Mars" to "Ambition",
        "Mercury" to "Communication",
        "Jupiter" to "Wisdom",
        "Venus" to "Creativity",
        "Saturn" to "Structure",
        "Rahu" to "Desires",
        "Ketu" to "Spirituality",
        "Major Period" to "Major Life Chapter",
        "Sub-period" to "Secondary Timing",
        "10th House" to "Career",
        "7th House" to "Relationships",
        "1st House" to "Self",
        "4th House" to "Home",
        "Ascendant" to "Public Persona",
        "Moon sign" to "Inner Nature",
        "Pratipada" to "the first day of the lunar cycle",
        "Shukla Paksha" to "the waxing moon phase of growth",
        "Krishna Paksha" to "the waning moon phase of reflection",
        "Purva Phalguni" to "the star of creativity and rest",
        "Indra Yoga" to "the alignment for leadership and power"
    )

    /** 
     * Synthesizes technical terms using the <XYZ / Meaning> format.
     */
    fun synthesize(text: String): String {
        var result = text
        // 1. First cleanup Sanskrit
        translationMap.keys.forEach { sanskrit ->
            result = result.replace(Regex("(?i)\\b$sanskrit\\b"), "")
        }
        
        // 2. Wrap with < > as requested
        semanticMap.forEach { (term, meaning) ->
            val pattern = Regex("(?i)\\b$term\\b")
            if (pattern.containsMatchIn(result)) {
                result = result.replace(pattern, "<XYZ: $term / Meaning: $meaning>")
            }
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    fun format(concepts: Set<String>, context: AiRequestContext): List<String> {
        val facts = mutableListOf<String>()
        for (concept in concepts) {
            val raw = AstroInterpretationRenderer.renderFactsFromTags(concept, context)
            facts.addAll(splitToSentences(raw))
        }
        
        val plainFacts = facts.map { synthesize(it) }.distinct()
        return enforceBudget(plainFacts, 120)
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
            val tokens = s.split(" ").size * 1.5
            if (currentTokens + tokens <= maxTokens) {
                result.add("$s.")
                currentTokens += tokens
            }
        }
        return result
    }
}
