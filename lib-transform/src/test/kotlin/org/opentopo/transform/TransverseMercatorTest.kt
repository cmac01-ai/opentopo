package org.opentopo.transform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class TransverseMercatorTest {

    @Test
    fun `TM87 forward - Athens EGSA87 geographic`() {
        val result = TransverseMercator.forward(
            latDeg = 37.968905265531,
            lonDeg = 23.725006319766,
            centralMeridianDeg = 24.0,
            scaleFactor = 0.9996,
            falseEasting = 500_000.0,
            falseNorthing = 0.0,
        )
        assertTrue(abs(result.eastingM - 475846.075977) < 0.001, "E mismatch: ${result.eastingM}")
        assertTrue(abs(result.northingM - 4202400.665196) < 0.001, "N mismatch: ${result.northingM}")
    }

    @Test
    fun `TM07 forward - Athens HTRS07 geographic`() {
        val result = TransverseMercator.forward(
            latDeg = 37.9715,
            lonDeg = 23.7267,
            centralMeridianDeg = 24.0,
            scaleFactor = 0.9996,
            falseEasting = 500_000.0,
            falseNorthing = -2_000_000.0,
        )
        assertTrue(abs(result.eastingM - 475995.684650) < 0.001, "E mismatch: ${result.eastingM}")
        assertTrue(abs(result.northingM - 2202688.117793) < 0.001, "N mismatch: ${result.northingM}")
    }

    @Test
    fun `forward and inverse round trip`() {
        val projected = TransverseMercator.forward(
            latDeg = -19.916681,
            lonDeg = -43.934493,
            centralMeridianDeg = -45.0,
            falseNorthing = 10_000_000.0,
        )
        val geographic = TransverseMercator.inverse(
            eastingM = projected.eastingM,
            northingM = projected.northingM,
            centralMeridianDeg = -45.0,
            falseNorthing = 10_000_000.0,
        )

        assertTrue(abs(geographic.latitudeDeg - (-19.916681)) < 1e-8)
        assertTrue(abs(geographic.longitudeDeg - (-43.934493)) < 1e-8)
    }
}
