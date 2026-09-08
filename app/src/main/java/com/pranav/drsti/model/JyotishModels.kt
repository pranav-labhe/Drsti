package com.pranav.drsti.model

/**
 * Structured finding derived from local Jyotish laws.
 */
data class JyotishFinding(
    val category: FindingCategory,
    val intensity: Int, // 1-100
    val supportDelta: Int,
    val key: String, // Unique key for localization lookup
    val params: Map<String, String> = emptyMap()
)

enum class FindingCategory {
    THEME, PLACEMENT, AXIS, YOGA, TRANSIT
}
