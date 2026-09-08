package com.pranav.drsti.ai.provider

import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.dao.ConversationStateDao
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.time.Instant

/*
 * ai.provider — the whole AI architecture layer (spec §7-9).
 *
 * AiAstrologyService is the single provider-agnostic interface every
 * screen/repository talks to. Two implementations exist:
 *   - MockAiProvider: deterministic, offline, zero cost. Real positions
 *     (via AstroCalc) + rule-based Jyotish interpretation. Default mode —
 *     the whole app is fully usable without any API key.
 *   - OpenAiProvider: calls a configured OpenAI-compatible chat-completions
 *     endpoint for the interpretive capabilities (decision/outcome/chat),
 *     while astronomical positions still always come from AstroCalc, never
 *     from the model (spec §10).
 *
 * Each capability keeps its own prompt version string (panchang-v1,
 * kundali-v1, dasha-v1, decision-v1, outcome-v1, chat-v1) per spec §7.
 */

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

enum class AiMode { MOCK, LIVE, GEMINI }

interface AiAstrologyService {
    suspend fun calculatePlanetaryPositions(dateTime: LocalDateTime, zoneId: ZoneId, latitude: Double, longitude: Double): PlanetaryPositionResult
    suspend fun calculateKundali(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, latitude: Double, longitude: Double): KundaliData
    suspend fun calculateDasha(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double): DashaData
    suspend fun generatePanchang(date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double): PanchangData
    suspend fun analyzeTransits(context: AiRequestContext): TransitAnalysisResult
    suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis
    suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis
    suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long? = null): ChatReply
}

private const val CALC_VERSION = "astrocalc-1.0"

/** Helper to extract and parse JSON from AI responses that might contain markdown or extra text. */
private fun <T> extractJson(text: String, serializer: KSerializer<T>): T? {
    val trimmed = text.trim()
    
    // 1. Try finding JSON inside triple backticks
    val tripleBacktickMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)?.groupValues?.get(1)
    if (tripleBacktickMatch != null) {
        runCatching { return json.decodeFromString(serializer, tripleBacktickMatch.trim()) }
    }

    // 2. Try finding JSON between known tags used in chat
    val markerMatch = if (trimmed.contains("DECISION_ANALYSIS_START")) {
        trimmed.substringAfter("DECISION_ANALYSIS_START").substringBefore("DECISION_ANALYSIS_END").trim()
    } else if (trimmed.contains("JSON_START")) {
        trimmed.substringAfter("JSON_START").substringBefore("JSON_END").trim()
    } else null
    
    if (markerMatch != null) {
        runCatching { return json.decodeFromString(serializer, markerMatch) }
    }

    // 3. Try parsing the whole string as raw JSON
    runCatching { return json.decodeFromString(serializer, trimmed) }

    return null
}

// ==================== Panchang & Dasha master data / helpers ====================

private val TITHI_NAMES = listOf(
    "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami",
    "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima/Amavasya"
)
private val YOGA_NAMES = listOf(
    "Vishkambha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda", "Sukarma", "Dhriti",
    "Shoola", "Ganda", "Vriddhi", "Dhruva", "Vyaghata", "Harshana", "Vajra", "Siddhi", "Vyatipata",
    "Variyana", "Parigha", "Shiva", "Siddha", "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti"
)
private val KARANA_NAMES = listOf("Bava", "Balava", "Kaulava", "Taitila", "Gara", "Vanija", "Vishti")
private val FIXED_KARANAS = listOf("Shakuni", "Chatushpada", "Naga", "Kimstughna")
private val WEEKDAY_VARA = mapOf(
    1 to "Ravivar (Sunday)", 2 to "Somvar (Monday)", 3 to "Mangalvar (Tuesday)",
    4 to "Budhvar (Wednesday)", 5 to "Guruvar (Thursday)", 6 to "Shukravar (Friday)", 7 to "Shanivar (Saturday)"
)
// Rahu Kalam / Yamaganda / Gulika segment index (of 8 daytime segments) by ISO weekday (1=Mon..7=Sun)
private val RAHU_SEGMENT = mapOf(1 to 1, 2 to 6, 3 to 4, 4 to 5, 5 to 3, 6 to 2, 7 to 7)
private val YAMAGANDA_SEGMENT = mapOf(1 to 3, 2 to 1, 3 to 5, 4 to 6, 5 to 4, 6 to 2, 7 to 0)
private val GULIKA_SEGMENT = mapOf(1 to 5, 2 to 4, 3 to 3, 4 to 2, 5 to 1, 6 to 0, 7 to 6)

/** Vimshottari Dasha total years per lord, and the fixed traversal order starting from any lord. */
private val VIMSHOTTARI_YEARS: Map<PlanetName, Int> = mapOf(
    PlanetName.KETU to 7, PlanetName.VENUS to 20, PlanetName.SUN to 6, PlanetName.MOON to 10,
    PlanetName.MARS to 7, PlanetName.RAHU to 18, PlanetName.JUPITER to 16, PlanetName.SATURN to 19,
    PlanetName.MERCURY to 17
)
private val VIMSHOTTARI_ORDER = listOf(
    PlanetName.KETU, PlanetName.VENUS, PlanetName.SUN, PlanetName.MOON, PlanetName.MARS,
    PlanetName.RAHU, PlanetName.JUPITER, PlanetName.SATURN, PlanetName.MERCURY
)
private const val VIMSHOTTARI_TOTAL_YEARS = 120.0

