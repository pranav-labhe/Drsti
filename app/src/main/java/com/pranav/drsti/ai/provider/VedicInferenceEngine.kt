package com.pranav.drsti.ai.provider

import com.pranav.drsti.model.*

/**
 * Deterministic Expert System for Vedic Jyotish Interpretation.
 * Codifies laws from BPHS, Phaladeepika, and Saravali into structured data findings.
 */
object VedicInferenceEngine {

    /**
     * Extracts a list of structured findings from raw Jyotish data.
     */
    fun evaluate(context: AiRequestContext): List<JyotishFinding> {
        val facts = extractFacts(context)
        return matchLaws(facts, context)
    }

    private data class JyotishFact(
        val planet: PlanetName,
        val house: Int,
        val sign: ZodiacSign,
        val isRetrograde: Boolean,
        val isDashaLord: Boolean = false,
        val isAntardashaLord: Boolean = false
    )

    private fun extractFacts(context: AiRequestContext): List<JyotishFact> {
        val kundali = context.kundali ?: return emptyList()
        val dasha = context.dasha
        
        return kundali.planets.map { p ->
            JyotishFact(
                planet = p.planet,
                house = p.house,
                sign = p.sign,
                isRetrograde = p.retrograde,
                isDashaLord = dasha?.currentMahadasha?.planet == p.planet,
                isAntardashaLord = dasha?.currentAntardasha?.planet == p.planet
            )
        }
    }

    private fun matchLaws(facts: List<JyotishFact>, context: AiRequestContext): List<JyotishFinding> {
        val findings = mutableListOf<JyotishFinding>()
        val md = facts.firstOrNull { it.isDashaLord }
        val ad = facts.firstOrNull { it.isAntardashaLord }
        
        // 1. Dasha Lord Theme
        md?.let { boss ->
            findings.add(JyotishFinding(
                category = FindingCategory.THEME,
                intensity = 80,
                supportDelta = 0,
                key = "THEME_${boss.planet.name}"
            ))
            
            findings.add(JyotishFinding(
                category = FindingCategory.PLACEMENT,
                intensity = 70,
                supportDelta = 0,
                key = "PLACEMENT_HOUSE_${boss.house}",
                params = mapOf("planet" to boss.planet.name)
            ))
        }

        // 2. MD/AD Axis Logic
        if (md != null && ad != null && md.planet != ad.planet) {
            val axis = calculateAxis(md.house, ad.house)
            findings.add(JyotishFinding(
                category = FindingCategory.AXIS,
                intensity = 60,
                supportDelta = if (axis in listOf(5, 9, 3, 11)) 15 else if (axis in listOf(6, 8, 2, 12)) -15 else 0,
                key = "AXIS_$axis"
            ))
        }

        // 3. Transit Triggers
        context.planetaryPositions?.positions?.forEach { transit ->
            val natalMoon = context.kundali?.planets?.firstOrNull { it.planet == PlanetName.MOON }
            if (natalMoon != null) {
                val houseFromMoon = ((transit.sign.index - natalMoon.sign.index + 12) % 12) + 1
                if (isNotableTransit(transit.planet, houseFromMoon)) {
                    findings.add(JyotishFinding(
                        category = FindingCategory.TRANSIT,
                        intensity = 50,
                        supportDelta = calculateTransitDelta(transit.planet, houseFromMoon),
                        key = "TRANSIT_${transit.planet.name}_HOUSE_$houseFromMoon"
                    ))
                }
            }
        }

        // 4. Advanced Yoga & Combination Detection
        if (detectGajaKesari(facts)) {
            findings.add(JyotishFinding(
                category = FindingCategory.YOGA, intensity = 90, supportDelta = 20, key = "YOGA_GAJA_KESARI"
            ))
        }
        
        if (detectRajaYoga(facts)) {
            findings.add(JyotishFinding(
                category = FindingCategory.YOGA, intensity = 85, supportDelta = 25, key = "YOGA_RAJA_INDICATOR"
            ))
        }
        
        if (detectDhanaYoga(facts)) {
            findings.add(JyotishFinding(
                category = FindingCategory.YOGA, intensity = 80, supportDelta = 15, key = "YOGA_DHANA_INDICATOR"
            ))
        }

        return findings
    }

    private fun detectRajaYoga(facts: List<JyotishFact>): Boolean {
        // Simplified: Connection between Kendra (1,4,7,10) and Trikona (1,5,9) lords
        val kendraHouses = listOf(1, 4, 7, 10)
        val trikonaHouses = listOf(1, 5, 9)
        return facts.any { it.house in kendraHouses && it.isDashaLord } && 
               facts.any { it.house in trikonaHouses }
    }

    private fun detectDhanaYoga(facts: List<JyotishFact>): Boolean {
        // Simplified: Dasha lord in 2nd or 11th house
        return facts.any { it.isDashaLord && it.house in listOf(2, 11) }
    }

    private fun calculateAxis(h1: Int, h2: Int): Int {
        val diff = (h2 - h1 + 12) % 12
        return if (diff == 0) 1 else diff + 1
    }

    private fun isNotableTransit(planet: PlanetName, house: Int): Boolean {
        return (planet == PlanetName.JUPITER && house in listOf(2, 5, 7, 9, 11)) ||
               (planet == PlanetName.SATURN && house in listOf(1, 12, 2, 4, 8))
    }

    private fun calculateTransitDelta(planet: PlanetName, house: Int): Int {
        return when {
            planet == PlanetName.JUPITER -> 15
            planet == PlanetName.SATURN && house in listOf(1, 12, 2) -> -20 // Sade Sati zones
            planet == PlanetName.SATURN -> -10
            else -> 0
        }
    }

    private fun detectGajaKesari(facts: List<JyotishFact>): Boolean {
        val jupiter = facts.firstOrNull { it.planet == PlanetName.JUPITER }
        val moon = facts.firstOrNull { it.planet == PlanetName.MOON }
        if (jupiter == null || moon == null) return false
        val dist = calculateAxis(moon.house, jupiter.house)
        return dist in listOf(1, 4, 7, 10)
    }
}
