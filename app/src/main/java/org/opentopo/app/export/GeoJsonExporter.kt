package org.opentopo.app.export

import org.opentopo.app.db.PointEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports RFC 7946 style GeoJSON using longitude/latitude geometry.
 * SIRGAS2000/UTM values are preserved as feature properties.
 */
object GeoJsonExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun export(points: List<PointEntity>, projectName: String, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)

        writer.write("""{"type":"FeatureCollection","name":""")
        writer.write(jsonString(projectName))
        writer.write(""","features":[""")

        points.forEachIndexed { index, p ->
            if (index > 0) writer.write(",")
            writer.write("""{"type":"Feature","properties":{""")
            writer.write(""""id":${jsonString(p.pointId)}""")
            writer.write(""","reference_system":"SIRGAS2000"""")
            p.easting?.let { writer.write(""","easting_utm":${"%.3f".format(Locale.US, it)}""") }
            p.northing?.let { writer.write(""","northing_utm":${"%.3f".format(Locale.US, it)}""") }
            p.crsEpsg?.let { writer.write(""","epsg":$it""") }
            p.utmZone?.let { writer.write(""","utm_zone":$it""") }
            p.utmHemisphere?.let { writer.write(""","hemisphere":${jsonString(it)}""") }
            p.altitude?.let { writer.write(""","altitude":${"%.3f".format(Locale.US, it)}""") }
            p.orthometricHeight?.let { writer.write(""","ortho_height":${"%.3f".format(Locale.US, it)}""") }
            p.geoidSeparation?.let { writer.write(""","geoid_n":${"%.3f".format(Locale.US, it)}""") }
            p.horizontalAccuracy?.let { writer.write(""","h_accuracy":${"%.3f".format(Locale.US, it)}""") }
            p.verticalAccuracy?.let { writer.write(""","v_accuracy":${"%.3f".format(Locale.US, it)}""") }
            writer.write(""","fix":${jsonString(fixLabel(p.fixQuality))}""")
            writer.write(""","satellites":${p.numSatellites}""")
            writer.write(""","timestamp":${jsonString(dateFormat.format(Date(p.timestamp)))}""")
            if (p.remarks.isNotBlank()) writer.write(""","remarks":${jsonString(p.remarks)}""")
            writer.write("}")

            writer.write(
                ""","geometry":{"type":"Point","coordinates":[${"%.10f".format(Locale.US, p.longitude)},${"%.10f".format(Locale.US, p.latitude)}]}"""
            )
            writer.write("}")
        }

        writer.write("]}")
        writer.flush()
    }

    private fun fixLabel(quality: Int): String = when (quality) {
        4 -> "RTK_Fix"
        5 -> "RTK_Float"
        2 -> "DGPS"
        1 -> "GPS"
        else -> "None"
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
