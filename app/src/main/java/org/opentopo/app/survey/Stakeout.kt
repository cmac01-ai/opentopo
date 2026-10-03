package org.opentopo.app.survey

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import org.opentopo.app.coordinates.CoordinateSystemService
import org.opentopo.app.gnss.GnssState
import org.opentopo.transform.GeographicCoordinate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Stakeout navigation — computes live delta to a target point.
 */
class Stakeout(
    /**
     * Shared [GnssState] pipeline. Public so that the Stakeout UI panel can surface
     * σH and satellite count in its status pill without duplicating the flow
     * through separate parameters.
     */
    val gnssState: GnssState,
    private val coordinateSystem: CoordinateSystemService,
) {

    private val _target = MutableStateFlow<StakeoutTarget?>(null)

    /** Observable target — used by StakeoutPanel to react to externally-set targets. */
    val target: kotlinx.coroutines.flow.StateFlow<StakeoutTarget?> = _target

    fun setTarget(target: StakeoutTarget?) {
        _target.value = target
    }

    /** Flow of live stakeout results, updated whenever position or target changes. */
    val result: Flow<StakeoutResult?> = combine(
        gnssState.position,
        _target,
    ) { position, target ->
        if (target == null || !position.hasFix) return@combine null

        val currentProjected = coordinateSystem.project(
            GeographicCoordinate(position.latitude, position.longitude, position.altitude ?: 0.0)
        ).coordinate

        val deltaE = target.easting - currentProjected.eastingM
        val deltaN = target.northing - currentProjected.northingM
        val distance = sqrt(deltaE * deltaE + deltaN * deltaN)
        val bearingRad = atan2(deltaE, deltaN)
        var bearingDeg = bearingRad * 180.0 / PI
        if (bearingDeg < 0) bearingDeg += 360.0

        StakeoutResult(
            target = target,
            currentEasting = currentProjected.eastingM,
            currentNorthing = currentProjected.northingM,
            deltaEasting = deltaE,
            deltaNorthing = deltaN,
            distance = distance,
            bearingDeg = bearingDeg,
        )
    }
}

data class StakeoutTarget(
    val name: String,
    val easting: Double,      // E in the active project's CRS
    val northing: Double,     // N in the active project's CRS
    val elevation: Double? = null,
)

data class StakeoutResult(
    val target: StakeoutTarget,
    val currentEasting: Double,
    val currentNorthing: Double,
    val deltaEasting: Double,
    val deltaNorthing: Double,
    val distance: Double,
    val bearingDeg: Double,
) {
    val bearingCardinal: String
        get() {
            val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((bearingDeg + 22.5) / 45.0).toInt() % 8
            return dirs[index]
        }
}
