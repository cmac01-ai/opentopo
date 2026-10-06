package org.opentopo.app.gnss

import kotlin.math.abs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NmeaParserTest {

    // ── Checksum ──

    @Test
    fun `valid checksum passes`() {
        assertTrue(NmeaParser.verifyChecksum("\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76"))
    }

    @Test
    fun `invalid checksum fails`() {
        assertFalse(NmeaParser.verifyChecksum("\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*FF"))
    }

    // ── GGA ──

    @Test
    fun `parse GGA sentence`() {
        var result: GgaData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { result = data }
        })

        parser.parseLine("\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76")

        assertNotNull(result)
        val gga = result!!
        assertEquals("092750.000", gga.time)
        assertTrue(abs(gga.latitude!! - 53.361337) < 0.0001, "lat: ${gga.latitude}")
        assertTrue(abs(gga.longitude!! - (-6.505620)) < 0.0001, "lon: ${gga.longitude}")
        assertEquals(1, gga.quality)
        assertEquals(8, gga.numSatellites)
        assertEquals(1.03, gga.hdop)
        assertEquals(61.7, gga.altitude)
        assertEquals(55.2, gga.geoidSeparation)
    }

    @Test
    fun `parse GGA with RTK fix quality 4`() {
        var result: GgaData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { result = data }
        })

        parser.parseLine("\$GNGGA,120530.00,3703.8800,N,02206.8580,E,4,12,0.8,45.3,M,36.1,M,1.0,*64")

        assertNotNull(result)
        assertEquals(4, result!!.quality)
        assertEquals("RTK Fix", result!!.fixDescription)
        assertEquals(12, result!!.numSatellites)
        assertTrue(abs(result!!.latitude!! - 37.0646667) < 0.0001)
        assertTrue(abs(result!!.longitude!! - 22.1143) < 0.0001)
    }

    @Test
    fun `parse GGA with empty position`() {
        var result: GgaData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { result = data }
        })

        parser.parseLine("\$GPGGA,235959.00,,,,,0,0,,,M,,M,,*79")

        assertNotNull(result)
        assertNull(result!!.latitude)
        assertNull(result!!.longitude)
        assertEquals(0, result!!.quality)
    }

    // ── RMC ──

    @Test
    fun `parse RMC sentence`() {
        var result: RmcData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onRmc(data: RmcData) { result = data }
        })

        parser.parseLine("\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A")

        assertNotNull(result)
        val rmc = result!!
        assertEquals('A', rmc.status)
        assertTrue(abs(rmc.latitude!! - 48.1173) < 0.001)
        assertTrue(abs(rmc.longitude!! - 11.5167) < 0.001)
        assertEquals(22.4, rmc.speedKnots)
        assertEquals(84.4, rmc.courseTrue)
        assertEquals("230394", rmc.date)
    }

    // ── GSA ──

    @Test
    fun `parse GSA sentence`() {
        var result: GsaData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGsa(data: GsaData) { result = data }
        })

        parser.parseLine("\$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39")

        assertNotNull(result)
        val gsa = result!!
        assertEquals('A', gsa.mode)
        assertEquals(3, gsa.fixType)
        assertEquals(listOf(4, 5, 9, 12, 24), gsa.satellitePrns)
        assertEquals(2.5, gsa.pdop)
        assertEquals(1.3, gsa.hdop)
        assertEquals(2.1, gsa.vdop)
    }

    // ── GSV ──

    @Test
    fun `parse GSV sentence`() {
        var result: GsvData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGsv(data: GsvData) { result = data }
        })

        parser.parseLine("\$GPGSV,3,1,11,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00*74")

        assertNotNull(result)
        val gsv = result!!
        assertEquals(Constellation.GPS, gsv.constellation)
        assertEquals(3, gsv.totalMessages)
        assertEquals(1, gsv.messageNumber)
        assertEquals(11, gsv.totalSatellites)
        assertEquals(4, gsv.satellites.size)
        assertEquals(3, gsv.satellites[0].prn)
        assertEquals(3, gsv.satellites[0].elevationDeg)
        assertEquals(111, gsv.satellites[0].azimuthDeg)
        assertEquals(0, gsv.satellites[0].snrDb)
    }

    @Test
    fun `parse Galileo GSV`() {
        var result: GsvData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGsv(data: GsvData) { result = data }
        })

        parser.parseLine("\$GAGSV,1,1,04,02,40,310,43,08,25,180,40,11,55,090,45,36,15,270,38*6A")

        assertNotNull(result)
        assertEquals(Constellation.GALILEO, result!!.constellation)
        assertEquals(4, result!!.satellites.size)
    }

    // ── GST ──

    @Test
    fun `parse GST sentence`() {
        var result: GstData? = null
        val parser = NmeaParser(object : NmeaListener {
            override fun onGst(data: GstData) { result = data }
        })

        parser.parseLine("\$GPGST,082356.00,1.8,,,,0.014,0.010,0.023*7F")

        assertNotNull(result)
        val gst = result!!
        assertEquals("082356.00", gst.time)
        assertEquals(1.8, gst.rmsResidual)
        assertEquals(0.014, gst.latitudeErrorM)
        assertEquals(0.010, gst.longitudeErrorM)
        assertEquals(0.023, gst.altitudeErrorM)
    }

    // ── Feed (streaming) ──

    @Test
    fun `feed handles partial lines across multiple calls`() {
        var count = 0
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { count++ }
        })

        val full = "\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76\r\n"
        // Split the sentence in the middle
        val part1 = full.substring(0, 30)
        val part2 = full.substring(30)

        parser.feed(part1.toByteArray())
        assertEquals(0, count, "Should not have parsed yet")
        parser.feed(part2.toByteArray())
        assertEquals(1, count, "Should have parsed after receiving full line")
    }

    @Test
    fun `feed handles multiple sentences in one chunk`() {
        var ggaCount = 0
        var rmcCount = 0
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { ggaCount++ }
            override fun onRmc(data: RmcData) { rmcCount++ }
        })

        val data = "\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76\r\n" +
            "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A\r\n"

        parser.feed(data.toByteArray())
        assertEquals(1, ggaCount)
        assertEquals(1, rmcCount)
    }

    @Test
    fun `feed resynchronises after binary bytes before NMEA`() {
        var count = 0
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { count++ }
        })

        val binary = byteArrayOf(0xB5.toByte(), 0x62, 0x01, 0x07, 0x10, 0x20, 0x30)
        val gga = "\u0024GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76\r\n"
        parser.feed(binary + gga.toByteArray())

        assertEquals(1, count)
    }

    @Test
    fun `feed recovers after binary interrupts partial NMEA`() {
        var count = 0
        val parser = NmeaParser(object : NmeaListener {
            override fun onGga(data: GgaData) { count++ }
        })

        parser.feed("\u0024GPGGA,0927".toByteArray())
        parser.feed(byteArrayOf(0xB5.toByte(), 0x62))
        parser.feed("\u0024GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76\r\n".toByteArray())

        assertEquals(1, count)
    }

}
