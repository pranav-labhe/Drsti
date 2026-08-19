package com.pranav.drsti

import com.pranav.drsti.model.Nakshatra
import com.pranav.drsti.model.ZodiacSign
import org.junit.Assert.assertEquals
import org.junit.Test

class ZodiacMappingTest {

    @Test
    fun `0 degrees sidereal is Aries`() {
        assertEquals(ZodiacSign.ARIES, ZodiacSign.fromSiderealLongitude(0.0))
    }

    @Test
    fun `29-9 degrees sidereal is Pisces`() {
        assertEquals(ZodiacSign.PISCES, ZodiacSign.fromSiderealLongitude(359.9))
    }

    @Test
    fun `degree within sign is normalized correctly`() {
        val sign = ZodiacSign.fromSiderealLongitude(45.0) // Taurus, 15 degrees in
        assertEquals(ZodiacSign.TAURUS, sign)
        assertEquals(15.0, sign.degreeWithin(45.0), 0.001)
    }

    @Test
    fun `nakshatra index matches expected span`() {
        assertEquals(Nakshatra.ASHWINI, Nakshatra.fromSiderealLongitude(0.0))
        assertEquals(Nakshatra.BHARANI, Nakshatra.fromSiderealLongitude(14.0))
    }

    @Test
    fun `pada calculation stays within 1 to 4`() {
        for (deg in 0..359) {
            val pada = Nakshatra.padaFor(deg.toDouble())
            assert(pada in 1..4)
        }
    }
}
