package org.opentopo.app.geoid

import java.io.File
import kotlin.math.floor

/**
 * Regular 3-column grid used by IBGE models:
 *   longitude(0..360) latitude(deg) value(m)
 *
 * The official hgeoHNOR2020 / MAPGEO2015 files supplied by IBGE are ordered
 * north-to-south by latitude and west-to-east by longitude at 5 arc-minute spacing.
 */
class RegularGeoidGrid private constructor(
    private val lonMin: Double,
    private val lonMax: Double,
    private val latMin: Double,
    private val latMax: Double,
    private val lonStep: Double,
    private val latStep: Double,
    private val cols: Int,
    private val rows: Int,
    private val values: FloatArray,
) {
    fun interpolate(latitude: Double, longitude: Double): Double? {
        val lon360 = if (longitude < 0.0) longitude + 360.0 else longitude
        if (latitude < latMin || latitude > latMax || lon360 < lonMin || lon360 > lonMax) {
            return null
        }
        if (rows < 2 || cols < 2) return null

        val x = (lon360 - lonMin) / lonStep
        val y = (latMax - latitude) / latStep

        val c0 = floor(x).toInt().coerceIn(0, cols - 2)
        val r0 = floor(y).toInt().coerceIn(0, rows - 2)
        val tx = (x - c0).coerceIn(0.0, 1.0)
        val ty = (y - r0).coerceIn(0.0, 1.0)

        fun v(r: Int, c: Int): Double = values[r * cols + c].toDouble()

        val v00 = v(r0, c0)
        val v10 = v(r0, c0 + 1)
        val v01 = v(r0 + 1, c0)
        val v11 = v(r0 + 1, c0 + 1)

        val top = v00 * (1.0 - tx) + v10 * tx
        val bottom = v01 * (1.0 - tx) + v11 * tx
        return top * (1.0 - ty) + bottom * ty
    }

    data class Metadata(
        val lonMin: Double,
        val lonMax: Double,
        val latMin: Double,
        val latMax: Double,
        val lonStep: Double,
        val latStep: Double,
        val cols: Int,
        val rows: Int,
    )

    fun metadata(): Metadata = Metadata(
        lonMin = lonMin,
        lonMax = lonMax,
        latMin = latMin,
        latMax = latMax,
        lonStep = lonStep,
        latStep = latStep,
        cols = cols,
        rows = rows,
    )

    companion object {
        fun load(file: File): RegularGeoidGrid {
            require(file.exists()) { "Arquivo de grade não encontrado: ${file.name}" }

            val values = ArrayList<Float>(270_000)
            var lonMin = Double.POSITIVE_INFINITY
            var lonMax = Double.NEGATIVE_INFINITY
            var latMin = Double.POSITIVE_INFINITY
            var latMax = Double.NEGATIVE_INFINITY
            var firstLat: Double? = null
            var firstLon: Double? = null
            var secondLon: Double? = null
            var cols = 0
            var firstRowComplete = false

            file.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isBlank()) return@forEach
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 3) return@forEach

                    val lon = parts[0].toDoubleOrNull() ?: return@forEach
                    val lat = parts[1].toDoubleOrNull() ?: return@forEach
                    val value = parts[2].toFloatOrNull() ?: return@forEach

                    if (firstLat == null) {
                        firstLat = lat
                        firstLon = lon
                    }
                    if (!firstRowComplete) {
                        if (lat == firstLat) {
                            cols++
                            if (cols == 2) secondLon = lon
                        } else {
                            firstRowComplete = true
                        }
                    }

                    lonMin = minOf(lonMin, lon)
                    lonMax = maxOf(lonMax, lon)
                    latMin = minOf(latMin, lat)
                    latMax = maxOf(latMax, lat)
                    values.add(value)
                }
            }

            require(cols > 1 && values.size % cols == 0) {
                "Grade IBGE inválida ou incompleta: ${file.name}"
            }
            val rows = values.size / cols
            val lonStep = (secondLon ?: error("Passo longitudinal não identificado")) -
                (firstLon ?: error("Longitude inicial não identificada"))
            val latStep = if (rows > 1) (latMax - latMin) / (rows - 1) else 0.0

            return RegularGeoidGrid(
                lonMin = lonMin,
                lonMax = lonMax,
                latMin = latMin,
                latMax = latMax,
                lonStep = lonStep,
                latStep = latStep,
                cols = cols,
                rows = rows,
                values = FloatArray(values.size) { values[it] },
            )
        }
    }
}
