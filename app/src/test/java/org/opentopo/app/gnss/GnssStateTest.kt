package org.opentopo.app.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssStateTest {

    @Test
    fun `keeps active satellites separated by constellation`() {
        val state = GnssState()

        state.onGsa(
            GsaData(
                mode = 'A',
                fixType = 3,
                satellitePrns = listOf(4, 5, 9),
                pdop = 1.0,
                hdop = 0.7,
                vdop = 0.8,
                constellation = Constellation.GPS,
            )
        )
        state.onGsa(
            GsaData(
                mode = 'A',
                fixType = 3,
                satellitePrns = listOf(2, 8),
                pdop = 1.0,
                hdop = 0.7,
                vdop = 0.8,
                constellation = Constellation.GALILEO,
            )
        )

        val keys = state.accuracy.value.activeSatelliteKeys
        assertEquals(5, keys.size)
        assertEquals(3, keys.count { it.constellation == Constellation.GPS })
        assertEquals(2, keys.count { it.constellation == Constellation.GALILEO })
        assertTrue(SatelliteKey(Constellation.GPS, 4) in keys)
        assertTrue(SatelliteKey(Constellation.GALILEO, 2) in keys)
    }

    @Test
    fun `merges GSV sequences from multiple signal ids for the same constellation`() {
        val state = GnssState()

        state.onGsv(
            GsvData(
                constellation = Constellation.GPS,
                signalId = 1,
                totalMessages = 1,
                messageNumber = 1,
                totalSatellites = 1,
                satellites = listOf(
                    SatelliteInfo(4, 45, 120, 40, Constellation.GPS)
                ),
            )
        )
        state.onGsv(
            GsvData(
                constellation = Constellation.GPS,
                signalId = 6,
                totalMessages = 1,
                messageNumber = 1,
                totalSatellites = 1,
                satellites = listOf(
                    SatelliteInfo(9, 50, 180, 42, Constellation.GPS)
                ),
            )
        )

        assertEquals(2, state.satellites.value.totalInView)
        assertEquals(setOf(4, 9), state.satellites.value.satellites.map { it.prn }.toSet())
    }

}
