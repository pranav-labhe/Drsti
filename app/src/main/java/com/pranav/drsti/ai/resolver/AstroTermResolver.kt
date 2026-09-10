package com.pranav.drsti.ai.resolver

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class IntentFrame(
    val frame: String,
    val phrases: List<String>,
    val concept: String
)

@Serializable
data class Glossary(
    val IDENTITY: List<String> = emptyList(),
    val CAREER: List<String> = emptyList(),
    val ROMANCE: List<String> = emptyList(),
    val TIMING: List<String> = emptyList(),
    val VIBE: List<String> = emptyList(),
    val _intent_frames: List<IntentFrame> = emptyList()
)

object AstroTermResolver {
    private const val TAG = "AstroTermResolver"
    private var glossary: Glossary? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun initialize(context: Context) {
        if (glossary != null) return
        try {
            val jsonString = context.assets.open("astro_glossary.json").bufferedReader().use { it.readText() }
            glossary = json.decodeFromString(Glossary.serializer(), jsonString)
            Log.d(TAG, "Glossary loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load glossary", e)
        }
    }

    fun resolve(userMessage: String): Set<String> {
        val currentGlossary = glossary ?: return emptySet()
        
        // 2. Normalize input
        val normalized = normalize(userMessage)
        if (normalized.isBlank()) return emptySet()

        val tokens = normalized.split(Regex("\\s+"))
        val matchedConcepts = mutableSetOf<String>()

        // 3. Tier 1 — exact term match
        checkTier1(normalized, currentGlossary, matchedConcepts)
        if (matchedConcepts.isNotEmpty()) return matchedConcepts

        // 4. Tier 2 — intent frames
        checkTier2(normalized, currentGlossary, matchedConcepts)
        if (matchedConcepts.isNotEmpty()) return matchedConcepts

        // 6. Optional fuzzy pass
        checkFuzzy(tokens, currentGlossary, matchedConcepts)
        
        return matchedConcepts
    }

    private fun normalize(input: String): String {
        val clamped = input.take(200).lowercase()
        // Strip punctuation but keep Devanagari (\u0900-\u097F) and spaces
        return clamped.replace(Regex("[^a-z0-9\\s\\u0900-\\u097F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun checkTier1(normalized: String, glossary: Glossary, results: MutableSet<String>) {
        val maps = mapOf(
            "IDENTITY" to glossary.IDENTITY,
            "CAREER" to glossary.CAREER,
            "ROMANCE" to glossary.ROMANCE,
            "TIMING" to glossary.TIMING,
            "VIBE" to glossary.VIBE
        )

        for ((concept, terms) in maps) {
            for (term in terms) {
                if (containsPhrase(normalized, term.lowercase())) {
                    results.add(concept)
                    break
                }
            }
        }
    }

    private fun checkTier2(normalized: String, glossary: Glossary, results: MutableSet<String>) {
        for (frame in glossary._intent_frames) {
            for (phrase in frame.phrases) {
                if (containsPhrase(normalized, phrase.lowercase())) {
                    results.add(frame.concept)
                    break
                }
            }
        }
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        // Match whole phrase on word boundaries
        val escaped = Regex.escape(phrase)
        val pattern = Regex("(^|\\s)$escaped($|\\s)")
        return pattern.containsMatchIn(text)
    }

    private fun checkFuzzy(tokens: List<String>, glossary: Glossary, results: MutableSet<String>) {
        val allTerms = mapOf(
            "IDENTITY" to glossary.IDENTITY,
            "CAREER" to glossary.CAREER,
            "ROMANCE" to glossary.ROMANCE,
            "TIMING" to glossary.TIMING,
            "VIBE" to glossary.VIBE
        )

        for (token in tokens) {
            if (token.length < 5) continue
            for ((concept, terms) in allTerms) {
                for (term in terms) {
                    val normalizedTerm = term.lowercase()
                    if (normalizedTerm.length >= 5 && levenshteinDistance(token, normalizedTerm) <= 1) {
                        results.add(concept)
                        break
                    }
                }
            }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev
                else 1 + minOf(prev, minOf(dp[j - 1], dp[j]))
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
