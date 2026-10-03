package org.opentopo.app.coordinates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opentopo.transform.GeographicCoordinate
import org.opentopo.transform.HeposTransform
import org.opentopo.transform.ProjectedCoordinate
import org.opentopo.transform.SirgasUtmTransform

enum class CoordinateSystemType(val storedValue: String) {
    SIRGAS2000_UTM("SIRGAS2000_UTM"),
    EGSA87("EGSA87");

    companion object {
        fun fromStored(value: String): CoordinateSystemType =
            values().firstOrNull { it.storedValue == value } ?: SIRGAS2000_UTM
    }
}

data class CoordinateSystemConfig(
    val type: CoordinateSystemType = CoordinateSystemType.SIRGAS2000_UTM,
    /** Null means derive the UTM zone from longitude for every position. */
    val utmZone: Int? = null,
)

data class ProjectedCrsResult(
    val coordinate: ProjectedCoordinate,
    val label: String,
    val epsg: Int?,
    val utmZone: Int? = null,
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

    fun setConfig(storedType: String, utmZone: Int?) {
        val type = CoordinateSystemType.fromStored(storedType)
        val sanitizedZone = if (type == CoordinateSystemType.SIRGAS2000_UTM) {
            utmZone?.takeIf { it in 1..60 }
        } else {
            null
        }
        _config.value = CoordinateSystemConfig(type, sanitizedZone)
    }

    fun project(coordinate: GeographicCoordinate): ProjectedCrsResult {
        val current = _config.value
        return when (current.type) {
            CoordinateSystemType.SIRGAS2000_UTM -> {
                val utm = SirgasUtmTransform.forward(
                    coordinate = coordinate,
                    zoneOverride = current.utmZone,
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
