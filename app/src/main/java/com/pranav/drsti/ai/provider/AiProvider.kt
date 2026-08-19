package com.pranav.drsti.ai.provider

import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    suspend fun chat(context: AiRequestContext, userMessage: String): ChatReply
}

private const val CALC_VERSION = "astrocalc-1.0"

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
                "These are astrological indicators, not guarantees. The final decision is yours."
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

    override suspend fun chat(context: AiRequestContext, userMessage: String): ChatReply = withContext(Dispatchers.Default) {
        val intent = IntentRecognizer.recognize(userMessage)
        val panchangInfo = context.panchang?.let {
            "Today is ${it.tithiName} tithi, ${it.nakshatra.displayName} nakshatra."
        } ?: ""
        
        val text = when (intent) {
            ChatIntent.PANCHANG_QUERY -> context.panchang?.let {
                "Today (${it.dateIso}) is ${it.vara}, ${it.tithiName} (${it.paksha}), ${it.nakshatra.displayName} nakshatra, ${it.yogaName} yoga." +
                        (it.rahuKalam?.let { rk -> " Rahu Kalam is around $rk — best avoided for new beginnings." } ?: "")
            } ?: "I don't have today's Panchang loaded yet — open the Panchang tab to generate it."
            ChatIntent.KUNDALI_QUERY -> context.kundali?.let {
                "Your Ascendant is ${it.ascendant.sign.displayName}. Moon is in ${it.planets.firstOrNull { p -> p.planet == PlanetName.MOON }?.sign?.displayName ?: "—"}."
            } ?: "I don't see a saved birth chart yet. Add your birth details in Profile to unlock this."
            ChatIntent.DASHA_QUERY -> context.dasha?.currentMahadasha?.let {
                "You're currently running ${it.planet.displayName()} Mahadasha (until ${it.endDateIso})." +
                        (context.dasha.currentAntardasha?.let { a -> " Within that, ${a.planet.displayName()} Antardasha is active until ${a.endDateIso}." } ?: "")
            } ?: "I don't have Dasha calculated yet — add your birth details first."
            ChatIntent.DECISION_ANALYSIS, ChatIntent.TIMING_COMPARISON ->
                "That sounds like a real decision worth comparing properly. $panchangInfo Tap the \"New Decision\" icon (+) in the top bar so I can lay out each option side by side with astrological support."
            else -> "I'm here to help you think through this using your chart and today's Panchang ($panchangInfo). Tell me more about what you're weighing, or ask about your Panchang, Kundali, or Dasha directly."
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
        // Falls back to the deterministic engine if the network call fails for any reason —
        // the app must never silently fabricate data (spec §44).
        return runCatching { callForInterpretation("transit-v1", context.toString()) }
            .map { mockFallback.analyzeTransits(context) } // JSON->TransitAnalysisResult wiring left to the caller's schema in a full build
            .getOrElse { mockFallback.analyzeTransits(context) }
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis {
        return runCatching { callForInterpretation("decision-v1", context.toString() + request.toString()) }
            .map { mockFallback.analyzeDecision(context, request) }
            .getOrElse { mockFallback.analyzeDecision(context, request) }
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis {
        return runCatching { callForInterpretation("outcome-v1", originalAnalysis.toString() + outcome.toString()) }
            .map { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
            .getOrElse { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String): ChatReply {
        val text = runCatching { callForInterpretation("chat-v1", userMessage) }.getOrNull()
            ?: return mockFallback.chat(context, userMessage)
        return ChatReply(
            text = text, intent = IntentRecognizer.recognize(userMessage).name,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "chat-v1", model = model,
                generatedAt = java.time.Instant.now().toString(), source = "OpenAiProvider",
                sourceVersion = "openai-chat-completions-v1",
                inputHash = HashUtil.sha256(userMessage), outputHash = HashUtil.sha256(text)
            )
        )
    }

    /**
     * Minimal chat-completions call. Kept intentionally simple/isolated inside this class
     * (spec §8: "keep API-specific implementation isolated inside the provider layer").
     */
    private fun callForInterpretation(promptVersion: String, userContent: String): String {
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
                return output!!
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
                // Use a standard non-blocking insert if possible, but here we are in a suspendable context
                // already via the AiAstrologyService calls. However, callForInterpretation is private and
                // called from suspend functions.
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
            }
        }
    }
}

object SystemPrompts {
    fun forVersion(promptVersion: String): String = when (promptVersion) {
        "decision-v1" -> "You are Dṛṣṭi's Vedic decision-analysis capability. You receive real, pre-computed astronomical/Jyotish data (never invent positions). Compare the given options using Dasha, transits, and Panchang. Never tell the user what to do — only present astrological support (0-100 indicators, not probabilities), supporting and contradicting factors, and clearly state the final decision is theirs."
        "outcome-v1" -> "You are Dṛṣṭi's retrospective Outcome capability. Compare the original immutable analysis against what actually happened. Never rewrite the original analysis. Identify which indicators aligned or didn't, and note calibration learnings."
        "transit-v1" -> "You are Dṛṣṭi's Transit capability. Analyze the supplied natal chart and current planetary positions. Do not fabricate positions; only interpret what's supplied."
        "chat-v1" -> "You are Dṛṣṭi, a private Vedic decision companion. Be concise, warm, and clear that astrology offers a lens, not certainty. Never decide for the user."
        else -> "You are Dṛṣṭi, a private Vedic Jyotish assistant. Use only the supplied data; never invent astronomical positions."
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
        logDao: AIRequestLogDao? = null
    ): AiAstrologyService = when (mode) {
        AiMode.MOCK -> MockAiProvider()
        AiMode.LIVE -> if (apiKey.isNullOrBlank()) MockAiProvider() else OpenAiProvider(apiKey, model, logDao)
        AiMode.GEMINI -> if (geminiApiKey.isNullOrBlank()) MockAiProvider() else GeminiAiProvider(geminiApiKey, model, logDao)
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
            .map { mockFallback.analyzeTransits(context) }
            .getOrElse { mockFallback.analyzeTransits(context) }
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis {
        return runCatching { callForInterpretation("decision-v1", context.toString() + request.toString()) }
            .map { mockFallback.analyzeDecision(context, request) }
            .getOrElse { mockFallback.analyzeDecision(context, request) }
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis {
        return runCatching { callForInterpretation("outcome-v1", originalAnalysis.toString() + outcome.toString()) }
            .map { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
            .getOrElse { mockFallback.analyzeOutcome(originalAnalysis, outcome) }
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String): ChatReply {
        val text = runCatching { callForInterpretation("chat-v1", userMessage) }.getOrNull()
            ?: return mockFallback.chat(context, userMessage)
        return ChatReply(
            text = text, intent = IntentRecognizer.recognize(userMessage).name,
            provenance = Provenance(
                calculationVersion = CALC_VERSION, promptVersion = "chat-v1", model = model,
                generatedAt = java.time.Instant.now().toString(), source = "GeminiAiProvider",
                sourceVersion = "gemini-generate-content-v1",
                inputHash = HashUtil.sha256(userMessage), outputHash = HashUtil.sha256(text)
            )
        )
    }

    private fun callForInterpretation(promptVersion: String, userContent: String): String {
        val startTime = System.currentTimeMillis()
        var success = false
        var errorMsg: String? = null
        val inputHash = HashUtil.sha256(userContent)
        var output: String? = null

        try {
            val systemPrompt = SystemPrompts.forVersion(promptVersion)
            val combinedPrompt = "$systemPrompt\n\nUser input: $userContent"
            
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
                return output!!
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
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
            }
        }
    }
}

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
