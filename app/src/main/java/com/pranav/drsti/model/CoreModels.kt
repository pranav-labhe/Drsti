package com.pranav.drsti.model

import kotlinx.serialization.Serializable

/*
 * These are the domain/DTO models shared across the AI provider, the
 * repositories and the UI layer. They are kept deliberately separate from
 * the Room @Entity classes (see database/entity/Entities.kt) so that raw
 * DATA (birth details, computed positions) is never confused with AI
 * OUTPUT (interpretation, decision analysis) — see spec §61.
 */

// ---------- Astrology system configuration (spec §13) ----------

@Serializable
data class AstrologySystemConfig(
    val zodiac: String = "SIDEREAL",
    val ayanamsha: String = "LAHIRI",
    val houseSystem: String = "WHOLE_SIGN",
    val dashaSystem: String = "VIMSHOTTARI"
)

@Serializable
enum class PlanetName {
    SUN, MOON, MARS, MERCURY, JUPITER, VENUS, SATURN, RAHU, KETU;

    fun displayName(): String = when (this) {
        SUN -> "Sun (Surya)"
        MOON -> "Moon (Chandra)"
        MARS -> "Mars (Mangala)"
        MERCURY -> "Mercury (Budha)"
        JUPITER -> "Jupiter (Guru)"
        VENUS -> "Venus (Shukra)"
        SATURN -> "Saturn (Shani)"
        RAHU -> "Rahu"
        KETU -> "Ketu"
    }
}

@Serializable
enum class ZodiacSign(val index: Int, val displayName: String, val lord: PlanetName) {
    ARIES(0, "Aries", PlanetName.MARS),
    TAURUS(1, "Taurus", PlanetName.VENUS),
    GEMINI(2, "Gemini", PlanetName.MERCURY),
    CANCER(3, "Cancer", PlanetName.MOON),
    LEO(4, "Leo", PlanetName.SUN),
    VIRGO(5, "Virgo", PlanetName.MERCURY),
    LIBRA(6, "Libra", PlanetName.VENUS),
    SCORPIO(7, "Scorpio", PlanetName.MARS),
    SAGITTARIUS(8, "Sagittarius", PlanetName.JUPITER),
    CAPRICORN(9, "Capricorn", PlanetName.SATURN),
    AQUARIUS(10, "Aquarius", PlanetName.SATURN),
    PISCES(11, "Pisces", PlanetName.JUPITER);

    companion object {
        fun fromSiderealLongitude(longitude: Double): ZodiacSign {
            val norm = ((longitude % 360.0) + 360.0) % 360.0
            return entries[(norm / 30.0).toInt().coerceIn(0, 11)]
        }
    }

    fun degreeWithin(longitude: Double): Double {
        val norm = ((longitude % 360.0) + 360.0) % 360.0
        return norm - (index * 30.0)
    }
}

@Serializable
enum class Nakshatra(val index: Int, val displayName: String, val dashaLord: PlanetName) {
    ASHWINI(0, "Ashwini", PlanetName.KETU),
    BHARANI(1, "Bharani", PlanetName.VENUS),
    KRITTIKA(2, "Krittika", PlanetName.SUN),
    ROHINI(3, "Rohini", PlanetName.MOON),
    MRIGASHIRA(4, "Mrigashira", PlanetName.MARS),
    ARDRA(5, "Ardra", PlanetName.RAHU),
    PUNARVASU(6, "Punarvasu", PlanetName.JUPITER),
    PUSHYA(7, "Pushya", PlanetName.SATURN),
    ASHLESHA(8, "Ashlesha", PlanetName.MERCURY),
    MAGHA(9, "Magha", PlanetName.KETU),
    PURVA_PHALGUNI(10, "Purva Phalguni", PlanetName.VENUS),
    UTTARA_PHALGUNI(11, "Uttara Phalguni", PlanetName.SUN),
    HASTA(12, "Hasta", PlanetName.MOON),
    CHITRA(13, "Chitra", PlanetName.MARS),
    SWATI(14, "Swati", PlanetName.RAHU),
    VISHAKHA(15, "Vishakha", PlanetName.JUPITER),
    ANURADHA(16, "Anuradha", PlanetName.SATURN),
    JYESHTHA(17, "Jyeshtha", PlanetName.MERCURY),
    MULA(18, "Mula", PlanetName.KETU),
    PURVA_ASHADHA(19, "Purva Ashadha", PlanetName.VENUS),
    UTTARA_ASHADHA(20, "Uttara Ashadha", PlanetName.SUN),
    SHRAVANA(21, "Shravana", PlanetName.MOON),
    DHANISHTA(22, "Dhanishta", PlanetName.MARS),
    SHATABHISHA(23, "Shatabhisha", PlanetName.RAHU),
    PURVA_BHADRAPADA(24, "Purva Bhadrapada", PlanetName.JUPITER),
    UTTARA_BHADRAPADA(25, "Uttara Bhadrapada", PlanetName.SATURN),
    REVATI(26, "Revati", PlanetName.MERCURY);

