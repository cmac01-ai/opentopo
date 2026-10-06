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
}
