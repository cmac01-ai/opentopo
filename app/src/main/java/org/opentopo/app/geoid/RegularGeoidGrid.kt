package org.opentopo.app.geoid

import java.io.File
import kotlin.math.floor

/**
 * Regular 3-column grid used by IBGE models:
 *   longitude(0..360) latitude(deg) value(m)
 *
 * The official hgeoHNOR2020 / MAPGEO2015 files supplied by IBGE are ordered
 * north-to-south by latitude and west-to-east by longitude at 5 arc-minute spacing.
 *
 * IBGE's hgeoHNOR2020 online service uses bicubic spline interpolation.  We mirror
 * that approach as a tensor-product natural cubic spline: first along longitude for
 * every row, then along latitude.  This is intentionally different from the simpler
 * bilinear interpolation used in the first OpenTopo implementation.
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
    private val lonSecondDerivatives: FloatArray,
) {
    fun interpolate(latitude: Double, longitude: Double): Double? {
        val lon360 = if (longitude < 0.0) longitude + 360.0 else longitude
        if (latitude < latMin || latitude > latMax || lon360 < lonMin || lon360 > lonMax) {
            return null
        }
        if (rows < 2 || cols < 2) return null

        val x = ((lon360 - lonMin) / lonStep).coerceIn(0.0, (cols - 1).toDouble())
        val y = ((latMax - latitude) / latStep).coerceIn(0.0, (rows - 1).toDouble())

        // Interpolate every latitude row at the requested longitude. The second
        // derivatives along longitude are precomputed once when the grid is loaded.
        val rowValues = DoubleArray(rows)
        for (row in 0 until rows) {
            rowValues[row] = splineAtRow(row, x)
        }

        // Then spline those row values in the latitude direction. Because spline
        // interpolation is linear in the supplied ordinates, this is the standard
        // tensor-product bicubic spline construction used for regular grids.
        val latSecondDerivatives = naturalSecondDerivatives(rowValues)
        return splineEvaluate(rowValues, latSecondDerivatives, y)
    }

    private fun splineAtRow(row: Int, x: Double): Double {
        val base = row * cols
        val clamped = x.coerceIn(0.0, (cols - 1).toDouble())
        val lo = floor(clamped).toInt().coerceAtMost(cols - 2)
        val hi = lo + 1
        val b = clamped - lo
        val a = 1.0 - b
        val y0 = values[base + lo].toDouble()
        val y1 = values[base + hi].toDouble()
        val y20 = lonSecondDerivatives[base + lo].toDouble()
        val y21 = lonSecondDerivatives[base + hi].toDouble()
        return a * y0 + b * y1 +
            (((a * a * a) - a) * y20 + ((b * b * b) - b) * y21) / 6.0
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
            val flatValues = FloatArray(values.size) { values[it] }

            // Natural cubic-spline second derivatives for every longitude row.
            val lonY2 = FloatArray(flatValues.size)
            for (row in 0 until rows) {
                val rowValues = DoubleArray(cols) { col ->
                    flatValues[row * cols + col].toDouble()
                }
                val y2 = naturalSecondDerivatives(rowValues)
                for (col in 0 until cols) {
                    lonY2[row * cols + col] = y2[col].toFloat()
                }
            }

            return RegularGeoidGrid(
                lonMin = lonMin,
                lonMax = lonMax,
                latMin = latMin,
                latMax = latMax,
                lonStep = lonStep,
                latStep = latStep,
                cols = cols,
                rows = rows,
                values = flatValues,
                lonSecondDerivatives = lonY2,
            )
        }

        /**
         * Numerical-Recipes style natural cubic-spline second derivatives for
         * equally spaced samples (grid-index coordinates 0,1,2,...).
         */
        private fun naturalSecondDerivatives(y: DoubleArray): DoubleArray {
            val n = y.size
            if (n <= 2) return DoubleArray(n)

            val y2 = DoubleArray(n)
            val u = DoubleArray(n - 1)
            y2[0] = 0.0
            u[0] = 0.0

            for (i in 1 until n - 1) {
                val sig = 0.5
                val p = sig * y2[i - 1] + 2.0
                y2[i] = (sig - 1.0) / p
                val curvature = 3.0 * (y[i + 1] - 2.0 * y[i] + y[i - 1])
                u[i] = (curvature - sig * u[i - 1]) / p
            }

            y2[n - 1] = 0.0
            for (k in n - 2 downTo 0) {
                y2[k] = y2[k] * y2[k + 1] + u[k]
            }
            return y2
        }

        private fun splineEvaluate(y: DoubleArray, y2: DoubleArray, x: Double): Double {
            if (y.size == 1) return y[0]
            val clamped = x.coerceIn(0.0, (y.size - 1).toDouble())
            val lo = floor(clamped).toInt().coerceAtMost(y.size - 2)
            val hi = lo + 1
            val b = clamped - lo
            val a = 1.0 - b
            return a * y[lo] + b * y[hi] +
                (((a * a * a) - a) * y2[lo] + ((b * b * b) - b) * y2[hi]) / 6.0
        }
    }
}