    companion object {
        const val SPAN_DEGREES = 360.0 / 27.0

        fun fromSiderealLongitude(longitude: Double): Nakshatra {
            val norm = ((longitude % 360.0) + 360.0) % 360.0
            return entries[(norm / SPAN_DEGREES).toInt().coerceIn(0, 26)]
        }

        fun padaFor(longitude: Double): Int {
            val norm = ((longitude % 360.0) + 360.0) % 360.0
            val posInNakshatra = norm % SPAN_DEGREES
            return (posInNakshatra / (SPAN_DEGREES / 4.0)).toInt().coerceIn(0, 3) + 1
        }

        fun fractionElapsed(longitude: Double): Double {
            val norm = ((longitude % 360.0) + 360.0) % 360.0
            return (norm % SPAN_DEGREES) / SPAN_DEGREES
        }
    }
}

// ---------- Provenance (spec §37) ----------

@Serializable
data class Provenance(
    val schemaVersion: String = "1.0",
    val calculationVersion: String,
    val promptVersion: String? = null,
    val model: String? = null,
    val generatedAt: String,
    val source: String,
    val sourceVersion: String,
    val inputHash: String,
    val outputHash: String
)

// ---------- Raw astronomical data (never AI-fabricated, spec §10) ----------

@Serializable
data class PlanetPosition(
    val planet: PlanetName,
    val tropicalLongitude: Double,
    val siderealLongitude: Double,
    val isRetrograde: Boolean,
    val sign: ZodiacSign,
    val degreeInSign: Double,
    val nakshatra: Nakshatra,
    val pada: Int
)

@Serializable
data class PlanetaryPositionResult(
    val timestampUtc: String,
    val latitude: Double,
    val longitude: Double,
    val ayanamshaDegrees: Double,
    val positions: List<PlanetPosition>,
    val provenance: Provenance
)

// ---------- Kundali (spec §18) ----------

@Serializable
data class AscendantInfo(
    val siderealLongitude: Double,
    val sign: ZodiacSign,
    val degreeInSign: Double,
    val nakshatra: Nakshatra,
    val pada: Int
)

@Serializable
data class KundaliPlanet(
    val planet: PlanetName,
    val sign: ZodiacSign,
    val degreeInSign: Double,
    val house: Int,
    val nakshatra: Nakshatra,
    val pada: Int,
    val retrograde: Boolean
)

@Serializable
data class KundaliData(
    val schemaVersion: String = "1.0",
    val system: String = "Vedic",
    val zodiac: String = "Sidereal",
    val ayanamsha: String = "Lahiri",
    val houseSystem: String = "Whole Sign",
    val ascendant: AscendantInfo,
    val planets: List<KundaliPlanet>,
    val provenance: Provenance
)

// ---------- Vimshottari Dasha (spec §24) ----------

@Serializable
data class DashaPeriod(
    val level: String, // mahadasha | antardasha | pratyantardasha
    val planet: PlanetName,
    val startDateIso: String,
    val endDateIso: String,
    val parentPlanet: PlanetName? = null
)

@Serializable
data class DashaData(
    val schemaVersion: String = "1.0",
    val system: String = "Vimshottari",
    val birthNakshatra: Nakshatra,
    val mahadashas: List<DashaPeriod>,
    val currentMahadasha: DashaPeriod?,
    val currentAntardasha: DashaPeriod?,
    val provenance: Provenance
)

// ---------- Panchang (spec §20-22) ----------

@Serializable
data class PanchangData(
    val schemaVersion: String = "1.0",
    val dateIso: String,
    val vara: String,
    val tithiName: String,
    val tithiNumber: Int,
    val paksha: String,
    val nakshatra: Nakshatra,
    val yogaName: String,
    val karanaName: String,
    val sunriseLocal: String?,
    val sunsetLocal: String?,
    val rahuKalam: String?,
    val yamaganda: String?,
    val gulikaKalam: String?,
    val abhijitMuhurta: String?,
    val calculationNotes: String? = null,
    val provenance: Provenance
)

