package com.pranav.drsti.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Room entities. Kept in one file to match the requested project layout.
 * Conceptually grouped exactly like spec §38: DATA entities (Person, Place,
 * PlanetaryPosition) stay separate from AI OUTPUT entities (KundaliEntity,
 * PanchangEntity, DecisionAnalysisEntity, ...). Every AI-output entity
 * carries its own provenance columns (schemaVersion/model/promptVersion/
 * hashes) instead of one shared "everything" table (spec §38).
 */

@Entity(tableName = "person")
@Serializable
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateOfBirthIso: String,
    val timeOfBirthIso: String,
    val birthTimeUncertaintyMinutes: Int? = null,
    val placeId: Long?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val gender: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "place")
@Serializable
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val country: String = "India",
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val aliases: String? = null, // comma separated
    val isUserCreated: Boolean = false
)

@Entity(tableName = "conversation")
@Serializable
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "conversation_message")
@Serializable
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val intent: String? = null,
    val contextSnapshotRef: String? = null,
    val timestamp: String
)

@Entity(tableName = "kundali")
@Serializable
data class KundaliEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val dataJson: String, // serialized KundaliData
    val calculationVersion: String,
    val generatedAt: String
)

@Entity(tableName = "dasha")
@Serializable
data class DashaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val dataJson: String, // serialized DashaData
    val calculationVersion: String,
    val generatedAt: String
)

@Entity(tableName = "panchang")
@Serializable
data class PanchangEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cacheKey: String, // date+lat+lon+tz+systemVersion+calcVersion
    val dateIso: String,
    val latitude: Double,
    val longitude: Double,
    val dataJson: String, // serialized PanchangData
    val generatedAt: String
)

@Entity(tableName = "planetary_position")
@Serializable
data class PlanetaryPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: String,
    val latitude: Double,
    val longitude: Double,
    val dataJson: String, // serialized PlanetaryPositionResult
    val fetchedAt: String
)

@Entity(tableName = "transit_analysis")
@Serializable
data class TransitAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val dataJson: String, // serialized TransitAnalysisResult
    val generatedAt: String
)

@Entity(tableName = "decision")
@Serializable
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val conversationId: Long?,
    val question: String,
    val optionsJson: String, // serialized List<DecisionOptionInput>
    val context: String? = null,
    val desiredDecisionDateIso: String? = null,
    val status: String, // OPEN | DECIDED | OUTCOME_PENDING | COMPLETED
    val selectedOptionId: String? = null,
    val selectionTimestamp: String? = null,
    val createdAt: String
)

/** Immutable pre-decision analysis snapshot. Never updated after creation (spec §33). */
@Entity(tableName = "decision_analysis")
@Serializable
data class DecisionAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long,
    val analysisJson: String, // serialized DecisionAnalysis
    val natalSnapshotJson: String?,
    val dashaSnapshotJson: String?,
    val panchangSnapshotJson: String?,
    val planetarySnapshotJson: String?,
    val promptVersion: String,
    val model: String,
    val inputHash: String,
    val outputHash: String,
    val analysisTimestamp: String
)

@Entity(tableName = "decision_outcome")
@Serializable
data class DecisionOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long,
    val description: String,
    val occurredAtIso: String,
    val selectedOptionId: String?,
    val userAssessment: String,
    val notes: String? = null,
    val recordedAt: String
)

@Entity(tableName = "outcome_analysis")
@Serializable
data class OutcomeAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long,
    val analysisJson: String, // serialized OutcomeAnalysis
    val generatedAt: String
)

@Entity(tableName = "ai_request_log")
@Serializable
data class AIRequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long? = null,
    val interactionId: String? = null,
    val previousInteractionId: String? = null,
    val requestType: String,
    val timestamp: String,
    val model: String,
    val promptVersion: String,
    val inputHash: String,
    val outputHash: String?,
    val success: Boolean,
    val error: String? = null,
    val latencyMs: Long
)

@Entity(tableName = "app_settings")
@Serializable
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "conversation_state")
@Serializable
data class ConversationStateEntity(
    @PrimaryKey val conversationId: Long,
    val activeTopic: String?,
    val activeDecisionId: Long?,
    val languagePreference: String,
    val detailLevel: String,
    val lastFactsSnapshot: String?,
    val updatedAt: String
)

@Entity(tableName = "calibration_stats")
@Serializable
data class CalibrationStatsEntity(
    @PrimaryKey val id: Int = 1, // Only one row for global stats
    val decisionsAnalyzed: Int,
    val outcomesRecorded: Int,
    val directionallyCorrect: Int,
    val overconfidenceCount: Int,
    val underconfidenceCount: Int,
    val updatedAt: String
)

