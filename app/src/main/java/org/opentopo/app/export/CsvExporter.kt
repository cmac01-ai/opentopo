package org.opentopo.app.export

import org.opentopo.app.db.PointEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports survey points with SIRGAS2000/UTM metadata.
 */
object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun export(points: List<PointEntity>, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        writer.write(
            "ID,Attribute,Easting_UTM,Northing_UTM,EPSG,UTM_Zone,Hemisphere," +
                "Latitude_SIRGAS2000,Longitude_SIRGAS2000,Ellipsoidal_Height,Physical_Height," +
                "Height_Model,Height_Factor,Height_Uncertainty,Antenna_Height," +
                "H_Accuracy,V_Accuracy,Fix,Satellites,HDOP,Averaging_s,DateTime,Remarks\n"
        )

        for (p in points) {
            writer.write(buildString {
                append(p.pointId).append(',')
                append(escapeCsv(p.attribute)).append(',')
                append(p.easting?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.northing?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.crsEpsg ?: "").append(',')
                append(p.utmZone ?: "").append(',')
                append(p.utmHemisphere ?: "").append(',')
                append("%.10f".format(Locale.US, p.latitude)).append(',')
                append("%.10f".format(Locale.US, p.longitude)).append(',')
                append(p.altitude?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.orthometricHeight?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.heightModel ?: "").append(',')
                append(p.geoidSeparation?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.heightUncertainty?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.antennaHeight?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.horizontalAccuracy?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(p.verticalAccuracy?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
                append(fixLabel(p.fixQuality)).append(',')
                append(p.numSatellites).append(',')
                append(p.hdop?.let { "%.1f".format(Locale.US, it) } ?: "").append(',')
                append(p.averagingSeconds).append(',')
                append(dateFormat.format(Date(p.timestamp))).append(',')
                append(escapeCsv(p.remarks))
                append('\n')
            })
        }

        writer.flush()
    }

    private fun fixLabel(quality: Int): String = when (quality) {
        4 -> "RTK_Fix"
        5 -> "RTK_Float"
        2 -> "DGPS"
        1 -> "GPS"
        else -> "None"
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