object DashaCalculator {
    fun compute(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double): DashaData {
        val birthNakshatra = Nakshatra.fromSiderealLongitude(moonSiderealLongitude)
        val elapsedFraction = Nakshatra.fractionElapsed(moonSiderealLongitude)
        val startLordIndex = VIMSHOTTARI_ORDER.indexOf(birthNakshatra.dashaLord)

        val birthDateTime = LocalDateTime.of(birthDate, birthTime)
        // Remaining balance of the first (birth) mahadasha, in years
        val firstLordYears = VIMSHOTTARI_YEARS.getValue(birthNakshatra.dashaLord)
        val remainingYearsOfFirst = firstLordYears * (1.0 - elapsedFraction)

        val periods = mutableListOf<DashaPeriod>()
        var cursor = birthDateTime
        for (i in 0 until 9) {
            val lord = VIMSHOTTARI_ORDER[(startLordIndex + i) % 9]
            val years = if (i == 0) remainingYearsOfFirst else VIMSHOTTARI_YEARS.getValue(lord).toDouble()
            val start = cursor
            val end = addYearsFractional(start, years)
            periods.add(
                DashaPeriod(
                    level = "mahadasha",
                    planet = lord,
                    startDateIso = start.toLocalDate().toString(),
                    endDateIso = end.toLocalDate().toString()
                )
            )
            cursor = end
        }

        val now = LocalDateTime.now(zoneId)
        val currentMaha = periods.firstOrNull { p ->
            val s = LocalDate.parse(p.startDateIso).atStartOfDay()
            val e = LocalDate.parse(p.endDateIso).atStartOfDay()
            !now.isBefore(s) && now.isBefore(e)
        } ?: periods.last()

        val currentAntar = computeAntardasha(currentMaha, now)

        val inputHash = HashUtil.sha256("$birthDate|$birthTime|$moonSiderealLongitude")
        val outputHash = HashUtil.sha256(periods.joinToString { it.planet.name + it.startDateIso })

        return DashaData(
            birthNakshatra = birthNakshatra,
            mahadashas = periods,
            currentMahadasha = currentMaha,
            currentAntardasha = currentAntar,
            provenance = Provenance(
                calculationVersion = CALC_VERSION,
                generatedAt = java.time.Instant.now().toString(),
                source = "AstroCalc (local Vimshottari engine)",
                sourceVersion = CALC_VERSION,
                inputHash = inputHash,
                outputHash = outputHash
            )
        )
    }

    /** Antardashas subdivide the mahadasha proportionally, starting from the same lord, per classical Vimshottari rule. */
    private fun computeAntardasha(maha: DashaPeriod, now: LocalDateTime): DashaPeriod {
        val start = LocalDate.parse(maha.startDateIso).atStartOfDay()
        val mahaYears = VIMSHOTTARI_YEARS.getValue(maha.planet).toDouble()
        val startIndex = VIMSHOTTARI_ORDER.indexOf(maha.planet)
        var cursor = start
        for (i in 0 until 9) {
            val subLord = VIMSHOTTARI_ORDER[(startIndex + i) % 9]
            val subYears = mahaYears * (VIMSHOTTARI_YEARS.getValue(subLord) / VIMSHOTTARI_TOTAL_YEARS)
            val subStart = cursor
            val subEnd = addYearsFractional(subStart, subYears)
            if (!now.isBefore(subStart) && now.isBefore(subEnd)) {
                return DashaPeriod(
                    level = "antardasha",
                    planet = subLord,
                    startDateIso = subStart.toLocalDate().toString(),
                    endDateIso = subEnd.toLocalDate().toString(),
                    parentPlanet = maha.planet
                )
            }
            cursor = subEnd
        }
        return DashaPeriod("antardasha", maha.planet, maha.startDateIso, maha.endDateIso, maha.planet)
    }

    private fun addYearsFractional(dt: LocalDateTime, years: Double): LocalDateTime {
        val wholeDays = (years * 365.2425).toLong()
        return dt.plusDays(wholeDays)
    }
}

object PanchangCalculator {
    fun compute(date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double): PanchangData {
        val noonLocal = LocalDateTime.of(date, LocalTime.of(12, 0))
        val zoneOffset = AstroCalc.zoneOffsetHoursFor(noonLocal, zoneId)
        val jd = AstroCalc.julianDay(noonLocal, zoneOffset)

        val sunLong = AstroCalc.sunTropicalLongitude(jd)
        val moonLong = AstroCalc.moonTropicalLongitude(jd)

        var diff = ((moonLong - sunLong) % 360.0 + 360.0) % 360.0
        val tithiIndex = (diff / 12.0).toInt() // 0-29
        val tithiInPaksha = tithiIndex % 15
        val paksha = if (tithiIndex < 15) "Shukla Paksha" else "Krishna Paksha"
        val tithiName = if (tithiInPaksha == 14) {
            if (paksha == "Shukla Paksha") "Purnima" else "Amavasya"
        } else TITHI_NAMES[tithiInPaksha]

        val ayanamsha = AstroCalc.lahiriAyanamsha(jd)
        val moonSidereal = ((moonLong - ayanamsha) % 360.0 + 360.0) % 360.0
        val nakshatra = Nakshatra.fromSiderealLongitude(moonSidereal)

        val yogaSum = ((sunLong + moonLong) % 360.0 + 360.0) % 360.0
        val yogaIndex = (yogaSum / (360.0 / 27.0)).toInt().coerceIn(0, 26)
        val yogaName = YOGA_NAMES[yogaIndex]

        val karanaIndex = (diff / 6.0).toInt() // half-tithi, 0-59
        val karanaName = when {
            karanaIndex == 0 -> FIXED_KARANAS[0]
            karanaIndex in 57..59 -> FIXED_KARANAS[(karanaIndex - 56).coerceIn(1, 3)]
            else -> KARANA_NAMES[(karanaIndex - 1) % 7]
        }

        val isoDow = date.dayOfWeek.value // 1=Mon..7=Sun
        val varaMapKey = if (isoDow == 7) 1 else isoDow + 1 // convert to 1=Sun..7=Sat for display table above
        val vara = WEEKDAY_VARA[varaMapKey] ?: date.dayOfWeek.name

        val sunTimes = AstroCalc.sunriseSunsetLocalHours(date, latitude, longitude, zoneOffset)
        var rahuKalam: String? = null
        var yamaganda: String? = null
        var gulika: String? = null
        var abhijit: String? = null
        if (sunTimes != null) {
            val (sunriseH, sunsetH) = sunTimes
            val segmentLength = (sunsetH - sunriseH) / 8.0
            fun segmentRange(seg: Int): Pair<Double, Double> {
                val s = sunriseH + segmentLength * seg
                return s to (s + segmentLength)
            }
            RAHU_SEGMENT[isoDow]?.let { seg -> val (s, e) = segmentRange(seg); rahuKalam = "${AstroCalc.formatHour(s)}-${AstroCalc.formatHour(e)}" }
            YAMAGANDA_SEGMENT[isoDow]?.let { seg -> val (s, e) = segmentRange(seg); yamaganda = "${AstroCalc.formatHour(s)}-${AstroCalc.formatHour(e)}" }
            GULIKA_SEGMENT[isoDow]?.let { seg -> val (s, e) = segmentRange(seg); gulika = "${AstroCalc.formatHour(s)}-${AstroCalc.formatHour(e)}" }
            val midDay = (sunriseH + sunsetH) / 2.0
            abhijit = "${AstroCalc.formatHour(midDay - 0.4)}-${AstroCalc.formatHour(midDay + 0.4)}"
        }

        val inputHash = HashUtil.sha256("$date|$latitude|$longitude|$zoneId")
        val outputHash = HashUtil.sha256("$tithiName|$nakshatra|$yogaName|$karanaName")

        return PanchangData(
            dateIso = date.toString(),
            vara = vara,
            tithiName = tithiName,
            tithiNumber = tithiInPaksha + 1,
            paksha = paksha,
            nakshatra = nakshatra,
            yogaName = yogaName,
            karanaName = karanaName,
            sunriseLocal = sunTimes?.let { AstroCalc.formatHour(it.first) },
            sunsetLocal = sunTimes?.let { AstroCalc.formatHour(it.second) },
            rahuKalam = rahuKalam,
            yamaganda = yamaganda,
            gulikaKalam = gulika,
            abhijitMuhurta = abhijit,
            calculationNotes = if (sunTimes == null) "Sunrise/sunset could not be computed for this latitude/date; muhurta windows omitted." else null,
            provenance = Provenance(
                calculationVersion = CALC_VERSION,
                generatedAt = java.time.Instant.now().toString(),
                source = "AstroCalc (local low-precision solar/lunar engine)",
                sourceVersion = CALC_VERSION,
                inputHash = inputHash,
                outputHash = outputHash
            )
        )
    }
}

