package org.opentopo.transform

import kotlin.math.*

/**
 * Transverse Mercator projection (forward and inverse) on GRS80.
 */
object TransverseMercator {
    fun forward(
        latDeg: Double,
        lonDeg: Double,
        centralMeridianDeg: Double = 24.0,
        scaleFactor: Double = 0.9996,
        falseEasting: Double = 500_000.0,
        falseNorthing: Double = 0.0,
    ): ProjectedCoordinate {
        val phi = Math.toRadians(latDeg)
        val lam = Math.toRadians(lonDeg)
        val lam0 = Math.toRadians(centralMeridianDeg)

        val a = Ellipsoid.A
        val e2 = Ellipsoid.E2
        val ePrime2 = e2 / (1.0 - e2)

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val n = a / sqrt(1.0 - e2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = ePrime2 * cosPhi * cosPhi
        val aa = (lam - lam0) * cosPhi

        val e4 = e2 * e2
        val e6 = e4 * e2
        val m = a * (
            (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi
            - (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * phi)
            + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * phi)
            - (35.0 * e6 / 3072.0) * sin(6.0 * phi)
        )

        val easting = falseEasting + scaleFactor * n * (
            aa
            + (1.0 - t + c) * aa.pow(3) / 6.0
            + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ePrime2) * aa.pow(5) / 120.0
        )

        val northing = falseNorthing + scaleFactor * (
            m + n * tanPhi * (
                aa * aa / 2.0
                + (5.0 - t + 9.0 * c + 4.0 * c * c) * aa.pow(4) / 24.0
                + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ePrime2) * aa.pow(6) / 720.0
            )
        )

        return ProjectedCoordinate(eastingM = easting, northingM = northing)
    }

    fun inverse(
        eastingM: Double,
        northingM: Double,
        centralMeridianDeg: Double = 24.0,
        scaleFactor: Double = 0.9996,
        falseEasting: Double = 500_000.0,
        falseNorthing: Double = 0.0,
        heightM: Double = 0.0,
    ): GeographicCoordinate {
        require(scaleFactor > 0.0) { "scaleFactor must be positive" }

        val a = Ellipsoid.A
        val e2 = Ellipsoid.E2
        val ePrime2 = e2 / (1.0 - e2)
        val e4 = e2 * e2
        val e6 = e4 * e2

        val m = (northingM - falseNorthing) / scaleFactor
        val mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0))

        val sqrtOneMinusE2 = sqrt(1.0 - e2)
        val e1 = (1.0 - sqrtOneMinusE2) / (1.0 + sqrtOneMinusE2)

        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0) * sin(2.0 * mu) +
            (21.0 * e1 * e1 / 16.0 - 55.0 * e1.pow(4) / 32.0) * sin(4.0 * mu) +
            (151.0 * e1.pow(3) / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1.pow(4) / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)

        val n1 = a / sqrt(1.0 - e2 * sinPhi1 * sinPhi1)
        val r1 = a * (1.0 - e2) / (1.0 - e2 * sinPhi1 * sinPhi1).pow(1.5)
        val t1 = tanPhi1 * tanPhi1
        val c1 = ePrime2 * cosPhi1 * cosPhi1
        val d = (eastingM - falseEasting) / (n1 * scaleFactor)

        val latRad = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2.0
            - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ePrime2) * d.pow(4) / 24.0
            + (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ePrime2 - 3.0 * c1 * c1) * d.pow(6) / 720.0
        )

        val lonRad = Math.toRadians(centralMeridianDeg) + (
            d
            - (1.0 + 2.0 * t1 + c1) * d.pow(3) / 6.0
            + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ePrime2 + 24.0 * t1 * t1) * d.pow(5) / 120.0
        ) / cosPhi1

        return GeographicCoordinate(
            latitudeDeg = Math.toDegrees(latRad),
            longitudeDeg = Math.toDegrees(lonRad),
            heightM = heightM,
        )
    }
}
