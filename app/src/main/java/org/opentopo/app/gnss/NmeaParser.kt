package org.opentopo.app.gnss

import kotlin.math.floor

/**
 * NMEA 0183 sentence parser.
 *
 * Parses GGA, RMC, GSA, GSV, and GST sentences from any talker ID
 * (GP, GL, GA, GB, GN). Pure Kotlin — no Android dependencies.
 *
 * Usage: call [parseLine] for each complete NMEA sentence.
 * Register a [NmeaListener] to receive parsed data.
 */
class NmeaParser(private val listener: NmeaListener) {

    private val lineBuffer = StringBuilder()

    /**
     * Feed a stream that may contain NMEA mixed with UBX/RTCM binary data.
     * Resynchronises on byte 0x24 (the NMEA start marker).
     */
    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        val endIndex = (offset + length).coerceAtMost(data.size)
        var collecting = lineBuffer.isNotEmpty()

        for (i in offset until endIndex) {
            val value = data[i].toInt() and 0xFF

            when {
                value == 0x24 -> {
                    lineBuffer.clear()
                    lineBuffer.append(value.toChar())
                    collecting = true
                }

                !collecting -> Unit

                value == 0x0A -> {
                    val line = lineBuffer.toString().trimEnd('\r')
                    lineBuffer.clear()
                    collecting = false
                    parseLine(line)
                }

                value == 0x0D || value in 0x20..0x7E -> {
                    if (lineBuffer.length < 512) {
                        lineBuffer.append(value.toChar())
                    } else {
                        lineBuffer.clear()
                        collecting = false
                    }
                }

                else -> {
                    lineBuffer.clear()
                    collecting = false
                }
            }
        }
    }

    /** Parse a single NMEA sentence string (e.g., "$GPGGA,...*XX"). */
    fun parseLine(sentence: String) {
        if (!sentence.startsWith("$")) return
        if (!verifyChecksum(sentence)) return

        // Strip $ and checksum, split fields
        val body = sentence.substring(1).substringBefore('*')
        val fields = body.split(',')
        if (fields.isEmpty()) return

        val sentenceId = fields[0]
        // Extract the sentence type (last 3 chars): e.g., "GPGGA" -> "GGA"
        val type = if (sentenceId.length >= 3) sentenceId.takeLast(3) else return

        when (type) {
            "GGA" -> { parseGga(fields); listener.onRawGga(sentence) }
            "RMC" -> parseRmc(fields)
            "GSA" -> parseGsa(fields, sentenceId.take(2))
            "GSV" -> parseGsv(fields)
            "GST" -> parseGst(fields)
        }
    }

    // ── GGA: Global Positioning System Fix Data ──

    private fun parseGga(fields: List<String>) {
        if (fields.size < 15) return
        val gga = GgaData(
            time = fields[1],
            latitude = parseLatitude(fields[2], fields[3]),
            longitude = parseLongitude(fields[4], fields[5]),
            quality = fields[6].toIntOrNull() ?: 0,
            numSatellites = fields[7].toIntOrNull() ?: 0,
            hdop = fields[8].toDoubleOrNull(),
            altitude = fields[9].toDoubleOrNull(),
            geoidSeparation = fields[11].toDoubleOrNull(),
            ageOfDgps = fields[13].toDoubleOrNull(),
        )
        listener.onGga(gga)
    }

    // ── RMC: Recommended Minimum ──

    private fun parseRmc(fields: List<String>) {
        if (fields.size < 12) return
        val rmc = RmcData(
            time = fields[1],
            status = fields[2].firstOrNull() ?: 'V',
            latitude = parseLatitude(fields[3], fields[4]),
            longitude = parseLongitude(fields[5], fields[6]),
            speedKnots = fields[7].toDoubleOrNull(),
            courseTrue = fields[8].toDoubleOrNull(),
            date = fields[9],
        )
        listener.onRmc(rmc)
    }

    // ── GSA: DOP and Active Satellites ──

    private fun parseGsa(fields: List<String>, talker: String) {
        if (fields.size < 18) return
        val prns = (3..14).mapNotNull { fields.getOrNull(it)?.toIntOrNull() }
        val systemId = fields.getOrNull(18)?.toIntOrNull()
        val constellation = when (talker) {
            "GP" -> Constellation.GPS
            "GL" -> Constellation.GLONASS
            "GA" -> Constellation.GALILEO
            "GB", "BD" -> Constellation.BEIDOU
            "GN" -> when (systemId) {
                1 -> Constellation.GPS
                2 -> Constellation.GLONASS
                3 -> Constellation.GALILEO
                4 -> Constellation.BEIDOU
                else -> Constellation.UNKNOWN
            }
            else -> Constellation.UNKNOWN
        }
        val gsa = GsaData(
            mode = fields[1].firstOrNull() ?: 'A',
            fixType = fields[2].toIntOrNull() ?: 1,
            satellitePrns = prns,
            pdop = fields[15].toDoubleOrNull(),
            hdop = fields[16].toDoubleOrNull(),
            vdop = fields[17].toDoubleOrNull(),
            constellation = constellation,
        )
        listener.onGsa(gsa)
    }

    // ── GSV: Satellites in View ──

    private fun parseGsv(fields: List<String>) {
        if (fields.size < 4) return
        val talker = fields[0].take(2)
        val constellation = when (talker) {
            "GP" -> Constellation.GPS
            "GL" -> Constellation.GLONASS
            "GA" -> Constellation.GALILEO
            "GB", "BD" -> Constellation.BEIDOU
            else -> Constellation.UNKNOWN
        }
        val totalMessages = fields[1].toIntOrNull() ?: return
        val messageNumber = fields[2].toIntOrNull() ?: return
        val totalSats = fields[3].toIntOrNull() ?: 0

        val satellites = mutableListOf<SatelliteInfo>()
        var i = 4
        while (i + 3 < fields.size) {
            val prn = fields[i].toIntOrNull()
            val elevation = fields[i + 1].toIntOrNull()
            val azimuth = fields[i + 2].toIntOrNull()
            val snr = fields[i + 3].substringBefore('*').toIntOrNull()
            if (prn != null) {
                satellites.add(SatelliteInfo(prn, elevation, azimuth, snr, constellation))
            }
            i += 4
        }

        listener.onGsv(
            GsvData(
                constellation = constellation,
                totalMessages = totalMessages,
                messageNumber = messageNumber,
                totalSatellites = totalSats,
                satellites = satellites,
            )
        )
    }

    // ── GST: Pseudorange Noise Statistics ──

    private fun parseGst(fields: List<String>) {
        if (fields.size < 9) return
        val gst = GstData(
            time = fields[1],
            rmsResidual = fields[2].toDoubleOrNull(),
            semiMajorError = fields[3].toDoubleOrNull(),
            semiMinorError = fields[4].toDoubleOrNull(),
            orientationDeg = fields[5].toDoubleOrNull(),
            latitudeErrorM = fields[6].toDoubleOrNull(),
            longitudeErrorM = fields[7].toDoubleOrNull(),
            altitudeErrorM = fields[8].substringBefore('*').toDoubleOrNull(),
        )
        listener.onGst(gst)
    }

    // ── Coordinate parsing helpers ──

    /** Parse NMEA latitude "DDMM.MMMMM" + N/S. Returns degrees or null. */
    private fun parseLatitude(value: String, hemisphere: String): Double? {
        if (value.isBlank()) return null
        val raw = value.toDoubleOrNull() ?: return null
        val degrees = floor(raw / 100.0)
        val minutes = raw - degrees * 100.0
        val result = degrees + minutes / 60.0
        return if (hemisphere == "S") -result else result
    }

    /** Parse NMEA longitude "DDDMM.MMMMM" + E/W. Returns degrees or null. */
    private fun parseLongitude(value: String, hemisphere: String): Double? {
        if (value.isBlank()) return null
        val raw = value.toDoubleOrNull() ?: return null
        val degrees = floor(raw / 100.0)
        val minutes = raw - degrees * 100.0
        val result = degrees + minutes / 60.0
        return if (hemisphere == "W") -result else result
    }

    companion object {
        /** Verify the NMEA checksum (XOR of all chars between $ and *). */
        fun verifyChecksum(sentence: String): Boolean {
            val starIdx = sentence.indexOf('*')
            if (starIdx < 0 || starIdx + 2 >= sentence.length) return false
            val body = sentence.substring(1, starIdx)
            var computed = 0
            for (c in body) {
                computed = computed xor c.code
            }
            val expected = sentence.substring(starIdx + 1).take(2).uppercase()
            val actual = computed.toString(16).uppercase().padStart(2, '0')
            return actual == expected
        }
    }
}

