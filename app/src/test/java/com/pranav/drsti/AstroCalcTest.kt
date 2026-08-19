package com.pranav.drsti

import com.pranav.drsti.ai.provider.AstroCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AstroCalcTest {

    @Test
    fun `known J2000 epoch julian day is correct`() {
        // Jan 1, 2000, 12:00 UTC is JD 2451545.0 by definition.
        val jd = AstroCalc.julianDay(LocalDateTime.of(2000, 1, 1, 12, 0), 0.0)
        assertEquals(2451545.0, jd, 0.01)
    }

    @Test
    fun `sun longitude stays within 0 to 360`() {
        val jd = AstroCalc.julianDay(LocalDateTime.of(2026, 3, 20, 12, 0), 0.0)
        val lon = AstroCalc.sunTropicalLongitude(jd)
        assertTrue(lon in 0.0..360.0)
    }

    @Test
    fun `moon longitude stays within 0 to 360`() {
        val jd = AstroCalc.julianDay(LocalDateTime.of(2026, 6, 1, 0, 0), 0.0)
        val lon = AstroCalc.moonTropicalLongitude(jd)
        assertTrue(lon in 0.0..360.0)
    }

    @Test
    fun `lahiri ayanamsha is roughly in the expected modern range`() {
        val jd = AstroCalc.julianDay(LocalDateTime.of(2026, 1, 1, 0, 0), 0.0)
        val ayanamsha = AstroCalc.lahiriAyanamsha(jd)
        // Lahiri ayanamsha in the 2020s is documented to sit close to 24 degrees.
        assertTrue("ayanamsha=$ayanamsha", ayanamsha in 23.5..25.5)
    }

    @Test
    fun `ascendant longitude stays within 0 to 360`() {
        val jd = AstroCalc.julianDay(LocalDateTime.of(2026, 1, 1, 6, 30), 5.5)
        val asc = AstroCalc.ascendantTropicalLongitude(jd, latitude = 21.15, longitudeEast = 79.09)
        assertTrue(asc in 0.0..360.0)
    }
}
