package com.pranav.drsti.ai.provider

import com.pranav.drsti.model.Nakshatra
import com.pranav.drsti.model.PlanetName
import com.pranav.drsti.model.PlanetPosition
import com.pranav.drsti.model.ZodiacSign
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.*

/**
 * Real deterministic astronomical calculations (Julian Day, low-precision
 * Sun/Moon/planet ecliptic longitudes, mean lunar node, ascendant, Lahiri
 * ayanamsha). This is the "local ephemeris" implementation of
 * PlanetaryPositionService allowed by spec §11-§12 ("a local ephemeris
 * dataset ... a future implementation may swap in an external API").
 *
 * These are standard, widely published low-precision astronomical
 * algorithms (Meeus-style solar/lunar series, JPL approximate Keplerian
 * elements for the major planets). Accuracy is roughly arc-minute level —
 * more than sufficient for whole-sign Vedic house/sign placement, but NOT
 * suitable for arc-second research use. The AI layer never invents these
 * numbers; it only interprets them (spec §10).
 */
object AstroCalc {

    private fun Double.rad() = Math.toRadians(this)
    private fun Double.deg() = Math.toDegrees(this)
    private fun norm360(x: Double): Double { val m = x % 360.0; return if (m < 0) m + 360.0 else m }

    // ---------------- Julian Day ----------------

    fun julianDay(dt: LocalDateTime, zoneOffsetHours: Double): Double {
        val utc = dt.minusSeconds((zoneOffsetHours * 3600).toLong())
        var year = utc.year
        var month = utc.monthValue
        val day = utc.dayOfMonth + (utc.hour + utc.minute / 60.0 + utc.second / 3600.0) / 24.0
        if (month <= 2) { year -= 1; month += 12 }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    fun julianCenturiesSinceJ2000(jd: Double): Double = (jd - 2451545.0) / 36525.0

    // ---------------- Ayanamsha (Lahiri, linear approximation) ----------------

    fun lahiriAyanamsha(jd: Double): Double {
        val yearsSince1900 = (jd - 2415020.0) / 365.25
        return 23.85333 + 0.013972 * yearsSince1900
    }

    // ---------------- Sun (Meeus low-precision) ----------------

    fun sunTropicalLongitude(jd: Double): Double {
        val t = julianCenturiesSinceJ2000(jd)
        val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = m.rad()
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2 * mRad) +
                0.000289 * sin(3 * mRad)
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val apparent = trueLong - 0.00569 - 0.00478 * sin(omega.rad())
        return norm360(apparent)
    }

    // ---------------- Moon (Meeus abbreviated series) ----------------

