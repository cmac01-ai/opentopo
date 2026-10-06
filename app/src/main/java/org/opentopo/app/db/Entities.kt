package org.opentopo.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Stored CRS identifier. New Brazilian projects use SIRGAS2000_UTM. */
    @ColumnInfo(defaultValue = "EGSA87")
    val coordinateSystem: String = "SIRGAS2000_UTM",
    /** Null means automatic UTM zone derived from longitude. */
    val utmZone: Int? = null,
    /** AUTO, NORTH or SOUTH. Null from older projects is treated as AUTO. */
    val utmHemisphere: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val pointId: String,           // user-visible auto-incrementing ID (e.g., "P001")
    val latitude: Double,          // geographic latitude from GNSS
    val longitude: Double,         // geographic longitude from GNSS
    val altitude: Double?,         // ellipsoidal height (m)
    val easting: Double?,          // projected E in the project's CRS
    val northing: Double?,         // projected N in the project's CRS
    val horizontalAccuracy: Double?,
    val verticalAccuracy: Double?,
    val fixQuality: Int,           // 0=none, 1=GPS, 2=DGPS, 4=RTK fix, 5=RTK float
    val numSatellites: Int,
    val hdop: Double?,
    val averagingSeconds: Int,     // how many seconds averaged
    val antennaHeight: Double? = null, // metres, instrument height
    val attribute: String = "",
    val remarks: String = "",
    @ColumnInfo(name = "photoPath") val photoPath: String? = null,
    @ColumnInfo(name = "layerType") val layerType: String = "point", // "point", "line_vertex", "polygon_vertex"
    @ColumnInfo(name = "featureId") val featureId: Long? = null, // groups vertices into lines/polygons
    @ColumnInfo(name = "geoidSeparation") val geoidSeparation: Double? = null,
    @ColumnInfo(name = "orthometricHeight") val orthometricHeight: Double? = null,
    /** Height-conversion model used at capture time. */
    val heightModel: String? = null,
    /** Uncertainty reported by the selected height model, when available. */
    val heightUncertainty: Double? = null,
    /** CRS metadata captured when this point was recorded. */
    val crsEpsg: Int? = null,
    val utmZone: Int? = null,
    val utmHemisphere: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
