package com.pranav.drsti.ai

/**
 * Extracts specialized entities from user messages without a heavy LLM.
 * Uses token context, regex, and proximity rules for high accuracy in offline mode.
 */
object NativeEntityExtractor {

    fun extract(message: String): ExtractedContext {
        val m = message.lowercase()
        return ExtractedContext(
            options = extractOptions(m),
            amount = extractAmount(m),
            dateHint = extractDateHint(m)
        )
    }

    private fun extractOptions(m: String): List<String> {
        val options = mutableListOf<String>()
        // Pattern: "A or B"
        if (" or " in m) {
            val parts = m.split(" or ")
            parts.forEach { part ->
                val words = part.trim().split(" ")
                if (words.isNotEmpty()) {
                    options.add(words.takeLast(2).joinToString(" "))
                }
            }
        }
        return options.distinct()
    }

    private fun extractAmount(m: String): String? {
        val regex = Regex("\\d+\\s*(lpa|cr|k|cr|lakh)")
        return regex.find(m)?.value
    }

    private fun extractDateHint(m: String): String? {
        return when {
            "today" in m -> "today"
            "tomorrow" in m -> "tomorrow"
            "next month" in m -> "next month"
            "next week" in m -> "next week"
            else -> null
        }
    }

    data class ExtractedContext(
        val options: List<String>,
        val amount: String?,
        val dateHint: String?
    )
}
