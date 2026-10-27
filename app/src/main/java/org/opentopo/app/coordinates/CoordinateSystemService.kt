package org.opentopo.app.coordinates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opentopo.transform.GeographicCoordinate
import org.opentopo.transform.HeposTransform
import org.opentopo.transform.ProjectedCoordinate
import org.opentopo.transform.SirgasUtmTransform
import org.opentopo.transform.UtmHemisphere

enum class CoordinateSystemType(val storedValue: String) {
    SIRGAS2000_UTM("SIRGAS2000_UTM"),
    EGSA87("EGSA87");

    companion object {
        fun fromStored(value: String): CoordinateSystemType =
            values().firstOrNull { it.storedValue == value } ?: SIRGAS2000_UTM
    }
}

enum class UtmHemisphereMode(val storedValue: String) {
    AUTO("AUTO"),
    NORTH("NORTH"),
    SOUTH("SOUTH");

    companion object {
        fun fromStored(value: String?): UtmHemisphereMode =
            values().firstOrNull { it.storedValue == value } ?: AUTO
    }

    fun override(): UtmHemisphere? = when (this) {
        AUTO -> null
        NORTH -> UtmHemisphere.NORTH
        SOUTH -> UtmHemisphere.SOUTH
    }
}

data class CoordinateSystemConfig(
    val type: CoordinateSystemType = CoordinateSystemType.SIRGAS2000_UTM,
    /** Null means derive the UTM zone from longitude for every position. */
    val utmZone: Int? = null,
    val hemisphereMode: UtmHemisphereMode = UtmHemisphereMode.AUTO,
)

data class ProjectedCrsResult(
    val coordinate: ProjectedCoordinate,
    val label: String,
    val epsg: Int?,
    val utmZone: Int? = null,
    val utmHemisphere: UtmHemisphere? = null,
)

/**
 * Shared projection service used by survey and stakeout.
 *
 * The GNSS transport remains CRS-agnostic: it provides geographic coordinates,
 * while the active project's configuration decides how E/N are generated.
 */
class CoordinateSystemService(
    private val heposTransform: HeposTransform?,
) {
    private val _config = MutableStateFlow(CoordinateSystemConfig())
    val config: StateFlow<CoordinateSystemConfig> = _config.asStateFlow()

    fun setConfig(storedType: String, utmZone: Int?, utmHemisphere: String? = null) {
        val type = CoordinateSystemType.fromStored(storedType)
        val sanitizedZone = if (type == CoordinateSystemType.SIRGAS2000_UTM) {
            utmZone?.takeIf { it in 1..60 }
        } else {
            null
        }
        _config.value = CoordinateSystemConfig(
            type = type,
            utmZone = sanitizedZone,
            hemisphereMode = UtmHemisphereMode.fromStored(utmHemisphere),
        )
    }

    fun project(coordinate: GeographicCoordinate): ProjectedCrsResult {
        val current = _config.value
        return when (current.type) {
            CoordinateSystemType.SIRGAS2000_UTM -> {
                val utm = SirgasUtmTransform.forward(
                    coordinate = coordinate,
                    zoneOverride = current.utmZone,
                    hemisphereOverride = current.hemisphereMode.override(),
                )
                val epsg = utm.epsg
                ProjectedCrsResult(
                    coordinate = ProjectedCoordinate(utm.eastingM, utm.northingM),
                    label = buildString {
                        append("SIRGAS2000 · UTM ")
                        append(utm.zone)
                        append(utm.hemisphere.code)
                        if (epsg != null) append(" · EPSG ").append(epsg)
                    },
                    epsg = epsg,
                    utmZone = utm.zone,
                    utmHemisphere = utm.hemisphere,
                )
            }

            CoordinateSystemType.EGSA87 -> {
                val transform = heposTransform
                    ?: error("HEPOS correction grids are not available")
                ProjectedCrsResult(
                    coordinate = transform.forward(coordinate),
                    label = "EGSA87 · EPSG 2100",
                    epsg = 2100,
                )
            }
        }
    }
}