// ── Data classes ──

data class GgaData(
    val time: String,
    val latitude: Double?,
    val longitude: Double?,
    val quality: Int,            // 0=invalid, 1=GPS, 2=DGPS, 4=RTK fix, 5=RTK float
    val numSatellites: Int,
    val hdop: Double?,
    val altitude: Double?,       // metres above geoid
    val geoidSeparation: Double?,
    val ageOfDgps: Double?,
) {
    val fixDescription: String
        get() = when (quality) {
            0 -> "No fix"
            1 -> "GPS"
            2 -> "DGPS"
            4 -> "RTK Fix"
            5 -> "RTK Float"
            else -> "Unknown ($quality)"
        }
}

data class RmcData(
    val time: String,
    val status: Char,            // A=active, V=void
    val latitude: Double?,
    val longitude: Double?,
    val speedKnots: Double?,
    val courseTrue: Double?,
    val date: String,
)

data class GsaData(
    val mode: Char,              // A=auto, M=manual
    val fixType: Int,            // 1=no fix, 2=2D, 3=3D
    val satellitePrns: List<Int>,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
    val constellation: Constellation = Constellation.UNKNOWN,
)

data class GsvData(
    val constellation: Constellation,
    val totalMessages: Int,
    val messageNumber: Int,
    val totalSatellites: Int,
    val satellites: List<SatelliteInfo>,
)

data class SatelliteInfo(
    val prn: Int,
    val elevationDeg: Int?,
    val azimuthDeg: Int?,
    val snrDb: Int?,
    val constellation: Constellation,
)

data class GstData(
    val time: String,
    val rmsResidual: Double?,
    val semiMajorError: Double?,
    val semiMinorError: Double?,
    val orientationDeg: Double?,
    val latitudeErrorM: Double?,
    val longitudeErrorM: Double?,
    val altitudeErrorM: Double?,
)

enum class Constellation {
    GPS, GLONASS, GALILEO, BEIDOU, UNKNOWN
}

/** Callback interface for parsed NMEA data. */
interface NmeaListener {
    fun onGga(data: GgaData) {}
    fun onRawGga(sentence: String) {}
    fun onRmc(data: RmcData) {}
    fun onGsa(data: GsaData) {}
    fun onGsv(data: GsvData) {}
    fun onGst(data: GstData) {}
}