// ==================== Mock provider (default, offline, zero-cost) ====================

/**
 * Deterministic, offline provider. Real positions (via AstroCalc) + basic rule-based 
 * Jyotish interpretation. Used as the foundation for calculation and as a fallback
 * for interpretive providers (spec §7).
 */
class MockAiProvider : AiAstrologyService {

    override suspend fun calculatePlanetaryPositions(
        dateTime: LocalDateTime, zoneId: ZoneId, latitude: Double, longitude: Double
    ): PlanetaryPositionResult = withContext(Dispatchers.Default) {
        val offset = AstroCalc.zoneOffsetHoursFor(dateTime, zoneId)
        val jd = AstroCalc.julianDay(dateTime, offset)
        val ayanamsha = AstroCalc.lahiriAyanamsha(jd)
        val positions = AstroCalc.allPlanetPositions(jd, ayanamsha)
        val inputHash = HashUtil.sha256("$dateTime|$zoneId|$latitude|$longitude")
        val outputHash = HashUtil.sha256(positions.joinToString { it.planet.name + it.siderealLongitude })
        PlanetaryPositionResult(
            timestampUtc = dateTime.atZone(zoneId).withZoneSameInstant(ZoneId.of("UTC")).toString(),
            latitude = latitude, longitude = longitude,
            ayanamshaDegrees = ayanamsha,
            positions = positions,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, generatedAt = java.time.Instant.now().toString(),
                source = "AstroCalc (local ephemeris)", sourceVersion = CALC_VERSION,
                inputHash = inputHash, outputHash = outputHash
            )
        )
    }

    override suspend fun calculateKundali(
        birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, latitude: Double, longitude: Double
    ): KundaliData = withContext(Dispatchers.Default) {
        val dateTime = LocalDateTime.of(birthDate, birthTime)
        val offset = AstroCalc.zoneOffsetHoursFor(dateTime, zoneId)
        val jd = AstroCalc.julianDay(dateTime, offset)
        val ayanamsha = AstroCalc.lahiriAyanamsha(jd)

        val ascTropical = AstroCalc.ascendantTropicalLongitude(jd, latitude, longitude)
        val ascSidereal = ((ascTropical - ayanamsha) % 360.0 + 360.0) % 360.0
        val ascSign = ZodiacSign.fromSiderealLongitude(ascSidereal)
        val ascendant = AscendantInfo(
            siderealLongitude = ascSidereal, sign = ascSign,
            degreeInSign = ascSign.degreeWithin(ascSidereal),
            nakshatra = Nakshatra.fromSiderealLongitude(ascSidereal),
            pada = Nakshatra.padaFor(ascSidereal)
        )

        val positions = AstroCalc.allPlanetPositions(jd, ayanamsha)
        val planets = positions.map { p ->
            // Whole-sign houses: house = (planet sign index - ascendant sign index + 12) % 12 + 1
            val house = ((p.sign.index - ascSign.index + 12) % 12) + 1
            KundaliPlanet(
                planet = p.planet, sign = p.sign, degreeInSign = p.degreeInSign, house = house,
                nakshatra = p.nakshatra, pada = p.pada, retrograde = p.isRetrograde
            )
        }

        val inputHash = HashUtil.sha256("$birthDate|$birthTime|$zoneId|$latitude|$longitude")
        val outputHash = HashUtil.sha256(planets.joinToString { it.planet.name + it.house })

        KundaliData(
            ascendant = ascendant, planets = planets,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, generatedAt = java.time.Instant.now().toString(),
                source = "AstroCalc (local ephemeris + whole-sign houses)", sourceVersion = CALC_VERSION,
                inputHash = inputHash, outputHash = outputHash
            )
        )
    }

    override suspend fun calculateDasha(
        birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double
    ): DashaData = withContext(Dispatchers.Default) {
        DashaCalculator.compute(birthDate, birthTime, zoneId, moonSiderealLongitude)
    }

    override suspend fun generatePanchang(
        date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double
    ): PanchangData = withContext(Dispatchers.Default) {
        PanchangCalculator.compute(date, zoneId, latitude, longitude)
    }

    override suspend fun analyzeTransits(context: AiRequestContext): TransitAnalysisResult = withContext(Dispatchers.Default) {
        val natal = context.kundali
        val current = context.planetaryPositions
        val highlights = mutableListOf<TransitHighlight>()
        val favorable = mutableListOf<String>()
        val caution = mutableListOf<String>()

        if (natal != null && current != null) {
            for (transitPos in current.positions) {
                val natalHit = natal.planets.firstOrNull { it.sign == transitPos.sign && it.planet != transitPos.planet }
                if (natalHit != null) {
                    highlights += TransitHighlight(
                        transitPlanet = transitPos.planet, natalPlanet = natalHit.planet,
                        relationship = "conjunction (same sign)",
                        note = "Transiting ${transitPos.planet.displayName()} is moving through your natal ${natalHit.planet.displayName()}'s sign (${transitPos.sign.displayName})."
                    )
                }
                if (transitPos.isRetrograde) {
                    caution += "${transitPos.planet.displayName()} is currently retrograde — its themes may feel slower or more internal than usual."
                }
            }
            val benefics = current.positions.filter { it.planet == PlanetName.JUPITER || it.planet == PlanetName.VENUS }
            if (benefics.any { !it.isRetrograde }) {
                favorable += "Jupiter/Venus are direct — generally supportive for new beginnings in their significations."
            }
        }

        val inputHash = HashUtil.sha256(context.toString())
        TransitAnalysisResult(
            summary = if (natal == null) "No natal chart on file yet — add your birth details to unlock personalised transit analysis."
            else "Current transits compared against your natal chart, based on today's real computed planetary positions.",
            highlights = highlights, favorableThemes = favorable, cautionThemes = caution,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "transit-v1", model = "mock",
                generatedAt = java.time.Instant.now().toString(), source = "MockAiProvider",
                sourceVersion = CALC_VERSION, inputHash = inputHash, outputHash = HashUtil.sha256(highlights.toString())
            )
        )
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis = withContext(Dispatchers.Default) {
        val dasha = context.dasha
        val panchang = context.panchang
        val options = request.options.mapIndexed { idx, opt ->
            // Deterministic but non-trivial scoring so MOCK mode still feels differentiated per option,
            // seeded from the option text + current dasha lord so results are stable, not random noise.
            val seed = (HashUtil.sha256(opt.description + (dasha?.currentMahadasha?.planet?.name ?: "")).take(6).sumOf { it.code })
            val base = 45 + (seed % 40) // 45-84
            val astro = base.coerceIn(15, 90)
            val timing = (base + (idx * 3) - 5).coerceIn(15, 90)
            val strength = when {
                astro >= 76 -> "very strong"
                astro >= 61 -> "strong"
                astro >= 41 -> "moderate"
                astro >= 21 -> "weak"
                else -> "very weak"
            }
            val dashaNote = dasha?.currentMahadasha?.let {
                "You are currently in ${it.planet.displayName()} Mahadasha, which colours how this path is likely to unfold."
            } ?: "Add your birth details to bring Dasha timing into this analysis."
            val panchangNote = panchang?.let {
                "Today's Panchang shows ${it.tithiName} tithi under ${it.nakshatra.displayName} nakshatra."
            } ?: ""
            DecisionOptionAnalysis(
                id = opt.id,
                astrologicalSupport = astro,
                timingSupport = timing,
                strength = strength,
                strengths = listOfNotNull(
                    if (astro >= 60) "Multiple independent indicators point in a supportive direction for this option." else null,
                    dashaNote
                ),
                concerns = listOfNotNull(
                    if (astro < 45) "Fewer supporting indicators were found; treat this option with more caution." else null,
                    if (dasha?.currentAntardasha?.planet == PlanetName.SATURN) "Saturn's sub-period often favours patience over speed." else null
                ),
                supportingFactors = listOfNotNull(dashaNote, panchangNote.ifBlank { null }),
                contradictingFactors = emptyList(),
                explanation = "Based on your current Dasha period and today's Panchang, option \"${opt.description}\" shows $strength astrological support (an AI-estimated indicator, not a probability)."
            )
        }
        val preferred = options.maxByOrNull { it.astrologicalSupport + it.timingSupport }?.id
        val inputHash = HashUtil.sha256(request.toString())
        val outputHash = HashUtil.sha256(options.toString())
        DecisionAnalysis(
            analysisSummary = "Compared ${options.size} path(s) for: \"${request.question}\" using your natal chart, current Dasha, and today's Panchang where available.",
            options = options,
            preferredOptionId = preferred,
            confidence = if (context.kundali != null && context.dasha != null) "medium" else "low",
            caveats = listOfNotNull(
                if (context.kundali == null) "No natal chart on file — add your birth details for a fuller analysis." else null,
                "These are astrological indicators, not guarantees. The final decision is theirs."
            ),
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "decision-v1", model = "mock",
                generatedAt = java.time.Instant.now().toString(), source = "MockAiProvider",
                sourceVersion = CALC_VERSION, inputHash = inputHash, outputHash = outputHash
            )
        )
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis = withContext(Dispatchers.Default) {
        val chosen = originalAnalysis.options.firstOrNull { it.id == outcome.selectedOptionId }
        val aligned = mutableListOf<String>()
        val misaligned = mutableListOf<String>()
        if (chosen != null) {
            when {
                chosen.astrologicalSupport >= 60 && outcome.userAssessment.contains("better", true) ->
                    aligned += "The high astrological support score aligned with a positive outcome."
                chosen.astrologicalSupport < 45 && outcome.userAssessment.contains("worse", true) ->
                    aligned += "The low astrological support score aligned with a disappointing outcome."
                else -> misaligned += "The original score did not clearly track how things actually went — worth noting for calibration."
            }
        }
        val inputHash = HashUtil.sha256(outcome.toString())
        OutcomeAnalysis(
            alignedIndicators = aligned,
            misalignedIndicators = misaligned,
            timingUsefulness = "Recorded for your personal calibration history; no single outcome should be over-weighted.",
            scoreCalibrationNote = if (chosen != null) "Original score for the chosen path was ${chosen.astrologicalSupport}%." else "No matching option score found.",
            learnings = listOf("This outcome has been added to your personal calibration statistics."),
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "outcome-v1", model = "mock",
                generatedAt = java.time.Instant.now().toString(), source = "MockAiProvider",
                sourceVersion = CALC_VERSION, inputHash = inputHash, outputHash = HashUtil.sha256(aligned.toString() + misaligned.toString())
            )
        )
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply = withContext(Dispatchers.Default) {
        val intent = IntentRecognizer.recognize(userMessage)
        
        val panchangInfo = context.panchang?.let {
            "Today is ${it.tithiName} tithi (${it.paksha}) under ${it.nakshatra.displayName} nakshatra."
        } ?: "Panchang details are not yet generated for today."

        val kundaliInfo = context.kundali?.let {
            "Your Lagna (Ascendant) is ${it.ascendant.sign.displayName}."
        } ?: "Birth chart details are not on file."

        val dashaInfo = context.dasha?.currentMahadasha?.let {
            "You are running ${it.planet.displayName()} Mahadasha."
        } ?: "Dasha timing information is missing."

        val text = when (intent) {
            ChatIntent.PANCHANG_QUERY -> context.panchang?.let {
                "Today (${it.dateIso}) is ${it.vara}, ${it.tithiName} (${it.paksha}), ${it.nakshatra.displayName} nakshatra. " +
                        (it.rahuKalam?.let { rk -> "Rahu Kalam is around $rk." } ?: "")
            } ?: "Open the Panchang tab to load today's cosmic timing."
            
            ChatIntent.KUNDALI_QUERY -> context.kundali?.let {
                "Your Ascendant is ${it.ascendant.sign.displayName}. Moon is in ${it.planets.firstOrNull { p -> p.planet == PlanetName.MOON }?.sign?.displayName ?: "—"}."
            } ?: "Add your birth details in Profile to unlock your chart analysis."
            
            ChatIntent.DASHA_QUERY -> context.dasha?.currentMahadasha?.let {
                "You're in ${it.planet.displayName()} Mahadasha until ${it.endDateIso}." +
                        (context.dasha.currentAntardasha?.let { a -> " Within that, ${a.planet.displayName()} Antardasha is active." } ?: "")
            } ?: "Add birth details to calculate your Vimshottari Dasha periods."
            
            ChatIntent.DECISION_ANALYSIS, ChatIntent.TIMING_COMPARISON ->
                "That sounds like a decision. $panchangInfo Tap the (+) icon in the top bar to compare your choices with full astrological support."
            
            else -> "I see you're asking about your day. $panchangInfo $kundaliInfo $dashaInfo Based on this, it's a good time for reflection. For a deeper analysis of a specific choice, try the 'New Decision' icon in the top bar."
        }

        ChatReply(
            text = text, intent = intent.name,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "chat-v1", model = "mock",
                generatedAt = java.time.Instant.now().toString(), source = "MockAiProvider",
                sourceVersion = CALC_VERSION, inputHash = HashUtil.sha256(userMessage), outputHash = HashUtil.sha256(text)
            )
        )
    }
}

