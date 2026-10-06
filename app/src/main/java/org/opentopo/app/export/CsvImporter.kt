package org.opentopo.app.export

import org.opentopo.app.db.PointEntity
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Imports both the Brazil CSV format and legacy OpenTopo CSV files.
 */
object CsvImporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun import(input: InputStream, projectId: Long): List<PointEntity> {
        val reader = input.bufferedReader()
        val rawHeader = reader.readLine() ?: return emptyList()
        val header = parseCsvFields(rawHeader).map { it.trim() }
        val newFormat = header.any { it.equals("Latitude_SIRGAS2000", ignoreCase = true) }

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                if (newFormat) parseBrazilLine(line, projectId, header)
                else parseLegacyLine(line, projectId, rawHeader)
            }
            .toList()
    }

    private fun parseBrazilLine(
        line: String,
        projectId: Long,
        header: List<String>,
    ): PointEntity? {
        val fields = parseCsvFields(line)
        fun value(vararg names: String): String? {
            val idx = header.indexOfFirst { h -> names.any { n -> h.equals(n, ignoreCase = true) } }
            return if (idx >= 0) fields.getOrNull(idx)?.trim() else null
        }

        val lat = value("Latitude_SIRGAS2000")?.toDoubleOrNull() ?: return null
        val lon = value("Longitude_SIRGAS2000")?.toDoubleOrNull() ?: return null

        return PointEntity(
            projectId = projectId,
            pointId = value("ID").orEmpty().ifBlank { "P" },
            easting = value("Easting_UTM")?.toDoubleOrNull(),
            northing = value("Northing_UTM")?.toDoubleOrNull(),
            crsEpsg = value("EPSG")?.toIntOrNull(),
            utmZone = value("UTM_Zone")?.toIntOrNull(),
            utmHemisphere = value("Hemisphere")?.uppercase()?.takeIf { it == "N" || it == "S" },
            latitude = lat,
            longitude = lon,
            altitude = value("Altitude")?.toDoubleOrNull(),
            orthometricHeight = value("Ortho_Height")?.toDoubleOrNull(),
            geoidSeparation = value("Geoid_N")?.toDoubleOrNull(),
            horizontalAccuracy = value("H_Accuracy")?.toDoubleOrNull(),
            verticalAccuracy = value("V_Accuracy")?.toDoubleOrNull(),
            fixQuality = value("Fix")?.let { parseFixLabel(it) } ?: 0,
            numSatellites = value("Satellites")?.toIntOrNull() ?: 0,
            hdop = value("HDOP")?.toDoubleOrNull(),
            averagingSeconds = value("Averaging_s")?.toIntOrNull() ?: 0,
            timestamp = value("DateTime")?.let { parseTimestamp(it) } ?: System.currentTimeMillis(),
            remarks = value("Remarks").orEmpty(),
        )
    }

    private fun parseLegacyLine(
        line: String,
        projectId: Long,
        header: String,
    ): PointEntity? {
        val fields = parseCsvFields(line)
        if (fields.size < 5) return null
        val hasGeoid = header.contains("Ortho_Height")
        val offset = if (hasGeoid) 2 else 0

        return try {
            PointEntity(
                projectId = projectId,
                pointId = fields[0].trim(),
                easting = fields.getOrNull(1)?.trim()?.toDoubleOrNull(),
                northing = fields.getOrNull(2)?.trim()?.toDoubleOrNull(),
                latitude = fields[3].trim().toDoubleOrNull() ?: return null,
                longitude = fields[4].trim().toDoubleOrNull() ?: return null,
                altitude = fields.getOrNull(5)?.trim()?.toDoubleOrNull(),
                orthometricHeight = if (hasGeoid) fields.getOrNull(6)?.trim()?.toDoubleOrNull() else null,
                geoidSeparation = if (hasGeoid) fields.getOrNull(7)?.trim()?.toDoubleOrNull() else null,
                horizontalAccuracy = fields.getOrNull(6 + offset)?.trim()?.toDoubleOrNull(),
                verticalAccuracy = fields.getOrNull(7 + offset)?.trim()?.toDoubleOrNull(),
                fixQuality = fields.getOrNull(8 + offset)?.trim()?.let { parseFixLabel(it) } ?: 0,
                numSatellites = fields.getOrNull(9 + offset)?.trim()?.toIntOrNull() ?: 0,
                hdop = fields.getOrNull(10 + offset)?.trim()?.toDoubleOrNull(),
                averagingSeconds = fields.getOrNull(11 + offset)?.trim()?.toIntOrNull() ?: 0,
                timestamp = fields.getOrNull(12 + offset)?.trim()?.let { parseTimestamp(it) }
                    ?: System.currentTimeMillis(),
                remarks = fields.getOrNull(13 + offset)?.trim() ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFixLabel(label: String): Int = when (label.uppercase()) {
        "RTK_FIX" -> 4
        "RTK_FLOAT" -> 5
        "DGPS" -> 2
        "GPS" -> 1
        "NONE" -> 0
        else -> label.toIntOrNull() ?: 0
    }

    private fun parseTimestamp(value: String): Long? =
        try { dateFormat.parse(value)?.time } catch (_: Exception) { null }

    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