    fun moonTropicalLongitude(jd: Double): Double {
        val t = julianCenturiesSinceJ2000(jd)
        val lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t)
        val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t)
        val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t)
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t)
        val f = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t)

        fun s(x: Double) = sin(x.rad())

        val dL = 6.288774 * s(mp) + 1.274027 * s(2 * d - mp) + 0.658314 * s(2 * d) +
                0.213618 * s(2 * mp) - 0.185116 * s(m) - 0.114332 * s(2 * f) +
                0.058793 * s(2 * d - 2 * mp) + 0.057066 * s(2 * d - m - mp) +
                0.053322 * s(2 * d + mp) + 0.045758 * s(2 * d - m) -
                0.040923 * s(m - mp) - 0.034720 * s(d) - 0.030383 * s(m + mp) +
                0.015327 * s(2 * d - 2 * f) - 0.012528 * s(mp + 2 * f) +
                0.010980 * s(mp - 2 * f)

        return norm360(lp + dL)
    }

    // ---------------- Mean lunar node (Rahu/Ketu) ----------------

    fun meanNodeLongitude(jd: Double): Double {
        val t = julianCenturiesSinceJ2000(jd)
        val omega = 125.0445479 - 1934.1362891 * t + 0.0020754 * t * t
        return norm360(omega)
    }

    // ---------------- Outer/inner planets via approximate Keplerian elements ----------------
    // JPL "Keplerian Elements for Approximate Positions of the Major Planets" (Standish),
    // valid ~1800-2050. a=AU, angles in degrees, rates are per Julian century.

    private data class Elements(
        val a0: Double, val aDot: Double,
        val e0: Double, val eDot: Double,
        val i0: Double, val iDot: Double,
        val l0: Double, val lDot: Double,
        val peri0: Double, val periDot: Double,
        val node0: Double, val nodeDot: Double
    )

    private val EARTH = Elements(
        1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668,
        100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0
    )
    private val ELEMENTS = mapOf(
        PlanetName.MERCURY to Elements(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        PlanetName.VENUS to Elements(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        PlanetName.MARS to Elements(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        PlanetName.JUPITER to Elements(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        PlanetName.SATURN to Elements(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794)
    )

    /** Heliocentric ecliptic (x, y) at time T (Julian centuries from J2000). */
    private fun heliocentricXY(e: Elements, t: Double): Pair<Double, Double> {
        val a = e.a0 + e.aDot * t
        val ecc = e.e0 + e.eDot * t
        val i = (e.i0 + e.iDot * t).rad()
        val l = e.l0 + e.lDot * t
        val peri = e.peri0 + e.periDot * t
        val node = e.node0 + e.nodeDot * t
        val w = (peri - node) // argument of perihelion
        var m = norm360(l - peri)
        if (m > 180.0) m -= 360.0

        val eccDeg = ecc.deg()
        var eAnom = m + eccDeg * sin(m.rad())
        repeat(8) {
            val dM = m - (eAnom - eccDeg * sin(eAnom.rad()))
            val dE = dM / (1 - ecc * cos(eAnom.rad()))
            eAnom += dE
        }
        val eRad = eAnom.rad()
        val xp = a * (cos(eRad) - ecc)
        val yp = a * sqrt(1 - ecc * ecc) * sin(eRad)

        val wRad = w.rad(); val nodeRad = node.rad(); val iRad = i
        val xEcl = (cos(wRad) * cos(nodeRad) - sin(wRad) * sin(nodeRad) * cos(iRad)) * xp +
                (-sin(wRad) * cos(nodeRad) - cos(wRad) * sin(nodeRad) * cos(iRad)) * yp
        val yEcl = (cos(wRad) * sin(nodeRad) + sin(wRad) * cos(nodeRad) * cos(iRad)) * xp +
                (-sin(wRad) * sin(nodeRad) + cos(wRad) * cos(nodeRad) * cos(iRad)) * yp
        return xEcl to yEcl
    }

    /** Geocentric tropical ecliptic longitude for Mercury/Venus/Mars/Jupiter/Saturn. */
    fun planetTropicalLongitude(planet: PlanetName, jd: Double): Double {
        val t = julianCenturiesSinceJ2000(jd)
        val elements = ELEMENTS[planet] ?: error("No orbital elements for $planet")
        val (xp, yp) = heliocentricXY(elements, t)
        val (xe, ye) = heliocentricXY(EARTH, t)
        val xg = xp - xe
        val yg = yp - ye
        return norm360(atan2(yg, xg).deg())
    }

    /** Rough retrograde check via longitude one day earlier vs later (finite difference). */
    fun isRetrograde(planet: PlanetName, jd: Double): Boolean {
        val before = longitudeFor(planet, jd - 1.0)
        val after = longitudeFor(planet, jd + 1.0)
        var delta = after - before
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        return delta < 0
    }

    fun longitudeFor(planet: PlanetName, jd: Double): Double = when (planet) {
        PlanetName.SUN -> sunTropicalLongitude(jd)
        PlanetName.MOON -> moonTropicalLongitude(jd)
        PlanetName.RAHU -> meanNodeLongitude(jd)
        PlanetName.KETU -> norm360(meanNodeLongitude(jd) + 180.0)
        else -> planetTropicalLongitude(planet, jd)
    }

    // ---------------- Ascendant ----------------

    fun ascendantTropicalLongitude(jd: Double, latitude: Double, longitudeEast: Double): Double {
        val t = julianCenturiesSinceJ2000(jd)
        val gmst = norm360(
            280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                    0.000387933 * t * t - (t * t * t) / 38710000.0
        )
        val lst = norm360(gmst + longitudeEast) // Local Sidereal Time / RAMC in degrees
        val obliquity = 23.439291 - 0.0130042 * t
        val lstRad = lst.rad(); val eRad = obliquity.rad(); val latRad = latitude.rad()

        val y = -cos(lstRad)
        val x = sin(eRad) * tan(latRad) + cos(eRad) * sin(lstRad)
        var asc = atan2(y, x).deg()
        asc = norm360(asc)
        return asc
    }

    // ---------------- Sunrise / sunset (approximate) ----------------

    /** Returns (sunriseHourLocal, sunsetHourLocal) as decimal hours, or null if not computable (polar cases). */
    fun sunriseSunsetLocalHours(date: LocalDate, latitude: Double, longitudeEast: Double, zoneOffsetHours: Double): Pair<Double, Double>? {
        val n = date.dayOfYear
        val lngHour = longitudeEast / 15.0

        fun compute(isSunrise: Boolean): Double? {
            val t = n + ((if (isSunrise) 6.0 else 18.0) - lngHour) / 24.0
            val m = 0.9856 * t - 3.289
            var l = m + 1.916 * sin(m.rad()) + 0.020 * sin(2 * m.rad()) + 282.634
            l = norm360(l)
            var ra = atan2(0.91764 * tan(l.rad()), 1.0).deg()
            ra = norm360(ra)
            val lQuadrant = floor(l / 90.0) * 90.0
            val raQuadrant = floor(ra / 90.0) * 90.0
            ra += (lQuadrant - raQuadrant)
            ra /= 15.0
            val sinDec = 0.39782 * sin(l.rad())
            val cosDec = cos(asin(sinDec))
            val cosH = (cos(90.833.rad()) - (sinDec * sin(latitude.rad()))) / (cosDec * cos(latitude.rad()))
            if (cosH > 1.0 || cosH < -1.0) return null // sun never rises/sets that day at this latitude
            val hRaw = if (isSunrise) 360.0 - acos(cosH).deg() else acos(cosH).deg()
            val h = hRaw / 15.0
            val time = h + ra - 0.06571 * t - 6.622
            var utHours = norm360(time * 15.0) / 15.0
            var localHours = utHours + zoneOffsetHours - lngHour
            localHours = ((localHours % 24.0) + 24.0) % 24.0
            return localHours
        }

        val sunrise = compute(true) ?: return null
        val sunset = compute(false) ?: return null
        return sunrise to sunset
    }

    fun formatHour(decimalHour: Double): String {
        val totalMinutes = (decimalHour * 60).roundToInt()
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return "%02d:%02d".format(h, m)
    }

    // ---------------- Sign / Nakshatra mapping ----------------

    fun toPlanetPosition(planet: PlanetName, jd: Double, ayanamsha: Double): PlanetPosition {
        val tropical = longitudeFor(planet, jd)
        val sidereal = ((tropical - ayanamsha) % 360.0 + 360.0) % 360.0
        val sign = ZodiacSign.fromSiderealLongitude(sidereal)
        val retro = if (planet == PlanetName.SUN || planet == PlanetName.MOON) false
        else if (planet == PlanetName.RAHU || planet == PlanetName.KETU) true
        else isRetrograde(planet, jd)
        return PlanetPosition(
            planet = planet,
            tropicalLongitude = tropical,
            siderealLongitude = sidereal,
            isRetrograde = retro,
            sign = sign,
            degreeInSign = sign.degreeWithin(sidereal),
            nakshatra = Nakshatra.fromSiderealLongitude(sidereal),
            pada = Nakshatra.padaFor(sidereal)
        )
    }

    fun allPlanetPositions(jd: Double, ayanamsha: Double): List<PlanetPosition> =
        PlanetName.entries.map { toPlanetPosition(it, jd, ayanamsha) }

    fun zoneOffsetHoursFor(dateTime: LocalDateTime, zoneId: java.time.ZoneId): Double {
        val offset = zoneId.rules.getOffset(dateTime)
        return offset.totalSeconds / 3600.0
    }
}