// ---------- Transit (spec §25-26) ----------

@Serializable
data class TransitHighlight(
    val transitPlanet: PlanetName,
    val natalPlanet: PlanetName?,
    val relationship: String,
    val note: String
)

@Serializable
data class TransitAnalysisResult(
    val summary: String,
    val highlights: List<TransitHighlight>,
    val favorableThemes: List<String>,
    val cautionThemes: List<String>,
    val provenance: Provenance
)

// ---------- Decision AI (spec §27-31) ----------

@Serializable
data class DecisionOptionInput(
    val id: String,
    val description: String
)

@Serializable
data class DecisionRequest(
    val question: String,
    val options: List<DecisionOptionInput>,
    val desiredDecisionDateIso: String? = null,
    val context: String? = null
)

@Serializable
data class DecisionOptionAnalysis(
    val id: String,
    val astrologicalSupport: Int, // 0-100, AI-estimated indicator, NOT a probability
    val timingSupport: Int,
    val strength: String, // very weak | weak | moderate | strong | very strong | exceptional
    val strengths: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val supportingFactors: List<String> = emptyList(),
    val contradictingFactors: List<String> = emptyList(),
    val explanation: String
)

@Serializable
data class DecisionAnalysis(
    val analysisSummary: String,
    val options: List<DecisionOptionAnalysis>,
    val preferredOptionId: String?,
    val confidence: String, // low | medium | high
    val caveats: List<String> = emptyList(),
    val provenance: Provenance
)

// ---------- Outcome AI (spec §35-36) ----------

@Serializable
data class OutcomeInput(
    val description: String,
    val occurredAtIso: String,
    val selectedOptionId: String?,
    val userAssessment: String, // e.g. "better than expected" / "as expected" / "worse than expected"
    val notes: String? = null
)

@Serializable
data class OutcomeAnalysis(
    val alignedIndicators: List<String>,
    val misalignedIndicators: List<String>,
    val timingUsefulness: String,
    val scoreCalibrationNote: String,
    val learnings: List<String>,
    val provenance: Provenance
)

@Serializable
data class CalibrationStats(
    val decisionsAnalyzed: Int,
    val outcomesRecorded: Int,
    val directionallyCorrect: Int,
    val overconfidenceCount: Int,
    val underconfidenceCount: Int
)

// ---------- Chat (spec §4-7, §47) ----------

@Serializable
enum class ChatIntent {
    GENERAL_CHAT, PANCHANG_QUERY, KUNDALI_QUERY, DASHA_QUERY, TRANSIT_QUERY,
    DECISION_ANALYSIS, TIMING_COMPARISON, DECISION_RECORD, OUTCOME_RECORD,
    OUTCOME_ANALYSIS, PROFILE_UPDATE, NAVIGATION_REQUEST
}

@Serializable
data class ChatReply(
    val text: String,
    val intent: String,
    val provenance: Provenance
)

// ---------- Context passed to AI capabilities (ContextBuilder output, spec §6) ----------

@Serializable
data class CurrentTimeContext(
    val localDate: String,
    val localTime: String,
    val localTimestamp: String,
    val utcTimestamp: String,
    val zoneId: String,
    val utcOffset: String
)

@Serializable
data class AiRequestContext(
    val currentTime: CurrentTimeContext,
    val latitude: Double?,
    val longitude: Double?,
    val personName: String?,
    val kundali: KundaliData?,
    val dasha: DashaData?,
    val panchang: PanchangData?,
    val planetaryPositions: PlanetaryPositionResult?,
    val recentMessages: List<String> = emptyList()
)

// ---------- Chat UI models (spec §4-7) ----------

/**
 * Simple representation of a chat message used by the UI layer.
 * Mirrors the fields stored in [ConversationMessageEntity] but keeps the
 * types convenient for Compose (e.g., `conversationId` as String).
 */
@Serializable
data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val timestamp: String,
    val intent: String? = null,
    val contextSnapshotRef: String? = null
)

/**
 * Simple representation of a conversation for UI selection.
 */
@Serializable
data class Conversation(
    val id: String,
    val personId: Long?,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)
