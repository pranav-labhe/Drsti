package com.pranav.drsti.model

import kotlinx.serialization.Serializable

@Serializable
enum class FindingCategory {
    THEME, PLACEMENT, AXIS, TRANSIT, YOGA
}

@Serializable
data class JyotishFinding(
    val category: FindingCategory,
    val key: String,
    val value: String? = null
)

@Serializable
enum class DetailLevel {
    CONCISE, ELABORATE
}
