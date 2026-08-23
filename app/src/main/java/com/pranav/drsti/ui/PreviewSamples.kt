package com.pranav.drsti.ui

import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.database.entity.ConversationMessageEntity
import com.pranav.drsti.database.entity.DecisionEntity
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.database.entity.PlaceEntity
import com.pranav.drsti.model.*
import java.time.Instant

object PreviewSamples {
    val provenance = Provenance(
        calculationVersion = "1.0",
        generatedAt = Instant.now().toString(),
        source = "Preview Mock",
        sourceVersion = "1.0",
        inputHash = "mock",
        outputHash = "mock"
    )

    val panchang = PanchangData(
        dateIso = "2026-08-21",
        vara = "Friday",
        tithiName = "Ekadashi",
        tithiNumber = 11,
        paksha = "Shukla Paksha",
        nakshatra = Nakshatra.ASHWINI,
        yogaName = "Saubhagya",
        karanaName = "Bava",
        sunriseLocal = "06:12",
        sunsetLocal = "18:45",
        rahuKalam = "10:30-12:00",
        yamaganda = "15:00-16:30",
        gulikaKalam = "07:30-09:00",
        abhijitMuhurta = "11:50-12:40",
        provenance = provenance
    )

    val planets = listOf(
        KundaliPlanet(PlanetName.SUN, ZodiacSign.LEO, 5.0, 1, Nakshatra.MAGHA, 1, false),
        KundaliPlanet(PlanetName.MOON, ZodiacSign.SCORPIO, 12.0, 4, Nakshatra.ANURADHA, 3, false),
        KundaliPlanet(PlanetName.MARS, ZodiacSign.ARIES, 22.0, 9, Nakshatra.BHARANI, 4, false)
    )

    val kundali = KundaliData(
        ascendant = AscendantInfo(120.0, ZodiacSign.LEO, 0.0, Nakshatra.MAGHA, 1),
        planets = planets,
        provenance = provenance
    )

    val dasha = DashaData(
        birthNakshatra = Nakshatra.ANURADHA,
        mahadashas = listOf(
            DashaPeriod("mahadasha", PlanetName.SATURN, "1995-01-01", "2014-01-01")
        ),
        currentMahadasha = DashaPeriod("mahadasha", PlanetName.SATURN, "1995-01-01", "2014-01-01"),
        currentAntardasha = DashaPeriod("antardasha", PlanetName.MERCURY, "2011-01-01", "2014-01-01", PlanetName.SATURN),
        provenance = provenance
    )

    val planetPositions = listOf(
        PlanetPosition(PlanetName.SUN, 125.0, 120.0, false, ZodiacSign.LEO, 0.0, Nakshatra.MAGHA, 1),
        PlanetPosition(PlanetName.MOON, 222.0, 217.0, false, ZodiacSign.SCORPIO, 7.0, Nakshatra.ANURADHA, 3)
    )

    val transitHighlights = listOf(
        TransitHighlight(PlanetName.JUPITER, PlanetName.SUN, "conjunction", "Transiting Jupiter is conjunct your natal Sun."),
        TransitHighlight(PlanetName.SATURN, null, "retrograde", "Saturn is currently retrograde, favoring internal reflection.")
    )

    val logs = listOf(
        AIRequestLogEntity(1, null, "v2_123", null, "chat-v1", "2026-08-20T10:00:00", "gemini-1.5-flash", "chat-v1", "mock_hash", "mock_hash", true, null, 1200),
        AIRequestLogEntity(2, null, null, null, "decision-v1", "2026-08-20T10:05:00", "gpt-4o", "decision-v1", "mock_hash", null, false, "Connection timeout", 5000)
    )

    val place = PlaceEntity(
        id = 1,
        name = "Mumbai",
        city = "Mumbai",
        state = "Maharashtra",
        country = "India",
        latitude = 19.076,
        longitude = 72.877,
        timezone = "Asia/Kolkata",
        isUserCreated = true
    )

    val person = PersonEntity(
        id = 1,
        name = "Pranav",
        dateOfBirthIso = "1990-05-15",
        timeOfBirthIso = "08:30:00",
        birthTimeUncertaintyMinutes = 5,
        placeId = 1,
        latitude = 19.076,
        longitude = 72.877,
        timezone = "Asia/Kolkata",
        isActive = true,
        createdAt = Instant.now().toString(),
        updatedAt = Instant.now().toString()
    )

    val decision = DecisionEntity(
        id = 1,
        personId = 1,
        conversationId = null,
        question = "Should I start a new project this week?",
        optionsJson = "[]",
        status = "OPEN",
        createdAt = Instant.now().toString()
    )

    val decisionAnalysis = DecisionAnalysis(
        analysisSummary = "The current planetary alignments suggest strong support for new beginnings.",
        options = listOf(
            DecisionOptionAnalysis("A", 85, 70, "Strong", listOf("Benefic aspects"), emptyList(), emptyList(), emptyList(), "Starting now aligns with your Dasha periods."),
            DecisionOptionAnalysis("B", 40, 30, "Weak", emptyList(), listOf("Malefic transits"), emptyList(), emptyList(), "Waiting might be more prudent given current retrograde planets.")
        ),
        preferredOptionId = "A",
        confidence = "High",
        caveats = listOf("Final decision should consider physical readiness."),
        provenance = provenance
    )

    val messages = listOf(
        ConversationMessageEntity(1, 1, "user", "How is my day looking?", null, null, Instant.now().toString()),
        ConversationMessageEntity(2, 1, "assistant", "#### Daily Overview\nToday is supportive for **creative work**. \n\n- Tithi: Ekadashi\n- Nakshatra: Ashwini\n\nTake care of your health.", null, null, Instant.now().toString())
    )
}
