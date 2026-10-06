package org.opentopo.app.export

import org.opentopo.app.db.PointEntity
import org.opentopo.app.db.ProjectEntity
import org.opentopo.transform.SirgasUtmTransform
import org.opentopo.transform.UtmHemisphere
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports survey points as a Shapefile (SHP/SHX/DBF) packed in a ZIP.
 * The PRJ is generated as SIRGAS2000 / UTM for the project zone/hemisphere.
 */
object ShapefileExporter {

    fun export(points: List<PointEntity>, project: ProjectEntity, output: OutputStream) {
        val filtered = points.filter { it.layerType == "point" && it.easting != null && it.northing != null }
        require(filtered.isNotEmpty()) { "No projected point available for Shapefile export" }

        val zip = ZipOutputStream(output)

        val baseName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // Write .shp
        zip.putNextEntry(ZipEntry("$baseName.shp"))
        zip.write(buildShp(filtered))
        zip.closeEntry()

        // Write .shx
        zip.putNextEntry(ZipEntry("$baseName.shx"))
        zip.write(buildShx(filtered))
        zip.closeEntry()

        // Write .dbf
        zip.putNextEntry(ZipEntry("$baseName.dbf"))
        zip.write(buildDbf(filtered))
        zip.closeEntry()

        // Write .prj (SIRGAS2000 / UTM WKT)
        zip.putNextEntry(ZipEntry("$baseName.prj"))
        zip.write(sirgasUtmWkt(project, filtered).toByteArray())
        zip.closeEntry()

        zip.finish()
        zip.flush()
    }

    private fun buildShp(points: List<PointEntity>): ByteArray {
        val numRecords = points.size
        val recordLen = 20 // 4 (shape type) + 8 (x) + 8 (y)
        val fileLen = 50 + numRecords * (recordLen + 8) // header + records (each has 8-byte record header)

        val buf = ByteBuffer.allocate(fileLen * 2) // word size

        // File header (100 bytes)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.putInt(9994) // file code
        buf.position(24)
        buf.putInt(fileLen) // file length in 16-bit words
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1000) // version
        buf.putInt(1) // shape type: Point

        // Bounding box
        val minX = points.minOf { it.easting!! }
        val minY = points.minOf { it.northing!! }
        val maxX = points.maxOf { it.easting!! }
        val maxY = points.maxOf { it.northing!! }
        buf.putDouble(minX)
        buf.putDouble(minY)
        buf.putDouble(maxX)
        buf.putDouble(maxY)
        buf.putDouble(0.0) // minZ
        buf.putDouble(0.0) // maxZ
        buf.putDouble(0.0) // minM
        buf.putDouble(0.0) // maxM

        // Records
        for ((i, pt) in points.withIndex()) {
            buf.order(ByteOrder.BIG_ENDIAN)
            buf.putInt(i + 1) // record number (1-based)
            buf.putInt(10) // content length in 16-bit words (20 bytes / 2)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(1) // shape type: Point
            buf.putDouble(pt.easting!!)
            buf.putDouble(pt.northing!!)
        }