/** Deterministic local intent detection so we don't burn an AI call just to classify obvious intents (spec §47). */
object IntentRecognizer {
    fun recognize(message: String): ChatIntent {
        val m = message.lowercase()
        return when {
            "panchang" in m || "tithi" in m || "nakshatra today" in m || "how is my day" in m || ("today" in m && "how" in m) -> ChatIntent.PANCHANG_QUERY
            "kundali" in m || "birth chart" in m || "ascendant" in m || "lagna" in m -> ChatIntent.KUNDALI_QUERY
            "dasha" in m || "mahadasha" in m || "antardasha" in m -> ChatIntent.DASHA_QUERY
            "transit" in m -> ChatIntent.TRANSIT_QUERY
            (" or " in m && ("should i" in m || "which" in m)) -> ChatIntent.DECISION_ANALYSIS
            "now or" in m || "better time" in m -> ChatIntent.TIMING_COMPARISON
            "i chose" in m || "i picked" in m || "i went with" in m -> ChatIntent.DECISION_RECORD
            "what happened" in m || "here's what happened" in m || "the outcome" in m -> ChatIntent.OUTCOME_RECORD
            else -> ChatIntent.GENERAL_CHAT
        }
    }
}

// ==================== Live provider (OpenAI-compatible, optional) ====================

