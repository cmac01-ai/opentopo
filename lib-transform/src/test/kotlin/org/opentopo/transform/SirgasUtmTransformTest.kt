package org.opentopo.transform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SirgasUtmTransformTest {

    private data class Vector(
        val latitude: Double,
        val longitude: Double,
        val zone: Int,
        val epsg: Int,
        val easting: Double,
        val northing: Double,
    )

    private val vectors = listOf(
        Vector(-19.916681, -43.934493, 23, 31983, 611521.098156, 7797385.458871),
        Vector(-30.034647, -51.217658, 22, 31982, 479014.660290, 6677355.518143),
        Vector(-3.119028, -60.021731, 20, 31980, 831068.364367, 9654781.774743),
        Vector(-8.047562, -34.877000, 25, 31985, 293144.377034, 9109969.620294),
    )

    @Test
    fun `SIRGAS2000 geographic to UTM matches reference vectors`() {
        for (vector in vectors) {
            val result = SirgasUtmTransform.forward(
                GeographicCoordinate(vector.latitude, vector.longitude),
            )

            assertEquals(vector.zone, result.zone)
            assertEquals(UtmHemisphere.SOUTH, result.hemisphere)
            assertEquals(vector.epsg, result.epsg)
            assertTrue(abs(result.eastingM - vector.easting) < 0.001, "E mismatch for EPSG:${vector.epsg}")
            assertTrue(abs(result.northingM - vector.northing) < 0.001, "N mismatch for EPSG:${vector.epsg}")
        }
    }

    @Test
    fun `SIRGAS2000 UTM inverse returns original geographic coordinate`() {
        for (vector in vectors) {
            val result = SirgasUtmTransform.inverse(
                SirgasUtmCoordinate(
                    eastingM = vector.easting,
                    northingM = vector.northing,
                    zone = vector.zone,
                    hemisphere = UtmHemisphere.SOUTH,
                ),
            )

            assertTrue(abs(result.latitudeDeg - vector.latitude) < 1e-8, "Latitude mismatch for EPSG:${vector.epsg}")
            assertTrue(abs(result.longitudeDeg - vector.longitude) < 1e-8, "Longitude mismatch for EPSG:${vector.epsg}")
        }
    }

    @Test
    fun `zone can be forced for projects near a UTM boundary`() {
        val automatic = SirgasUtmTransform.forward(
            GeographicCoordinate(latitudeDeg = -20.0, longitudeDeg = -42.01),
        )
        val forced = SirgasUtmTransform.forward(
            GeographicCoordinate(latitudeDeg = -20.0, longitudeDeg = -42.01),
            zoneOverride = 23,
        )

        assertEquals(24, automatic.zone)
        assertEquals(23, forced.zone)
        assertEquals(31983, forced.epsg)
    }

    @Test
    fun `EPSG mapping includes Brazilian SIRGAS2000 UTM zones`() {
        assertEquals(31978, SirgasUtmTransform.epsg(18, UtmHemisphere.SOUTH))
        assertEquals(31985, SirgasUtmTransform.epsg(25, UtmHemisphere.SOUTH))
        assertEquals(31972, SirgasUtmTransform.epsg(18, UtmHemisphere.NORTH))
        assertEquals(31977, SirgasUtmTransform.epsg(23, UtmHemisphere.NORTH))
        assertNull(SirgasUtmTransform.epsg(17, UtmHemisphere.SOUTH))
    }
}
