package org.opentopo.transform

import kotlin.math.floor

enum class UtmHemisphere(val code: Char) {
    NORTH('N'),
    SOUTH('S'),
}

data class SirgasUtmCoordinate(
    val eastingM: Double,
    val northingM: Double,
    val zone: Int,
    val hemisphere: UtmHemisphere,
    val heightM: Double = 0.0,
) {
    val epsg: Int?
        get() = SirgasUtmTransform.epsg(zone, hemisphere)
}

/**
 * SIRGAS2000 geographic <-> UTM conversion using the GRS80 ellipsoid.
 *
 * Brazil is covered by UTM zones 18-25 in the southern hemisphere and
 * zones 18-23 in the northern hemisphere. EPSG codes are returned for
 * those official SIRGAS2000 / UTM CRS definitions.
 */
object SirgasUtmTransform {
    private const val SCALE_FACTOR = 0.9996
    private const val FALSE_EASTING = 500_000.0
    private const val SOUTH_FALSE_NORTHING = 10_000_000.0

    fun zoneFromLongitude(longitudeDeg: Double): Int {
        require(longitudeDeg in -180.0..180.0) {
            "Longitude must be between -180 and 180 degrees"
        }
        return (floor((longitudeDeg + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
    }

    fun centralMeridianDeg(zone: Int): Double {
        require(zone in 1..60) { "UTM zone must be between 1 and 60" }
        return zone * 6.0 - 183.0
    }

    fun epsg(zone: Int, hemisphere: UtmHemisphere): Int? = when (hemisphere) {
        UtmHemisphere.SOUTH -> if (zone in 18..25) 31960 + zone else null
        UtmHemisphere.NORTH -> if (zone in 18..22) 31954 + zone else null
    }

    fun forward(
        coordinate: GeographicCoordinate,
        zoneOverride: Int? = null,
    ): SirgasUtmCoordinate {
        require(coordinate.latitudeDeg in -80.0..84.0) {
            "UTM is defined between 80°S and 84°N"
        }

        val zone = zoneOverride ?: zoneFromLongitude(coordinate.longitudeDeg)
        require(zone in 1..60) { "UTM zone must be between 1 and 60" }

        val hemisphere = if (coordinate.latitudeDeg < 0.0) {
            UtmHemisphere.SOUTH
        } else {
            UtmHemisphere.NORTH
        }

        val projected = TransverseMercator.forward(
            latDeg = coordinate.latitudeDeg,
            lonDeg = coordinate.longitudeDeg,
            centralMeridianDeg = centralMeridianDeg(zone),
            scaleFactor = SCALE_FACTOR,
            falseEasting = FALSE_EASTING,
            falseNorthing = if (hemisphere == UtmHemisphere.SOUTH) SOUTH_FALSE_NORTHING else 0.0,
        )

        return SirgasUtmCoordinate(
            eastingM = projected.eastingM,
            northingM = projected.northingM,
            zone = zone,
            hemisphere = hemisphere,
            heightM = coordinate.heightM,
        )
    }

    fun inverse(coordinate: SirgasUtmCoordinate): GeographicCoordinate {
        require(coordinate.zone in 1..60) { "UTM zone must be between 1 and 60" }

        return TransverseMercator.inverse(
            eastingM = coordinate.eastingM,
            northingM = coordinate.northingM,
            centralMeridianDeg = centralMeridianDeg(coordinate.zone),
            scaleFactor = SCALE_FACTOR,
            falseEasting = FALSE_EASTING,
            falseNorthing = if (coordinate.hemisphere == UtmHemisphere.SOUTH) SOUTH_FALSE_NORTHING else 0.0,
            heightM = coordinate.heightM,
        )
    }
}