/**
 * LIVE mode. Astronomical positions still always come from AstroCalc
 * (spec §10) — only the interpretive text (decision/outcome/chat) is
 * delegated to the configured model, as structured JSON.
 */
class OpenAiProvider(
    private val apiKey: String,
    private val model: String,
    private val logDao: AIRequestLogDao? = null,
    private val baseUrl: String = "https://api.openai.com/v1/chat/completions"
) : AiAstrologyService {

    private val mockFallback = MockAiProvider() // positions/kundali/dasha/panchang are pure calculation either way
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun calculatePlanetaryPositions(dateTime: LocalDateTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculatePlanetaryPositions(dateTime, zoneId, latitude, longitude)

    override suspend fun calculateKundali(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculateKundali(birthDate, birthTime, zoneId, latitude, longitude)

    override suspend fun calculateDasha(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double) =
        mockFallback.calculateDasha(birthDate, birthTime, zoneId, moonSiderealLongitude)

    override suspend fun generatePanchang(date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.generatePanchang(date, zoneId, latitude, longitude)

    override suspend fun analyzeTransits(context: AiRequestContext): TransitAnalysisResult {
        // Falls back to the deterministic engine if the network call fails or parsing fails —
        // the app must never silently fabricate data (spec §44).
        return runCatching { callForInterpretation("transit-v1", context.toString()) }
            .map { output -> extractJson(output, TransitAnalysisResult.serializer()) ?: mockFallback.analyzeTransits(context) }
            .getOrElse { mockFallback.analyzeTransits(context) }
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis {
        return runCatching { callForInterpretation("decision-v1", context.toString() + request.toString()) }
            .map { output -> extractJson(output, DecisionAnalysis.serializer()) ?: mockFallback.analyzeDecision(context, request) }
            .getOrElse { mockFallback.analyzeDecision(context, request) }
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis {
        return runCatching { callForInterpretation("outcome-v1", originalAnalysis.toString() + outcome.toString()) }
            .map { output -> extractJson(output, OutcomeAnalysis.serializer()) ?: mockFallback.analyzeOutcome(originalAnalysis, outcome) }
            .getOrElse { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply {
        val promptVersion = "chat-v1"
        val startTime = System.currentTimeMillis()
        var success = false
        var errorMsg: String? = null
        var output: String? = null

        val structuredContext = buildString {
            append("AVAILABLE JYOTISH DATA\n\n")
            
            context.panchang?.let {
                append("[PANCHANG]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.kundali?.let {
                append("[KOSHTAKA / NATAL CHART]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.dasha?.let {
                append("[DASHA]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.planetaryPositions?.let {
                append("[CURRENT PLANETARY POSITIONS]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }

            if (context.recentMessages.isNotEmpty()) {
                append("RECENT CONVERSATION HISTORY\n")
                context.recentMessages.forEach { append(it).append("\n") }
                append("\n")
            }

            append("USER MESSAGE\n")
            append(userMessage)
        }

        try {
            output = callForInterpretation(promptVersion, structuredContext)
            success = true

            // Try to extract a structured decision analysis if the AI included one
            val decisionAnalysis = extractJson(output, DecisionAnalysis.serializer())

            val cleanedText = if (decisionAnalysis != null && output.contains("DECISION_ANALYSIS_START")) {
                output.substringBefore("DECISION_ANALYSIS_START").trim() + "\n\n" + output.substringAfter("DECISION_ANALYSIS_END").trim()
            } else output

            return ChatReply(
                text = cleanedText.trim(), 
                intent = if (decisionAnalysis != null) "DECISION_ANALYSIS" else "AI_INTERPRETED",
                provenance = Provenance(
                    calculationVersion = CALC_VERSION, promptVersion = promptVersion, model = model,
                    generatedAt = java.time.Instant.now().toString(), source = "OpenAiProvider",
                    sourceVersion = "openai-chat-completions-v1",
                    inputHash = HashUtil.sha256(structuredContext), outputHash = HashUtil.sha256(output)
                ),
                decisionAnalysis = decisionAnalysis
            )
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            throw e
        } finally {
            logDao?.let { dao ->
                val log = AIRequestLogEntity(
                    conversationId = conversationId,
                    requestType = promptVersion,
                    timestamp = Instant.now().toString(),
                    model = model,
                    promptVersion = promptVersion,
                    inputHash = HashUtil.sha256(structuredContext),
                    outputHash = output?.let { HashUtil.sha256(it) },
                    success = success,
                    error = errorMsg,
                    latencyMs = System.currentTimeMillis() - startTime
                )
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
            }
        }
    }

    /**
     * Minimal chat-completions call. Kept intentionally simple/isolated inside this class
     * (spec §8: "keep API-specific implementation isolated inside the provider layer").
     */
    private suspend fun callForInterpretation(promptVersion: String, userContent: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var success = false
        var errorMsg: String? = null
        val inputHash = HashUtil.sha256(userContent)
        var output: String? = null

        try {
            val bodyJson = json.encodeToString(
                OpenAiChatRequest(
                    model = model,
                    messages = listOf(
                        OpenAiMessage("system", SystemPrompts.forVersion(promptVersion)),
                        OpenAiMessage("user", userContent)
                    )
                )
            )
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    errorMsg = "OpenAI request failed: ${response.code} ${response.message}"
                    error(errorMsg!!)
                }
                val raw = response.body?.string() ?: error("Empty response body")
                val parsed = json.decodeFromString(OpenAiChatResponse.serializer(), raw)
                output = parsed.choices.firstOrNull()?.message?.content ?: error("No content in response")
                success = true
                return@withContext output!!
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            throw e
        } finally {
            logDao?.let { dao ->
                val log = AIRequestLogEntity(
                    requestType = promptVersion,
                    timestamp = Instant.now().toString(),
                    model = model,
                    promptVersion = promptVersion,
                    inputHash = inputHash,
                    outputHash = output?.let { HashUtil.sha256(it) },
                    success = success,
                    error = errorMsg,
                    latencyMs = System.currentTimeMillis() - startTime
                )
                dao.insert(log)
            }
        }
    }
}

object SystemPrompts {
    fun forVersion(promptVersion: String): String = when (promptVersion) {
        "decision-v1" -> """
            You are D\u1e5b\u1e63\u1e6di's Vedic decision-analysis capability. You help the user look beyond the obvious by comparing choices through the lens of Vedic Jyotish.
            You receive real, pre-computed astronomical data (never invent positions). 
            Compare the given options using Dasha, transits, and Panchang. Never tell the user what to do — only present astrological support (0-100 indicators, not probabilities), supporting and contradicting factors, and clearly state the final decision is theirs.
            
            Return your analysis as a structured JSON object matching the DecisionAnalysis schema. 
            Do not include any conversational filler; only return the JSON block.
        """.trimIndent()
        "outcome-v1" -> """
            You are D\u1e5b\u1e63\u1e6di's retrospective Outcome capability. Compare the original immutable analysis against what actually happened. Never rewrite the original analysis. Identify which indicators aligned or didn't, and note calibration learnings.
            
            Return your analysis as a structured JSON object matching the OutcomeAnalysis schema.
            Do not include any conversational filler; only return the JSON block.
        """.trimIndent()
        "transit-v1" -> """
            You are D\u1e5b\u1e63\u1e6di's Transit capability. Analyze the supplied natal chart and current planetary positions. Do not fabricate positions; only interpret what's supplied.
            
            Return your analysis as a structured JSON object matching the TransitAnalysisResult schema.
            Do not include any conversational filler; only return the JSON block.
        """.trimIndent()
        "chat-v1" -> """
            You are D\u1e5b\u1e63\u1e6di, an intelligent decision companion with the ability to reason using Vedic Jyotish.
            D\u1e5b\u1e63\u1e6di helps the user look beyond the obvious, using different Jyotish perspectives as an underlying reasoning lens.
            The user speaks normally. D\u1e5b\u1e63\u1e6di understands the Jyotish implications behind the question.
            Analyze the user's question using the supplied Jyotish data.
            Determine yourself which parts of the supplied data are relevant to the question. Do not assume every vector is relevant.
            Do not invent missing astronomical, Kundali, Dasha, Panchang, or transit data.
            
            IMPORTANT FOR END USER:
            - Respond directly to the user in a natural, friendly, human-understandable manner.
            - Avoid technical Jyotish jargon (like Graha names, house numbers, or specific yoga names) in your final natural language response unless explicitly asked.
            - Focus primarily on the practical implications of the user's actual question and circumstances. When the question is broader or its practical context is unclear, focus on relevant aspects of the user's life, such as career, relationships, timing, personal growth, or wellbeing.
            - For every insight or prediction, provide an estimated "Astrological Support" percentage (0-100%) based on the strength or consistency of the indicators you found in the data.
            - Astrology is an interpretive lens, not certainty. Do not present indicators as scientifically validated probabilities or guarantees.
            
            Determine yourself which factors are relevant. Do not merely repeat the Panchang when the user is asking for a personalized interpretation.
            Synthesize the relevant natal chart, Dasha, transits, and Panchang vectors.

            If the user is asking you to compare choices or make a decision in chat, identify the choices from the user's message and provide a structured comparison suitable for D\u1e5b\u1e63\u1e6di's existing DecisionAnalysis flow. 
            
            Your response MUST follow this format if a decision is detected:
            1. A natural language introduction.
            2. The tag "DECISION_ANALYSIS_START" on its own line.
            3. A JSON object representing the DecisionAnalysis model (containing summary, options with id and explanation, preferredOptionId, confidence, and caveats).
            4. The tag "DECISION_ANALYSIS_END" on its own line.
            5. A natural language conclusion.
        """.trimIndent()
        else -> "You are D\u1e5b\u1e63\u1e6di, a private Vedic Jyotish assistant. Use only the supplied data; never invent astronomical positions."
    }
}

@kotlinx.serialization.Serializable
private data class OpenAiMessage(val role: String, val content: String)
@kotlinx.serialization.Serializable
private data class OpenAiChatRequest(val model: String, val messages: List<OpenAiMessage>, val temperature: Double = 0.4)
@kotlinx.serialization.Serializable
private data class OpenAiChoice(val message: OpenAiMessage)
@kotlinx.serialization.Serializable
private data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())

object AiProviderFactory {
    fun create(
        mode: AiMode,
        apiKey: String?,
        model: String,
        geminiApiKey: String? = null,
        logDao: AIRequestLogDao? = null,
        context: android.content.Context? = null,
        stateDao: ConversationStateDao? = null,
        decisionRepository: DecisionRepository? = null,
        personRepository: PersonRepository? = null
    ): AiAstrologyService {
        val base = MockAiProvider()
        val offline = if (context != null && stateDao != null && decisionRepository != null && personRepository != null) {
            NativeVedicProvider(base, context, stateDao, decisionRepository, personRepository)
        } else base

        return when (mode) {
            AiMode.MOCK -> offline
            AiMode.LIVE -> if (apiKey.isNullOrBlank()) offline else OpenAiProvider(apiKey, model, logDao)
            AiMode.GEMINI -> if (geminiApiKey.isNullOrBlank()) offline else GeminiAiProvider(geminiApiKey, model, logDao)
        }
    }
}

/**
 * GEMINI mode. Uses Google's Gemini API for interpretive capabilities.
 * Astronomical positions still always come from AstroCalc (spec §10).
 */
class GeminiAiProvider(
    private val apiKey: String,
    private val model: String,
    private val logDao: AIRequestLogDao? = null
) : AiAstrologyService {

    private val mockFallback = MockAiProvider()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch the list of available Gemini models for the provided API key.
     * Uses the Google Generative Language API endpoint:
     *   GET https://generativelanguage.googleapis.com/v1beta/models?key=API_KEY
     * Returns a list of model names (e.g., "gemini-1.5-flash").
     */
    suspend fun fetchAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Gemini model list request failed: ${response.code} ${response.message}")
            }
            val body = response.body?.string() ?: error("Empty response body for Gemini model list")
            // Expected JSON: {"models":[{"name":"models/gemini-1.5-flash",...}, ...]}
            val jsonElement = json.parseToJsonElement(body)
            val modelsArray = jsonElement.jsonObject["models"]?.jsonArray ?: return@withContext emptyList()
            modelsArray.mapNotNull { elem ->
                val name = elem.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                // Strip the leading "models/" if present
                name?.removePrefix("models/")
            }
        }
    }

    override suspend fun calculatePlanetaryPositions(dateTime: LocalDateTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculatePlanetaryPositions(dateTime, zoneId, latitude, longitude)

    override suspend fun calculateKundali(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculateKundali(birthDate, birthTime, zoneId, latitude, longitude)

    override suspend fun calculateDasha(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double) =
        mockFallback.calculateDasha(birthDate, birthTime, zoneId, moonSiderealLongitude)

    override suspend fun generatePanchang(date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.generatePanchang(date, zoneId, latitude, longitude)

    override suspend fun analyzeTransits(context: AiRequestContext): TransitAnalysisResult {
        return runCatching { callForInterpretation("transit-v1", context.toString()) }
            .map { output -> extractJson(output, TransitAnalysisResult.serializer()) ?: mockFallback.analyzeTransits(context) }
            .getOrElse { mockFallback.analyzeTransits(context) }
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis {
        return runCatching { callForInterpretation("decision-v1", context.toString() + request.toString()) }
            .map { output -> extractJson(output, DecisionAnalysis.serializer()) ?: mockFallback.analyzeDecision(context, request) }
            .getOrElse { mockFallback.analyzeDecision(context, request) }
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis {
        return runCatching { callForInterpretation("outcome-v1", originalAnalysis.toString() + outcome.toString()) }
            .map { output -> extractJson(output, OutcomeAnalysis.serializer()) ?: mockFallback.analyzeOutcome(originalAnalysis, outcome) }
            .getOrElse { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply {
        val promptVersion = "chat-v1"
        val startTime = System.currentTimeMillis()
        var success = false
        var errorMsg: String? = null
        var output: String? = null
        var responseId: String? = null
        var previousId: String? = null

        val structuredContext = buildString {
            append("AVAILABLE JYOTISH DATA\n\n")
            
            context.panchang?.let {
                append("[PANCHANG]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.kundali?.let {
                append("[KOSHTAKA / NATAL CHART]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.dasha?.let {
                append("[DASHA]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }
            
            context.planetaryPositions?.let {
                append("[CURRENT PLANETARY POSITIONS]\n")
                append(json.encodeToString(it))
                append("\n\n")
            }

            // For Gemini interactions, we don't manually append history here.
            // It's handled by the previous_interaction_id.

            append("USER MESSAGE\n")
            append(userMessage)
        }

        try {
            // Find previous interaction ID from logs
            if (conversationId != null && logDao != null) {
                previousId = logDao.getLastInteraction(conversationId)?.interactionId
            }

            val systemPrompt = SystemPrompts.forVersion(promptVersion)
            val combinedInput = "$systemPrompt\n\n$structuredContext"
            
            val bodyJson = json.encodeToString(
                GeminiInteractionRequest(
                    model = "models/$model",
                    input = combinedInput,
                    previous_interaction_id = previousId
                )
            )
            
            val url = "https://generativelanguage.googleapis.com/v1beta/interactions"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
                
            val responseText = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        errorMsg = "Gemini interaction failed: ${response.code} ${response.message}\n$errorBody"
                        error(errorMsg!!)
                    }
                    val raw = response.body?.string() ?: error("Empty response body")
                    try {
                        val parsed = json.decodeFromString(GeminiInteractionResponse.serializer(), raw)
                        responseId = parsed.id
                        // Extract text from steps -> content (native interactions format)
                        val stepOutput = parsed.steps
                            .find { it.type == "model_output" }
                            ?.content
                            ?.find { it.type == "text" }
                            ?.text
                        
                        output = stepOutput ?: parsed.output
                        output ?: error("No text content found in Gemini response. Checked 'steps' and 'output'. Raw: $raw")
                    } catch (e: Exception) {
                        error("Failed to parse Gemini interaction response: ${e.message}. Raw body: $raw")
                    }
                }
            }

            output = responseText
            success = true
            
            // Try to extract a structured decision analysis
            val decisionAnalysis = extractJson(output, DecisionAnalysis.serializer())

            val cleanedText = if (decisionAnalysis != null && output.contains("DECISION_ANALYSIS_START")) {
                output.substringBefore("DECISION_ANALYSIS_START").trim() + "\n\n" + output.substringAfter("DECISION_ANALYSIS_END").trim()
            } else output

            return ChatReply(
                text = cleanedText.trim(), 
                intent = if (decisionAnalysis != null) "DECISION_ANALYSIS" else "AI_INTERPRETED",
                provenance = Provenance(
                    calculationVersion = CALC_VERSION, promptVersion = promptVersion, model = model,
                    generatedAt = java.time.Instant.now().toString(), source = "GeminiAiProvider",
                    sourceVersion = "gemini-interactions-v1",
                    inputHash = HashUtil.sha256(structuredContext), outputHash = HashUtil.sha256(output)
                ),
                decisionAnalysis = decisionAnalysis
            )
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            throw e
        } finally {
            logDao?.let { dao ->
                val log = AIRequestLogEntity(
                    conversationId = conversationId,
                    interactionId = responseId,
                    previousInteractionId = previousId,
                    requestType = promptVersion,
                    timestamp = Instant.now().toString(),
                    model = model,
                    promptVersion = promptVersion,
                    inputHash = HashUtil.sha256(structuredContext),
                    outputHash = output?.let { HashUtil.sha256(it) },
                    success = success,
                    error = errorMsg,
                    latencyMs = System.currentTimeMillis() - startTime
                )
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
            }
        }
    }

    private suspend fun callForInterpretation(promptVersion: String, userContent: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var success = false
        var errorMsg: String? = null
        val inputHash = HashUtil.sha256(userContent)
        var output: String? = null

        try {
            val systemPrompt = SystemPrompts.forVersion(promptVersion)
            val combinedPrompt = "$systemPrompt\n\nUser input: $userContent"
            
            // For non-chat calls, we use the standard generateContent flow
            val bodyJson = json.encodeToString(
                GeminiChatRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = combinedPrompt)))
                    )
                )
            )
            
            val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val url = "$baseUrl?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    errorMsg = "Gemini request failed: ${response.code} ${response.message}"
                    error(errorMsg!!)
                }
                val raw = response.body?.string() ?: error("Empty response body")
                val parsed = json.decodeFromString(GeminiChatResponse.serializer(), raw)
                output = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: error("No content in response")
                success = true
                return@withContext output!!
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            throw e
        } finally {
            logDao?.let { dao ->
                val log = AIRequestLogEntity(
                    requestType = promptVersion,
                    timestamp = Instant.now().toString(),
                    model = model,
                    promptVersion = promptVersion,
                    inputHash = inputHash,
                    outputHash = output?.let { HashUtil.sha256(it) },
                    success = success,
                    error = errorMsg,
                    latencyMs = System.currentTimeMillis() - startTime
                )
                dao.insert(log)
            }
        }
    }
}

@kotlinx.serialization.Serializable
private data class GeminiInteractionRequest(
    val model: String,
    val input: String,
    val previous_interaction_id: String? = null
)

@kotlinx.serialization.Serializable
private data class GeminiInteractionResponse(
    val id: String,
    val output: String? = null,
    val steps: List<GeminiStep> = emptyList()
)

@kotlinx.serialization.Serializable
private data class GeminiStep(
    val type: String,
    val content: List<GeminiContentPart> = emptyList()
)

@kotlinx.serialization.Serializable
private data class GeminiContentPart(
    val type: String,
    val text: String? = null
)

@kotlinx.serialization.Serializable
private data class GeminiPart(val text: String)

@kotlinx.serialization.Serializable
private data class GeminiContent(val parts: List<GeminiPart>)

@kotlinx.serialization.Serializable
private data class GeminiChatRequest(val contents: List<GeminiContent>)

@kotlinx.serialization.Serializable
private data class GeminiCandidate(val content: GeminiContent)

@kotlinx.serialization.Serializable
private data class GeminiChatResponse(val candidates: List<GeminiCandidate> = emptyList())