        return buf.array().copyOf(buf.position())
    }

    private fun buildShx(points: List<PointEntity>): ByteArray {
        val numRecords = points.size
        val fileLen = 50 + numRecords * 8 // header + 8 bytes per record

        val buf = ByteBuffer.allocate(fileLen * 2)

        // Header (same as SHP but different file length)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.putInt(9994)
        buf.position(24)
        buf.putInt(fileLen)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1000)
        buf.putInt(1) // Point

        val minX = points.minOfOrNull { it.easting!! } ?: 0.0
        val minY = points.minOfOrNull { it.northing!! } ?: 0.0
        val maxX = points.maxOfOrNull { it.easting!! } ?: 0.0
        val maxY = points.maxOfOrNull { it.northing!! } ?: 0.0
        buf.putDouble(minX); buf.putDouble(minY)
        buf.putDouble(maxX); buf.putDouble(maxY)
        buf.putDouble(0.0); buf.putDouble(0.0)
        buf.putDouble(0.0); buf.putDouble(0.0)

        // Index records
        var offset = 50 // first record starts after header
        for (i in points.indices) {
            buf.order(ByteOrder.BIG_ENDIAN)
            buf.putInt(offset)
            buf.putInt(10) // content length
            offset += 14 // 8 (record header) + 20 (content) / 2 = 14 words
        }

        return buf.array().copyOf(buf.position())
    }

    private fun buildDbf(points: List<PointEntity>): ByteArray {
        // DBF with fields: ID (C,10), EASTING (N,12,3), NORTHING (N,12,3), ELEV (N,10,3), FIX (N,2,0), REMARKS (C,50)
        val fields = listOf(
            DbfField("ID", 'C', 10, 0),
            DbfField("EASTING", 'N', 12, 3),
            DbfField("NORTHING", 'N', 12, 3),
            DbfField("ELEVATION", 'N', 10, 3),
            DbfField("ORTHO_H", 'N', 10, 3),
            DbfField("GEOID_N", 'N', 8, 3),
            DbfField("FIX_QUAL", 'N', 2, 0),
            DbfField("REMARKS", 'C', 50, 0),
        )
        val recordLen = 1 + fields.sumOf { it.length } // 1 for delete flag
        val headerLen = 32 + fields.size * 32 + 1 // +1 for header terminator

        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        // Header
        dos.writeByte(0x03) // version
        dos.writeByte(26) // year (2026 - 1900 = 126... use 26 for simplicity)
        dos.writeByte(4) // month
        dos.writeByte(12) // day
        dos.writeInt(Integer.reverseBytes(points.size)) // num records (LE)
        dos.writeShort(java.lang.Short.reverseBytes(headerLen.toShort()).toInt()) // header size (LE)
        dos.writeShort(java.lang.Short.reverseBytes(recordLen.toShort()).toInt()) // record size (LE)
        dos.write(ByteArray(20)) // reserved

        // Field descriptors
        for (f in fields) {
            val name = f.name.padEnd(11, '\u0000').substring(0, 11)
            dos.write(name.toByteArray(Charsets.US_ASCII))
            dos.writeByte(f.type.code)
            dos.write(ByteArray(4)) // reserved
            dos.writeByte(f.length)
            dos.writeByte(f.decimal)
            dos.write(ByteArray(14)) // reserved
        }
        dos.writeByte(0x0D) // header terminator

        // Records
        for (pt in points) {
            dos.writeByte(0x20) // not deleted
            dos.write(pt.pointId.padEnd(10).substring(0, 10).toByteArray(Charsets.US_ASCII))
            dos.write("%.3f".format(pt.easting ?: 0.0).padStart(12).substring(0, 12).toByteArray(Charsets.US_ASCII))
            dos.write("%.3f".format(pt.northing ?: 0.0).padStart(12).substring(0, 12).toByteArray(Charsets.US_ASCII))
            dos.write("%.3f".format(pt.altitude ?: 0.0).padStart(10).substring(0, 10).toByteArray(Charsets.US_ASCII))
            dos.write("%.3f".format(pt.orthometricHeight ?: 0.0).padStart(10).substring(0, 10).toByteArray(Charsets.US_ASCII))
            dos.write("%.3f".format(pt.geoidSeparation ?: 0.0).padStart(8).substring(0, 8).toByteArray(Charsets.US_ASCII))
            dos.write("${pt.fixQuality}".padStart(2).substring(0, 2).toByteArray(Charsets.US_ASCII))
            dos.write(pt.remarks.padEnd(50).substring(0, 50).toByteArray(Charsets.US_ASCII))
        }

        dos.writeByte(0x1A) // EOF marker
        dos.flush()
        return out.toByteArray()
    }

    private data class DbfField(val name: String, val type: Char, val length: Int, val decimal: Int)

    private fun sirgasUtmWkt(project: ProjectEntity, points: List<PointEntity>): String {
        val first = points.first()
        val zone = project.utmZone
            ?: first.utmZone
            ?: SirgasUtmTransform.zoneFromLongitude(first.longitude)
        val hemisphere = when (project.utmHemisphere) {
            "NORTH" -> UtmHemisphere.NORTH
            "SOUTH" -> UtmHemisphere.SOUTH
            else -> when (first.utmHemisphere) {
                "N" -> UtmHemisphere.NORTH
                "S" -> UtmHemisphere.SOUTH
                else -> if (first.latitude < 0.0) UtmHemisphere.SOUTH else UtmHemisphere.NORTH
            }
        }
        val centralMeridian = SirgasUtmTransform.centralMeridianDeg(zone)
        val falseNorthing = if (hemisphere == UtmHemisphere.SOUTH) 10_000_000 else 0
        val epsg = SirgasUtmTransform.epsg(zone, hemisphere)
        val authority = epsg?.let { ",AUTHORITY[\"EPSG\",\"$it\"]" } ?: ""

        return """PROJCS["SIRGAS 2000 / UTM zone $zone${hemisphere.code}",GEOGCS["SIRGAS 2000",DATUM["Sistema_de_Referencia_Geocentrico_para_las_Americas_2000",SPHEROID["GRS 1980",6378137,298.257222101]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433],AUTHORITY["EPSG","4674"]],PROJECTION["Transverse_Mercator"],PARAMETER["latitude_of_origin",0],PARAMETER["central_meridian",$centralMeridian],PARAMETER["scale_factor",0.9996],PARAMETER["false_easting",500000],PARAMETER["false_northing",$falseNorthing],UNIT["metre",1]$authority]"""
    }
}
