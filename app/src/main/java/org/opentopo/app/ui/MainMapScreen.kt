package org.opentopo.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Polygon
import org.opentopo.app.db.AppDatabase
import org.opentopo.app.gnss.BluetoothGnssService
import org.opentopo.app.gnss.GnssState
import org.opentopo.app.gnss.UsbGnssService
import kotlinx.coroutines.flow.first
import org.opentopo.app.ntrip.NtripClient
import org.opentopo.app.ntrip.NtripStatus
import org.opentopo.app.ui.components.ntrip.NtripProfileSwitchSheet
import org.opentopo.app.survey.RecordingState
import org.opentopo.app.survey.Stakeout
import org.opentopo.app.survey.SurveyManager
import org.opentopo.app.ui.components.CoordinateBlock
import org.opentopo.app.ui.components.FixStatusPill
import org.opentopo.app.ui.components.survey.SplitButton
import org.opentopo.app.ui.theme.CoordinateFont
import org.opentopo.app.ui.theme.LabelOverline
import org.opentopo.app.ui.theme.LocalSurveyColors
import org.opentopo.app.ui.theme.MonoDelta
import org.opentopo.app.survey.StakeoutTarget
import org.opentopo.app.survey.TrigPoint
import org.opentopo.app.survey.TrigPointService

/**
 * v2 sheet routing. `MAP` collapses the sheet (home / peek only); the other modes expand
 * the sheet and drive which panel is composed inside it.
 *
 * The four primary entries (MAP, SURVEY, STAKEOUT, TRIG) are surfaced via the
 * `ShortNavigationBar`. The remaining entries (CONNECTION, TOOLS, EXPORT) are routed through
 * the top-bar overflow `DropdownMenu`.
 */
private enum class SheetMode { MAP, SURVEY, STAKEOUT, TRIG, CONNECTION, TOOLS, EXPORT }


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainMapScreen(
    gnssState: GnssState,
    bluetoothService: BluetoothGnssService,
    usbService: UsbGnssService,
    internalService: org.opentopo.app.gnss.InternalGnssService,
    ntripClient: NtripClient,
    ntripProfileRepo: org.opentopo.app.ntrip.NtripProfileRepository,
    db: AppDatabase,
    surveyManager: SurveyManager?,
    stakeout: Stakeout?,
    heposTransform: org.opentopo.transform.HeposTransform? = null,
    trigPointService: TrigPointService? = null,
    modifier: Modifier = Modifier,
) {
    val surveyColors = LocalSurveyColors.current
    val position by gnssState.position.collectAsState()
    val accuracy by gnssState.accuracy.collectAsState()
    val satellites by gnssState.satellites.collectAsState()
    val ntripState by ntripClient.state.collectAsState()
    val projectedCoords by surveyManager?.projectedPosition?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val projectedCrsLabel by surveyManager?.projectedCrsLabel?.collectAsState()
        ?: remember { mutableStateOf("SIRGAS2000 · UTM") }
    val recordingState =
        surveyManager?.recordingState?.collectAsState()?.value ?: RecordingState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = context as? org.opentopo.app.MainActivity
    val isInPipMode by activity?.isInPipMode?.collectAsState() ?: remember { mutableStateOf(false) }

    remember { MapLibre.getInstance(context) }

    // PiP compact view — shows only stakeout compass + distance
    if (isInPipMode) {
        PipStakeoutView(stakeout = stakeout, gnssState = gnssState)
        return
    }

    var sheetMode by remember { mutableStateOf(SheetMode.MAP) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    val preferReceiverGeoid by activity?.prefs?.preferReceiverGeoid?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var hasAnimatedToFirstFix by remember { mutableStateOf(false) }
    var stakeoutImmersive by remember { mutableStateOf(false) }

    // NTRIP UI overlay state — Connect screen opens the profile switch sheet;
    // that sheet can route into the Profiles management screen, which in turn
    // opens the Edit screen. All three are full-screen overlays managed here
    // so they can survive sheetMode changes.
    var ntripSwitchSheetOpen by remember { mutableStateOf(false) }
    var ntripProfilesOpen by remember { mutableStateOf(false) }
    var ntripProfileEditing by remember { mutableStateOf<org.opentopo.app.ntrip.NtripProfile?>(null) }
    var ntripProfileCreating by remember { mutableStateOf(false) }

    // More-tab overlays — the entire More Hub now opens as a full-screen
    // overlay (like Settings / About). Tools, settings, and about are all
    // top-level overlay flags; they're managed here so they can survive
    // sheetMode changes and system back navigation routes them correctly.
    var moreScreenOpen by remember { mutableStateOf(false) }
    var settingsScreenOpen by remember { mutableStateOf(false) }
    var aboutScreenOpen by remember { mutableStateOf(false) }
    var transformScreenOpen by remember { mutableStateOf(false) }

    // Trig point layer state
    var trigPointsVisible by remember { mutableStateOf(true) }
    var trigPointCache by remember { mutableStateOf<Map<String, TrigPoint>>(emptyMap()) }
    var selectedTrigPoint by remember { mutableStateOf<TrigPoint?>(null) }
    var verificationResult by remember { mutableStateOf<VerificationResult?>(null) }

    // Observe active project's survey points for map display
    val activeProjectId by surveyManager?.activeProjectId?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val activePoints by activeProjectId?.let {
        db.pointDao().getByProject(it).collectAsState(initial = emptyList())
    } ?: remember { mutableStateOf(emptyList()) }

    // Auto-compute verification residuals when the user is viewing a trig point
    // inside the Trig sheet panel and has a fix. Mirrors the legacy AlertDialog
    // "Verify" button logic but runs continuously so the inline Verify screen
    // always shows live residuals without an extra tap.
    LaunchedEffect(
        sheetMode,
        selectedTrigPoint?.gysId,
        position.hasFix,
        position.latitude,
        position.longitude,
    ) {
        if (sheetMode != SheetMode.TRIG) return@LaunchedEffect
        val tp = selectedTrigPoint ?: return@LaunchedEffect
        if (!position.hasFix) return@LaunchedEffect
        val ht = heposTransform ?: return@LaunchedEffect

        val publishedProjected = if (tp.egsa87Easting != null && tp.egsa87Northing != null) {
            org.opentopo.transform.ProjectedCoordinate(tp.egsa87Easting, tp.egsa87Northing)
        } else {
            ht.forward(
                org.opentopo.transform.GeographicCoordinate(tp.latitude, tp.longitude, tp.elevation ?: 0.0),
            )
        }
        val measured = ht.forward(
            org.opentopo.transform.GeographicCoordinate(
                position.latitude, position.longitude, position.altitude ?: 0.0,
            ),
        )
        val dE = measured.eastingM - publishedProjected.eastingM
        val dN = measured.northingM - publishedProjected.northingM

        val hEllipsoidal = position.altitude?.let { alt ->
            position.geoidSeparation?.let { n -> alt + n }
        }
        val measuredOrtho = hEllipsoidal?.let { h ->
            val tm07 = org.opentopo.transform.TransverseMercator.forward(
                position.latitude, position.longitude,
                24.0, 0.9996, 500_000.0, -2_000_000.0,
            )
            val greekN = ht.geoidUndulation(tm07.eastingM, tm07.northingM)
            val receiverN = position.geoidSeparation
            val effectiveN = if (preferReceiverGeoid) receiverN ?: greekN else greekN ?: receiverN
            effectiveN?.let { h - it }
        }
        val publishedH = tp.elevation
        val dH = if (measuredOrtho != null && publishedH != null) measuredOrtho - publishedH else null

        verificationResult = VerificationResult(
            pointName = "GYS ${tp.gysId}",
            publishedE = publishedProjected.eastingM,
            publishedN = publishedProjected.northingM,
            publishedH = publishedH,
            measuredE = measured.eastingM,
            measuredN = measured.northingM,
            measuredH = measuredOrtho,
            deltaE = dE,
            deltaN = dN,
            deltaH = dH,
            horizontalResidual = kotlin.math.sqrt(dE * dE + dN * dN),
            fixQuality = position.fixQuality,
            horizontalAccuracy = accuracy.horizontalAccuracyM,
            numSatellites = position.numSatellites,
        )
    }

    // Update user location on map when position changes
    LaunchedEffect(position.latitude, position.longitude, position.fixQuality) {
        val map = mapRef ?: return@LaunchedEffect
        if (!position.hasFix) return@LaunchedEffect

        val point = Point.fromLngLat(position.longitude, position.latitude)
        val source = map.style?.getSourceAs<GeoJsonSource>("user-location")
        if (source != null) {
            source.setGeoJson(point)
        }

        // Update dot color based on fix quality
        val fixColor = surveyColors.fixColor(position.fixQuality)
        val colorHex = String.format("#%06X", fixColor.toArgb() and 0xFFFFFF)
        map.style?.getLayerAs<CircleLayer>("user-location-dot")?.setProperties(
            PropertyFactory.circleColor(colorHex),
        )
        map.style?.getLayerAs<CircleLayer>("user-location-glow")?.setProperties(
            PropertyFactory.circleColor(colorHex),
        )

        // Animate to first fix
        if (!hasAnimatedToFirstFix) {
            hasAnimatedToFirstFix = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(position.latitude, position.longitude),
                    16.0,
                ),
                1500,
            )
        }
    }

    // Update accuracy convergence ring
    LaunchedEffect(accuracy.horizontalAccuracyM, position.hasFix) {
        val map = mapRef ?: return@LaunchedEffect
        val hAcc = accuracy.horizontalAccuracyM ?: return@LaunchedEffect
        if (!position.hasFix) return@LaunchedEffect

        // Convert accuracy (metres) to pixel radius at current zoom
        // At zoom 18, ~0.15m/px. At zoom 16, ~0.6m/px. At zoom 14, ~2.4m/px.
        val zoom = map.cameraPosition.zoom
        val metersPerPixel = 156543.03 * kotlin.math.cos(
            Math.toRadians(position.latitude)
        ) / Math.pow(2.0, zoom)
        val radiusPx = (hAcc / metersPerPixel).toFloat().coerceIn(4f, 200f)

        val ringColor = when {
            hAcc < 0.02 -> "#4CAF50"   // green — cm-level
            hAcc < 0.05 -> "#8BC34A"   // light green
            hAcc < 0.10 -> "#FFEB3B"   // yellow
            hAcc < 0.50 -> "#FF9800"   // orange
            else -> "#F44336"           // red
        }

        map.style?.getLayerAs<CircleLayer>("accuracy-ring")?.setProperties(
            PropertyFactory.circleRadius(radiusPx),
            PropertyFactory.circleColor(ringColor),
            PropertyFactory.circleStrokeColor(ringColor),
        )
    }

    // Haptic + audio feedback on point recorded
    LaunchedEffect(recordingState.lastRecordedPoint) {
        recordingState.lastRecordedPoint?.let { pt ->
            // Strong haptic confirmation
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(300)
                }
            } catch (_: Exception) {}

            // Audio chime
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                kotlinx.coroutines.delay(300)
                toneGen.release()
            } catch (_: Exception) {}

            val heightInfo = pt.orthometricHeight?.let { " H=${"%.3f".format(it)}m" } ?: ""
            snackbarHostState.showSnackbar(
                "Recorded ${pt.pointId}: E=${"%.3f".format(pt.easting)} N=${"%.3f".format(pt.northing)}$heightInfo \u00B1${"%.3f".format(pt.horizontalAccuracy ?: 0.0)}m"
            )
        }
    }

    // NTRIP disconnect alert — vibration pattern + warning tone
    LaunchedEffect(ntripState.status) {
        if (ntripState.status == NtripStatus.RECONNECTING || ntripState.status == NtripStatus.DISCONNECTED) {
            // Only alert if we were previously connected (not on initial state)
            if (ntripState.lastDataTime > 0) {
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 400), -1))
                    }
                } catch (_: Exception) {}
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 500)
                    kotlinx.coroutines.delay(600)
                    toneGen.release()
                } catch (_: Exception) {}

                snackbarHostState.showSnackbar("NTRIP connection lost!")
            }
        }
    }

    // Update survey point markers on map when active points change
    LaunchedEffect(activePoints) {
        val map = mapRef ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        // Build GeoJSON FeatureCollection from points
        val features = activePoints.mapNotNull { pt ->
            if (pt.latitude == 0.0 && pt.longitude == 0.0) return@mapNotNull null
            val point = Point.fromLngLat(pt.longitude, pt.latitude)
            Feature.fromGeometry(point).apply {
                addStringProperty("id", pt.pointId)
                addNumberProperty("fixQuality", pt.fixQuality)
            }
        }
        val fc = FeatureCollection.fromFeatures(features)

        val source = style.getSourceAs<GeoJsonSource>("survey-points")
        if (source != null) {
            source.setGeoJson(fc)
        }
    }

    // Update line and polygon features on map
    LaunchedEffect(activePoints) {
        val map = mapRef ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        // Group points by featureId for lines
        val lineVertices = activePoints.filter { it.layerType == "line_vertex" && it.featureId != null }
        val lineFeatures = lineVertices.groupBy { it.featureId }
        val lineGeoJsonFeatures = lineFeatures.map { (_, vertices) ->
            val coords = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }
            if (coords.size >= 2) {
                Feature.fromGeometry(LineString.fromLngLats(coords))
            } else null
        }.filterNotNull()
        style.getSourceAs<GeoJsonSource>("survey-lines")?.setGeoJson(
            FeatureCollection.fromFeatures(lineGeoJsonFeatures)
        )

        // Group points by featureId for polygons
        val polyVertices = activePoints.filter { it.layerType == "polygon_vertex" && it.featureId != null }
        val polyFeatures = polyVertices.groupBy { it.featureId }
        val polyGeoJsonFeatures = polyFeatures.map { (_, vertices) ->
            val coords = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }
            if (coords.size >= 3) {
                // Close the polygon
                val closed = coords + listOf(coords.first())
                Feature.fromGeometry(Polygon.fromLngLats(listOf(closed)))
            } else null
        }.filterNotNull()
        style.getSourceAs<GeoJsonSource>("survey-polygons")?.setGeoJson(
            FeatureCollection.fromFeatures(polyGeoJsonFeatures)
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
        ),
    )

    // Drive sheet open/close from the selected mode. MAP = peek (collapsed);
    // any other mode expands the sheet so its panel is visible.
    LaunchedEffect(sheetMode) {
        if (sheetMode == SheetMode.MAP) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.expand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        modifier = modifier,
        sheetPeekHeight = 320.dp,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = {
            // Drag handle spec: 36x4dp, outlineVariant, centered with 10dp bottom gap
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp))
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 340.dp),
            )
        },
        sheetContent = {
            // Project header state
            val projects by db.projectDao().getAll().collectAsState(initial = emptyList())
            val headerActiveProjectId by surveyManager?.activeProjectId?.collectAsState()
                ?: remember { mutableStateOf(null) }
            val headerActiveProject = projects.find { it.id == headerActiveProjectId }
            val headerActivePointCount by headerActiveProjectId?.let {
                db.pointDao().getByProject(it).collectAsState(initial = emptyList())
            }?.let { state -> remember { derivedStateOf { state.value.size } } }
                ?: remember { mutableStateOf(0) }

            val headerLineCount by headerActiveProjectId?.let {
                db.pointDao().getByProject(it).collectAsState(initial = emptyList())
            }?.let { state ->
                remember {
                    derivedStateOf {
                        state.value
                            .filter { it.layerType == "line_vertex" && it.featureId != null }
                            .map { it.featureId }
                            .distinct()
                            .size
                    }
                }
            } ?: remember { mutableStateOf(0) }

            var projectMenuExpanded by remember { mutableStateOf(false) }
            var showNewProjectDialog by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) {
                // ── Project header row: "THIS PROJECT · name" + "N pts · M lines" ──
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        val projectName = headerActiveProject?.name ?: "No project"
                        Text(
                            text = "THIS PROJECT \u00B7 $projectName",
                            style = LabelOverline,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { projectMenuExpanded = true }
                                .padding(vertical = 4.dp),
                        )
                        DropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false },
                        ) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        surveyManager?.setActiveProject(project.id)
                                        projectMenuExpanded = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("New Project") },
                                onClick = {
                                    showNewProjectDialog = true
                                    projectMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                            )
                        }
                    }
                    Text(
                        text = "$headerActivePointCount pts \u00B7 $headerLineCount lines",
                        style = MonoDelta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── v2 peek content: active project CRS CoordinateBlock ──
                PeekCoordinates(
                    position = position,
                    accuracy = accuracy,
                    projectedCoords = projectedCoords,
                    crsLabel = projectedCrsLabel,
                )

                Spacer(Modifier.height(12.dp))

                // ── Record SplitButton row ──
                val nextPointPreview = "P%03d".format((headerActivePointCount + 1).coerceAtLeast(1))
                val recordEnabled = position.hasFix
                    && surveyManager != null
                    && headerActiveProjectId != null
                val onRecord: () -> Unit = {
                    if (recordingState.isRecording) {
                        surveyManager?.cancelRecording()
                    } else {
                        surveyManager?.startRecording()
                    }
                }
                SplitButton(
                    primaryLabel = if (recordingState.isRecording) "Cancel" else "\u25CF Record $nextPointPreview",
                    onPrimaryClick = onRecord,
                    secondaryLabel = "",
                    onSecondaryClick = { sheetMode = SheetMode.SURVEY },
                    enabled = recordEnabled || recordingState.isRecording,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // ── ShortNavigationBar inlined inside the sheet (pill-shaped) ──
                // The "More" tab opens a full-screen overlay rather than a sheet
                // panel, so intercept it here and leave sheetMode unchanged.
                InlineShortNavBar(
                    selected = sheetMode,
                    onSelect = { picked ->
                        if (picked == SheetMode.TOOLS) {
                            moreScreenOpen = true
                        } else {
                            sheetMode = picked
                        }
                    },
                )

                // ── Panel content with M3E expressive transitions (only when expanded) ──
                if (sheetMode != SheetMode.MAP) {
                    Spacer(Modifier.height(12.dp))
                    val panelMotion = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = sheetMode,
                        transitionSpec = {
                            fadeIn(animationSpec = panelMotion) togetherWith
                                fadeOut(animationSpec = panelMotion)
                        },
                        label = "panel",
                    ) { mode ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                        ) {
                            when (mode) {
                                SheetMode.MAP -> Unit
                                SheetMode.SURVEY -> SurveyPanel(db, surveyManager)
                                SheetMode.STAKEOUT -> StakeoutPanel(
                                    stakeout,
                                    onImmersiveRequest = {
                                        stakeoutImmersive = true
                                    },
                                    onPipRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        { activity?.enterPipMode() }
                                    } else null,
                                    onVerifyRequest = {
                                        // If target is a GYS trig point, jump to Trig verify.
                                        // Otherwise the immersive HUD stays put.
                                        val targetName = stakeout?.target?.value?.name.orEmpty()
                                        if (targetName.startsWith("GYS ")) {
                                            val gysId = targetName.removePrefix("GYS ").trim()
                                            val match = trigPointCache.values.firstOrNull { it.gysId == gysId }
                                            if (match != null) {
                                                selectedTrigPoint = match
                                                sheetMode = SheetMode.TRIG
                                            } else {
                                                stakeoutImmersive = true
                                            }
                                        } else {
                                            stakeoutImmersive = true
                                        }
                                    },
                                )
                                SheetMode.TRIG -> TrigPanel(
                                    trigPoints = trigPointCache.values.toList(),
                                    onSelect = { selectedTrigPoint = it },
                                    selectedTrigPoint = selectedTrigPoint,
                                    verificationResult = verificationResult,
                                    onBack = {
                                        selectedTrigPoint = null
                                        verificationResult = null
                                    },
                                    onStakeout = { tp ->
                                        val projected = try {
                                            heposTransform?.forward(
                                                org.opentopo.transform.GeographicCoordinate(
                                                    tp.latitude, tp.longitude, tp.elevation ?: 0.0,
                                                ),
                                            )
                                        } catch (_: Exception) { null }
                                        if (projected != null && stakeout != null) {
                                            stakeout.setTarget(
                                                StakeoutTarget(
                                                    name = "GYS ${tp.gysId}",
                                                    easting = projected.eastingM,
                                                    northing = projected.northingM,
                                                    elevation = tp.elevation,
                                                ),
                                            )
                                            sheetMode = SheetMode.STAKEOUT
                                            selectedTrigPoint = null
                                        }
                                    },
                                    onSubmit = { _ ->
                                        android.widget.Toast.makeText(
                                            context,
                                            "vathra.xyz submit endpoint not wired yet",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                                SheetMode.CONNECTION -> ConnectionPanel(
                                    gnssState = gnssState,
                                    bluetoothService = bluetoothService,
                                    usbService = usbService,
                                    internalService = internalService,
                                    ntripClient = ntripClient,
                                    ntripProfileRepo = ntripProfileRepo,
                                    onNtripRowClick = { ntripSwitchSheetOpen = true },
                                    onNtripReconnectClick = {
                                        // Bounce the caster — repository auto-reconnects on active change.
                                        scope.launch {
                                            ntripClient.disconnect()
                                            val active = ntripProfileRepo.activeProfile.first()
                                            if (active != null) {
                                                ntripClient.connect(active.toConfig())
                                            }
                                        }
                                    },
                                )
                                // SheetMode.TOOLS is a routing target for the hamburger
                                // overflow menu but never renders in the sheet — the
                                // More tab opens the full-screen MoreHubScreen overlay
                                // instead. Render nothing here; the overlay handler at
                                // the bottom of MainMapScreen owns the presentation.
                                SheetMode.TOOLS -> Unit
                                SheetMode.EXPORT -> ExportPanel(db)
                            }
                        }
                    }
                }
            }

            // New project dialog (triggered from header dropdown)
            if (showNewProjectDialog) {
                NewProjectHeaderDialog(
                    db = db,
                    surveyManager = surveyManager,
                    onDismiss = { showNewProjectDialog = false },
                )
            }
        },
    ) { paddingValues ->
        // ── Map fills the screen ──
        var orthoVisible by remember { mutableStateOf(false) }
        var contoursVisible by remember { mutableStateOf(true) }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            // Load vathra.xyz vector basemap style from assets
                            val styleJson = ctx.assets.open("style_vathra.json")
                                .bufferedReader().readText()
                            map.setStyle(
                                org.maplibre.android.maps.Style.Builder()
                                    .fromJson(styleJson),
                            ) { style ->
                                // Initial camera: try to use last known GPS, fallback to Greece center
                                val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                                val lastLoc = try {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                            ctx, android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                            ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                    } else null
                                } catch (_: Exception) { null }
                                val (initLat, initLng, initZoom) = if (lastLoc != null) {
                                    Triple(lastLoc.latitude, lastLoc.longitude, 15.0)
                                } else {
                                    Triple(38.5, 23.8, 7.0)
                                }
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(initLat, initLng)).zoom(initZoom).build()

                                // Move compass to avoid overlap with fix status pill
                                // Compass: top-left, below fix pill
                                map.uiSettings.isCompassEnabled = true
                                map.uiSettings.compassGravity = android.view.Gravity.TOP or android.view.Gravity.START
                                map.uiSettings.setCompassMargins(40, 380, 0, 0)

                                // Add user location source + layers
                                val locationSource = GeoJsonSource(
                                    "user-location",
                                    Point.fromLngLat(23.8, 38.5),
                                )
                                style.addSource(locationSource)

                                style.addLayer(
                                    CircleLayer("user-location-glow", "user-location")
                                        .withProperties(
                                            PropertyFactory.circleRadius(18f),
                                            PropertyFactory.circleColor("#1565C0"),
                                            PropertyFactory.circleOpacity(0.15f),
                                            PropertyFactory.circleStrokeWidth(0f),
                                        )
                                )
                                style.addLayer(
                                    CircleLayer("user-location-dot", "user-location")
                                        .withProperties(
                                            PropertyFactory.circleRadius(8f),
                                            PropertyFactory.circleColor("#1565C0"),
                                            PropertyFactory.circleOpacity(1f),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(2.5f),
                                        )
                                )

                                // Accuracy convergence ring — shrinks as RTK converges
                                style.addLayerBelow(
                                    CircleLayer("accuracy-ring", "user-location")
                                        .withProperties(
                                            PropertyFactory.circleRadius(0f),  // updated dynamically
                                            PropertyFactory.circleColor("#1565C0"),
                                            PropertyFactory.circleOpacity(0.12f),
                                            PropertyFactory.circleStrokeColor("#1565C0"),
                                            PropertyFactory.circleStrokeWidth(1.5f),
                                            PropertyFactory.circleStrokeOpacity(0.4f),
                                        ),
                                    "user-location-glow",
                                )

                                // Survey points source + layers
                                val pointsSource = GeoJsonSource("survey-points")
                                style.addSource(pointsSource)

                                // Point markers - colored circles with thick white border for ortho visibility
                                style.addLayerBelow(
                                    CircleLayer("survey-points-circle", "survey-points")
                                        .withProperties(
                                            PropertyFactory.circleRadius(8f),
                                            PropertyFactory.circleColor(
                                                Expression.match(
                                                    Expression.get("fixQuality"),
                                                    Expression.literal("#FF1744"),  // default: bright red
                                                    Expression.stop(4, "#00E676"),  // RTK fix: bright green
                                                    Expression.stop(5, "#FFAB00"),  // RTK float: bright amber
                                                    Expression.stop(2, "#FFD600"),  // DGPS: bright yellow
                                                    Expression.stop(1, "#448AFF"),  // GPS: bright blue
                                                )
                                            ),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(3f),
                                        ),
                                    "user-location-glow",
                                )

                                // Point labels - white halo for ortho readability
                                style.addLayerBelow(
                                    org.maplibre.android.style.layers.SymbolLayer(
                                        "survey-points-labels", "survey-points",
                                    ).withProperties(
                                        PropertyFactory.textField(Expression.get("id")),
                                        PropertyFactory.textFont(arrayOf("Noto Sans Medium")),
                                        PropertyFactory.textSize(12f),
                                        PropertyFactory.textOffset(arrayOf(0f, -1.8f)),
                                        PropertyFactory.textColor("#FFFFFF"),
                                        PropertyFactory.textHaloColor("#000000"),
                                        PropertyFactory.textHaloWidth(2f),
                                        PropertyFactory.textAllowOverlap(true),
                                    ),
                                    "user-location-glow",
                                )

                                // Survey lines source + layer
                                val linesSource = GeoJsonSource("survey-lines")
                                style.addSource(linesSource)
                                style.addLayerBelow(
                                    LineLayer("survey-lines-layer", "survey-lines")
                                        .withProperties(
                                            PropertyFactory.lineColor("#00E5FF"),
                                            PropertyFactory.lineWidth(4f),
                                            PropertyFactory.lineOpacity(0.9f),
                                        ),
                                    "survey-points-circle",
                                )

                                // Survey polygons source + layer
                                val polygonsSource = GeoJsonSource("survey-polygons")
                                style.addSource(polygonsSource)
                                style.addLayerBelow(
                                    FillLayer("survey-polygons-layer", "survey-polygons")
                                        .withProperties(
                                            PropertyFactory.fillColor("#00E5FF"),
                                            PropertyFactory.fillOpacity(0.15f),
                                        ),
                                    "survey-lines-layer",
                                )
                                style.addLayerBelow(
                                    LineLayer("survey-polygons-outline", "survey-polygons")
                                        .withProperties(
                                            PropertyFactory.lineColor("#00E5FF"),
                                            PropertyFactory.lineWidth(3f),
                                            PropertyFactory.lineOpacity(0.6f),
                                        ),
                                    "survey-lines-layer",
                                )

                                // Trig points source + layer (status-colored markers)
                                val trigSource = GeoJsonSource("trig-points")
                                style.addSource(trigSource)
                                style.addLayerBelow(
                                    CircleLayer("trig-points-circle", "trig-points")
                                        .withProperties(
                                            PropertyFactory.circleRadius(
                                                Expression.interpolate(
                                                    Expression.linear(),
                                                    Expression.zoom(),
                                                    Expression.stop(8, 3f),
                                                    Expression.stop(12, 5f),
                                                    Expression.stop(16, 8f),
                                                ),
                                            ),
                                            PropertyFactory.circleColor(
                                                Expression.match(
                                                    Expression.get("status"),
                                                    Expression.literal("#9E9E9E"),  // default: gray/unknown
                                                    Expression.stop("OK", "#4CAF50"),
                                                    Expression.stop("DAMAGED", "#FF9800"),
                                                    Expression.stop("DESTROYED", "#F44336"),
                                                    Expression.stop("MISSING", "#9C27B0"),
                                                ),
                                            ),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(2f),
                                            PropertyFactory.circleOpacity(0.9f),
                                        ),
                                    "survey-polygons-layer",
                                )
                                style.addLayerBelow(
                                    org.maplibre.android.style.layers.SymbolLayer(
                                        "trig-points-labels", "trig-points",
                                    ).withProperties(
                                        PropertyFactory.textField(Expression.get("gys_id")),
                                        PropertyFactory.textFont(arrayOf("Noto Sans Medium")),
                                        PropertyFactory.textSize(10f),
                                        PropertyFactory.textOffset(arrayOf(0f, -1.5f)),
                                        PropertyFactory.textColor("#FFFFFF"),
                                        PropertyFactory.textHaloColor("#000000"),
                                        PropertyFactory.textHaloWidth(1.5f),
                                        PropertyFactory.textAllowOverlap(false),
                                        PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE),
                                    ),
                                    "survey-polygons-layer",
                                )

                                // Prepare Ktimatologio orthophoto WMS as hidden raster source
                                val ktimaSource = RasterSource(
                                    "ktima-ortho",
                                    TileSet(
                                        "2.2.0",
                                        "http://gis.ktimanet.gr/wms/wmsopen/wmsserver.aspx?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=BASEMAP&SRS=EPSG:900913&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256&FORMAT=image/jpeg",
                                    ),
                                    256,
                                )
                                style.addSource(ktimaSource)
                                style.addLayerBelow(
                                    RasterLayer("ktima-ortho-layer", "ktima-ortho")
                                        .withProperties(
                                            PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE),
                                        ),
                                    "survey-polygons-layer",  // below all survey layers
                                )

                            }
                            // Load trig points on camera idle
                            map.addOnCameraIdleListener {
                                if (trigPointService == null || !trigPointsVisible) return@addOnCameraIdleListener
                                val zoom = map.cameraPosition.zoom
                                if (zoom < 9) return@addOnCameraIdleListener // too far out
                                val bounds = map.projection.visibleRegion.latLngBounds
                                val center = bounds.center
                                // Approximate radius from bounds diagonal
                                val ne = bounds.northEast
                                val sw = bounds.southWest
                                val latDiff = ne.latitude - sw.latitude
                                val lonDiff = ne.longitude - sw.longitude
                                val radiusM = (kotlin.math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111_000 / 2).toInt()
                                    .coerceAtMost(50_000) // cap at 50km
                                scope.launch {
                                    try {
                                        val points = trigPointService.getNearby(center.latitude, center.longitude, radiusM)
                                        if (points.isNotEmpty()) {
                                            val updated = trigPointCache.toMutableMap()
                                            points.forEach { updated[it.gysId] = it }
                                            trigPointCache = updated
                                            // Update GeoJSON source
                                            val features = updated.values.map { tp ->
                                                val f = Feature.fromGeometry(
                                                    Point.fromLngLat(tp.longitude, tp.latitude),
                                                )
                                                f.addStringProperty("gys_id", tp.gysId)
                                                f.addStringProperty("name", tp.name ?: "")
                                                f.addStringProperty("status", tp.status ?: "UNKNOWN")
                                                tp.elevation?.let { f.addNumberProperty("elevation", it) }
                                                f
                                            }
                                            map.style?.getSourceAs<GeoJsonSource>("trig-points")?.setGeoJson(
                                                FeatureCollection.fromFeatures(features),
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            // Tap handler: trig points first, then survey points
                            map.addOnMapClickListener { latLng ->
                                val pixel = map.projection.toScreenLocation(latLng)

                                // Check trig points
                                if (trigPointsVisible) {
                                    val trigFeatures = map.queryRenderedFeatures(
                                        PointF(pixel.x, pixel.y),
                                        "trig-points-circle",
                                    )
                                    if (trigFeatures.isNotEmpty()) {
                                        val gysId = trigFeatures[0].getStringProperty("gys_id")
                                            ?: return@addOnMapClickListener false
                                        val tp = trigPointCache[gysId]
                                        if (tp != null) {
                                            selectedTrigPoint = tp
                                        }
                                        return@addOnMapClickListener true
                                    }
                                }

                                // Check survey points
                                val features = map.queryRenderedFeatures(
                                    PointF(pixel.x, pixel.y),
                                    "survey-points-circle",
                                )
                                if (features.isNotEmpty()) {
                                    val pointId = features[0].getStringProperty("id")
                                        ?: return@addOnMapClickListener false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Point: $pointId")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }

                            mapRef = map
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // ── v2 Top chrome: FixStatusPill (TopStart) + overflow menu (TopEnd) ──
            val fixExtras = remember(accuracy.horizontalAccuracyM, position.numSatellites) {
                val hAccM = accuracy.horizontalAccuracyM
                val sats = position.numSatellites
                when {
                    hAccM != null && sats > 0 -> {
                        val hAccCm = hAccM * 100.0
                        "${"%.1f".format(hAccCm)} cm \u00B7 $sats sats"
                    }
                    sats > 0 -> "$sats sats"
                    else -> null
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Fix status pill with elevation shadow
                FixStatusPill(
                    fixQuality = position.fixQuality,
                    extras = fixExtras,
                    modifier = Modifier.shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(percent = 50),
                        clip = false,
                    ),
                )

                // Right: hamburger/more icon button (44x44, rounded 12dp)
                Box {
                    Surface(
                        onClick = { overflowMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "More actions",
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Connection") },
                            leadingIcon = { Icon(Icons.Outlined.Cable, null) },
                            onClick = {
                                overflowMenuExpanded = false
                                sheetMode = SheetMode.CONNECTION
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Trig Points (GYS)") },
                            leadingIcon = { Icon(Icons.Outlined.PinDrop, null) },
                            onClick = {
                                overflowMenuExpanded = false
                                sheetMode = SheetMode.TRIG
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("More") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                            onClick = {
                                overflowMenuExpanded = false
                                moreScreenOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export") },
                            leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                            onClick = {
                                overflowMenuExpanded = false
                                sheetMode = SheetMode.EXPORT
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Layer: Ortho") },
                            leadingIcon = {
                                if (orthoVisible) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                else Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp))
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                orthoVisible = !orthoVisible
                                val newVis = if (orthoVisible)
                                    org.maplibre.android.style.layers.Property.VISIBLE
                                else
                                    org.maplibre.android.style.layers.Property.NONE
                                mapRef?.style?.getLayer("ktima-ortho-layer")?.setProperties(
                                    PropertyFactory.visibility(newVis),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Layer: Contours") },
                            leadingIcon = {
                                if (contoursVisible) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                else Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp))
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                contoursVisible = !contoursVisible
                                val newVis = if (contoursVisible)
                                    org.maplibre.android.style.layers.Property.VISIBLE
                                else
                                    org.maplibre.android.style.layers.Property.NONE
                                mapRef?.style?.getLayer("contours-lines")?.setProperties(PropertyFactory.visibility(newVis))
                                mapRef?.style?.getLayer("contours-labels")?.setProperties(PropertyFactory.visibility(newVis))
                            },
                        )
                        HorizontalDivider()
                        // Trig points are an *overlay*, not a basemap layer — they sit
                        // on top of whichever layer combo (ortho / contours / both) the
                        // user has active. Separated from the "Layer:" items visually
                        // to make that clear.
                        DropdownMenuItem(
                            text = { Text("Show Trig Points (GYS)") },
                            leadingIcon = {
                                if (trigPointsVisible) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                else Icon(Icons.Outlined.PinDrop, null, Modifier.size(18.dp))
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                trigPointsVisible = !trigPointsVisible
                                val newVis = if (trigPointsVisible)
                                    org.maplibre.android.style.layers.Property.VISIBLE
                                else
                                    org.maplibre.android.style.layers.Property.NONE
                                mapRef?.style?.getLayer("trig-points-circle")?.setProperties(PropertyFactory.visibility(newVis))
                                mapRef?.style?.getLayer("trig-points-labels")?.setProperties(PropertyFactory.visibility(newVis))
                            },
                        )
                    }
                }
            }

            // ── v2 vertical floating toolbar: Center / Layers / Stake ──
            // Anchored right=16dp, bottom=24dp inside the map area Box. The parent Box
            // ends at the peek-sheet top edge, so this sits just above the peek sheet
            // without stealing the hamburger's top-right slot.
            val toolbarFabEnabled = position.hasFix
                && surveyManager != null
                && surveyManager.activeProjectId.collectAsState().value != null
            val centerOnMeSelected = true // always highlight — user location is the focal
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
            ) {
                Column(
                    Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 1. Center-on-me (primaryContainer when selected)
                    Surface(
                        onClick = {
                            val map = mapRef ?: return@Surface
                            if (!position.hasFix) return@Surface
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(position.latitude, position.longitude),
                                    (map.cameraPosition.zoom).coerceAtLeast(17.0),
                                ),
                                800,
                            )
                        },
                        shape = RoundedCornerShape(22.dp),
                        color = if (centerOnMeSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (centerOnMeSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.GpsFixed,
                                contentDescription = "Center on me",
                            )
                        }
                    }
                    // 2. Layers — cycle through basemap layer combinations
                    Surface(
                        onClick = {
                            val newOrtho: Boolean
                            val newContours: Boolean
                            when {
                                !orthoVisible && !contoursVisible -> { newOrtho = false; newContours = true }
                                !orthoVisible && contoursVisible -> { newOrtho = true; newContours = false }
                                orthoVisible && !contoursVisible -> { newOrtho = true; newContours = true }
                                else -> { newOrtho = false; newContours = false }
                            }
                            orthoVisible = newOrtho
                            contoursVisible = newContours
                            mapRef?.style?.getLayer("ktima-ortho-layer")?.setProperties(
                                PropertyFactory.visibility(
                                    if (newOrtho) org.maplibre.android.style.layers.Property.VISIBLE
                                    else org.maplibre.android.style.layers.Property.NONE,
                                ),
                            )
                            val contourVis = if (newContours)
                                org.maplibre.android.style.layers.Property.VISIBLE
                            else org.maplibre.android.style.layers.Property.NONE
                            mapRef?.style?.getLayer("contours-lines")?.setProperties(PropertyFactory.visibility(contourVis))
                            mapRef?.style?.getLayer("contours-labels")?.setProperties(PropertyFactory.visibility(contourVis))
                        },
                        shape = RoundedCornerShape(22.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Layers,
                                contentDescription = "Cycle basemap",
                            )
                        }
                    }
                    // 3. Stake — quick-mark a waypoint at current position
                    Surface(
                        onClick = {
                            if (toolbarFabEnabled) surveyManager?.quickMark()
                        },
                        shape = RoundedCornerShape(22.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (toolbarFabEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.PinDrop,
                                contentDescription = "Stake / waypoint",
                            )
                        }
                    }
                }
            }

        }
    }

    // ── Trig point detail dialog ──
    // When the user is already inside the Trig sheet panel (sheetMode == TRIG) the
    // inline TrigVerifyScreen handles presentation, so we suppress the legacy
    // AlertDialog to avoid doubling up. The AlertDialog still fires for map-marker
    // taps (which set selectedTrigPoint from outside the sheet flow).
    if (sheetMode != SheetMode.TRIG) selectedTrigPoint?.let { tp ->
        // Use the published EGSA87 E/N directly from the API when available.
        // The API lat/lon are WGS84 (transformed from EGSA87 by GDAL using PROJ's
        // Helmert params which differ from the HEPOS params we use).  Recomputing
        // E/N from those lat/lon via HeposTransform causes ~1.3 m residuals because
        // the round-trip through two incompatible Helmert parameter sets doesn't close.
        val projected = remember(tp.gysId) {
            if (tp.egsa87Easting != null && tp.egsa87Northing != null) {
                org.opentopo.transform.ProjectedCoordinate(tp.egsa87Easting, tp.egsa87Northing)
            } else {
                // Fallback: old API without EGSA87 fields — use HeposTransform
                heposTransform?.forward(
                    org.opentopo.transform.GeographicCoordinate(tp.latitude, tp.longitude, tp.elevation ?: 0.0),
                )
            }
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedTrigPoint = null },
            icon = {
                Icon(
                    Icons.Outlined.PinDrop,
                    contentDescription = null,
                    tint = when (tp.status) {
                        "OK" -> Color(0xFF4CAF50)
                        "DAMAGED" -> Color(0xFFFF9800)
                        "DESTROYED" -> Color(0xFFF44336)
                        "MISSING" -> Color(0xFF9C27B0)
                        else -> Color(0xFF9E9E9E)
                    },
                )
            },
            title = {
                Text(
                    "GYS ${tp.gysId}",
                    fontFamily = CoordinateFont,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tp.name?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    tp.status?.let {
                        Text(
                            "Status: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tp.elevation?.let {
                        Text(
                            "Elevation: ${"%.1f".format(it)} m",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = CoordinateFont,
                        )
                    }
                    projected?.let {
                        Text(
                            "EGSA87: E ${"%.3f".format(it.eastingM)}  N ${"%.3f".format(it.northingM)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = CoordinateFont,
                        )
                    }
                    Text(
                        "WGS84: %.6f\u00B0, %.6f\u00B0".format(tp.latitude, tp.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = CoordinateFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tp.distanceM?.let {
                        Text(
                            "Distance: ${"%.0f".format(it)} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Verify button — compare current position to known coords
                    if (projected != null && position.hasFix) {
                        val currentProjected = remember(position.latitude, position.longitude) {
                            heposTransform?.forward(
                                org.opentopo.transform.GeographicCoordinate(
                                    position.latitude, position.longitude, position.altitude ?: 0.0,
                                ),
                            )
                        }
                        if (currentProjected != null) {
                            TextButton(
                                onClick = {
                                    val dE = currentProjected.eastingM - projected.eastingM
                                    val dN = currentProjected.northingM - projected.northingM
                                    // Compute orthometric height using Greek geoid when available.
                                    // H = h_ellipsoidal - N_greek.  The ellipsoidal height is
                                    // h = GGA_altitude + GGA_geoidSeparation.
                                    val hEllipsoidal = position.altitude?.let { alt ->
                                        position.geoidSeparation?.let { n -> alt + n }
                                    }
                                    val measuredOrtho = hEllipsoidal?.let { h ->
                                        heposTransform?.let { ht ->
                                            val tm07 = org.opentopo.transform.TransverseMercator.forward(
                                                position.latitude, position.longitude,
                                                24.0, 0.9996, 500_000.0, -2_000_000.0,
                                            )
                                            val greekN = ht.geoidUndulation(tm07.eastingM, tm07.northingM)
                                            val receiverN = position.geoidSeparation
                                            // Honor user-selected geoid source; fall back to the other source when null
                                            val effectiveN = if (preferReceiverGeoid) {
                                                receiverN ?: greekN
                                            } else {
                                                greekN ?: receiverN
                                            }
                                            effectiveN?.let { h - it }
                                        }
                                    }
                                    val publishedH = tp.elevation
                                    val dH = if (measuredOrtho != null && publishedH != null)
                                        measuredOrtho - publishedH else null
                                    verificationResult = VerificationResult(
                                        pointName = "GYS ${tp.gysId}",
                                        publishedE = projected.eastingM,
                                        publishedN = projected.northingM,
                                        publishedH = publishedH,
                                        measuredE = currentProjected.eastingM,
                                        measuredN = currentProjected.northingM,
                                        measuredH = measuredOrtho,
                                        deltaE = dE,
                                        deltaN = dN,
                                        deltaH = dH,
                                        horizontalResidual = kotlin.math.sqrt(dE * dE + dN * dN),
                                        fixQuality = position.fixQuality,
                                        horizontalAccuracy = accuracy.horizontalAccuracyM,
                                        numSatellites = position.numSatellites,
                                    )
                                    selectedTrigPoint = null
                                },
                            ) {
                                Text("Verify")
                            }
                        }
                    }
                    // Stakeout button
                    if (stakeout != null && projected != null) {
                        TextButton(
                            onClick = {
                                stakeout.setTarget(
                                    StakeoutTarget(
                                        name = "GYS ${tp.gysId}",
                                        easting = projected.eastingM,
                                        northing = projected.northingM,
                                        elevation = tp.elevation,
                                    ),
                                )
                                sheetMode = SheetMode.STAKEOUT
                                selectedTrigPoint = null
                            },
                        ) {
                            Text("Stakeout")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTrigPoint = null }) {
                    Text("Close")
                }
            },
        )
    }

    // ── Verification report dialog ──
    // Only shown from the legacy map-marker → AlertDialog → Verify flow.
    // When the inline TrigPanel verify screen owns the presentation, we render
    // residuals in the sheet instead of a modal dialog.
    if (sheetMode != SheetMode.TRIG) verificationResult?.let { result ->
        VerificationReportDialog(
            result = result,
            onDismiss = { verificationResult = null },
            onShare = { text ->
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Verification"))
                verificationResult = null
            },
        )
    }

    // ── Stakeout immersive full-screen overlay ──
    if (stakeoutImmersive && stakeout != null) {
        StakeoutImmersiveOverlay(
            stakeout = stakeout,
            gnssState = gnssState,
            onExit = { stakeoutImmersive = false },
        )
    }

    // ── NTRIP profile management overlays ──
    val ntripProfiles by ntripProfileRepo.profiles.collectAsState(initial = emptyList())
    val ntripActiveProfile by ntripProfileRepo.activeProfile.collectAsState(initial = null)
    val ntripConnectionState by ntripProfileRepo.state.collectAsState(
        initial = org.opentopo.app.ntrip.NtripConnectionState.Empty,
    )

    if (ntripSwitchSheetOpen) {
        NtripProfileSwitchSheet(
            profiles = ntripProfiles,
            activeProfileId = ntripActiveProfile?.id,
            onSelect = { picked ->
                scope.launch { ntripProfileRepo.setActive(picked.id) }
                android.widget.Toast.makeText(context, "Switched to ${picked.displayName}", android.widget.Toast.LENGTH_SHORT).show()
            },
            onManageClick = {
                ntripSwitchSheetOpen = false
                ntripProfilesOpen = true
            },
            onDismiss = { ntripSwitchSheetOpen = false },
        )
    }

    if (ntripProfilesOpen) {
        NtripProfilesScreen(
            profiles = ntripProfiles,
            activeProfile = ntripActiveProfile,
            state = ntripConnectionState,
            onBack = { ntripProfilesOpen = false },
            onActivate = { p -> scope.launch { ntripProfileRepo.setActive(p.id) } },
            onEdit = { p -> ntripProfileEditing = p },
            onDuplicate = { p -> scope.launch { ntripProfileRepo.duplicate(p.id) } },
            onDelete = { p -> scope.launch { ntripProfileRepo.delete(p.id) } },
            onAdd = { ntripProfileCreating = true },
        )
    }

    if (ntripProfileCreating || ntripProfileEditing != null) {
        NtripProfileEditScreen(
            initial = ntripProfileEditing,
            onScanSourcetable = { host, port, user, pass ->
                ntripProfileRepo.scanSourcetable(host, port, user, pass)
            },
            onSave = { updated ->
                scope.launch { ntripProfileRepo.upsert(updated) }
                ntripProfileEditing = null
                ntripProfileCreating = false
            },
            onCancel = {
                ntripProfileEditing = null
                ntripProfileCreating = false
            },
        )
    }

    // ── More-hub full-screen overlays ──
    // Draw order matters: the More Hub is declared first so that Settings /
    // About / Transform (declared below) render on TOP of it when opened from
    // a tile or row tap. Back from a child overlay reveals the More Hub again;
    // Back from the More Hub itself returns to the map.
    if (moreScreenOpen) {
        androidx.activity.compose.BackHandler(enabled = true) {
            moreScreenOpen = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                // Header row (back button only — ToolsPanel renders its own title below)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = { moreScreenOpen = false },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
                ToolsPanel(
                    db = db,
                    surveyManager = surveyManager,
                    transform = heposTransform,
                    onOpenCoordConverter = { transformScreenOpen = true },
                    onOpenGysSearch = {
                        moreScreenOpen = false
                        sheetMode = SheetMode.TRIG
                    },
                    onOpenImport = {
                        moreScreenOpen = false
                        sheetMode = SheetMode.EXPORT
                    },
                    onOpenExportProject = {
                        moreScreenOpen = false
                        sheetMode = SheetMode.EXPORT
                    },
                    onOpenAreaPerimeter = {
                        moreScreenOpen = false
                        sheetMode = SheetMode.SURVEY
                        android.widget.Toast.makeText(
                            context,
                            "Switch to Polygon mode in Survey to see live area + perimeter",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    },
                    onOpenTransformPipeline = { transformScreenOpen = true },
                    onOpenSettings = { settingsScreenOpen = true },
                    onOpenRecentActivity = {
                        moreScreenOpen = false
                        sheetMode = SheetMode.SURVEY
                    },
                    onOpenWhatsNew = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/ppapadeas/opentopo/blob/main/CHANGELOG.md"),
                        )
                        context.startActivity(intent)
                    },
                    onOpenAbout = { aboutScreenOpen = true },
                )
            }
        }
    }

    if (settingsScreenOpen) {
        androidx.activity.compose.BackHandler(enabled = true) {
            settingsScreenOpen = false
        }
        val amoledPref by (activity?.prefs?.preferReceiverGeoid?.collectAsState(initial = false)
            ?: remember { mutableStateOf(false) })
        val requireRtk by (surveyManager?.let {
            activity?.prefs?.requireRtkFix?.collectAsState(initial = false)
        } ?: remember { mutableStateOf(false) })
        SettingsScreen(
            userName = "",
            userOrg = "",
            amoledEnabled = amoledPref,
            requireRtkFix = requireRtk,
            onAmoledChange = { /* stub: AMOLED theme flag not yet in UserPreferences */ },
            onRequireRtkChange = { v ->
                scope.launch { activity?.prefs?.setRequireRtkFix(v) }
            },
            onBack = { settingsScreenOpen = false },
        )
    }

    if (aboutScreenOpen) {
        androidx.activity.compose.BackHandler(enabled = true) {
            aboutScreenOpen = false
        }
        AboutScreen(
            versionName = "v2.0.0",
            buildNumber = "16",
            license = "AGPLv3",
            onBack = { aboutScreenOpen = false },
            onSourceCodeClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/ppapadeas/opentopo"),
                )
                context.startActivity(intent)
            },
            onDocsClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/ppapadeas/opentopo/blob/main/README.md"),
                )
                context.startActivity(intent)
            },
            onPrivacyClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/ppapadeas/opentopo/blob/main/PRIVACY_POLICY.md"),
                )
                context.startActivity(intent)
            },
            onWhatsNewClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/ppapadeas/opentopo/blob/main/CHANGELOG.md"),
                )
                context.startActivity(intent)
            },
            onContactClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:pierros@papadeas.gr")
                }
                context.startActivity(intent)
            },
            onLicencesClick = {
                android.widget.Toast.makeText(
                    context,
                    "Licences screen — coming in a future release",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    // Transform pipeline inspector — full-screen overlay wrapping the existing
    // TransformPanel with a back button. This replaces the transform inspector
    // that used to live inline in ToolsPanel before the v2.0 More-Hub rewrite.
    if (transformScreenOpen && heposTransform != null) {
        androidx.activity.compose.BackHandler(enabled = true) {
            transformScreenOpen = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = { transformScreenOpen = false },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "MORE · TOOLS",
                            fontFamily = org.opentopo.app.ui.theme.CoordinateFont,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Transform",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                TransformPanel(transform = heposTransform)
            }
        }
    }
}

// ── v2 peek content ──

/**
 * Inline pill-shaped ShortNavigationBar rendered inside the bottom peek sheet.
 *
 * Matches the v2 mockup (`.short-nav` at surface-container, 999 radius, 4 tabs).
 * Renders the four primary tabs (GNSS, Survey, Stake, More). The Map tab is mapped to "More"
 * which routes to the Tools sheet so the user can escape back to overflow commands.
 */
@Composable
private fun InlineShortNavBar(
    selected: SheetMode,
    onSelect: (SheetMode) -> Unit,
) {
    val tabs = listOf(
        Triple(SheetMode.CONNECTION, "GNSS", Icons.Outlined.Cable),
        Triple(SheetMode.SURVEY, "Survey", Icons.Outlined.RadioButtonChecked),
        Triple(SheetMode.STAKEOUT, "Stake", Icons.Outlined.NearMe),
        Triple(SheetMode.TOOLS, "More", Icons.Outlined.Tune),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (mode, label, icon) ->
                val isActive = selected == mode
                val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
                val fg = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
                val hPadding = if (isActive) 14.dp else 10.dp
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(bg)
                        .clickable { onSelect(mode) }
                        .padding(horizontal = hPadding, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/**
 * v2 peek card. Shows coordinates in the active project's projected CRS.
 */
@Composable
private fun PeekCoordinates(
    position: org.opentopo.app.gnss.PositionState,
    accuracy: org.opentopo.app.gnss.AccuracyState,
    projectedCoords: org.opentopo.transform.ProjectedCoordinate?,
    crsLabel: String,
) {
    val sigmaH = accuracy.horizontalAccuracyM
    val sigmaV = accuracy.altitudeErrorM
    val sigmaHStr = sigmaH?.let { "\u03C3H ${"%.3f".format(it)} m" }
    val sigmaFooter = buildString {
        if (sigmaHStr != null) append(sigmaHStr)
        if (sigmaV != null) {
            if (isNotEmpty()) append(" \u00B7 ")
            append("\u03C3V ${"%.3f".format(sigmaV)} m")
        }
    }.ifBlank { null }

    if (projectedCoords != null && position.hasFix) {
        val ellipsoidal = position.altitude?.let { alt ->
            position.geoidSeparation?.let { n -> alt + n } ?: alt
        }
        val heightStr = ellipsoidal?.let { "${"%.3f".format(it)} m" }
        CoordinateBlock(
            label = crsLabel,
            easting = "${"%.3f".format(projectedCoords.eastingM)} m",
            northing = "${"%.3f".format(projectedCoords.northingM)} m",
            height = heightStr,
            fixQuality = null,
            sigmaH = sigmaFooter,
        )
    } else {
        // No fix yet — show a placeholder for the active project CRS.
        CoordinateBlock(
            label = crsLabel,
            easting = "\u2014",
            northing = "\u2014",
            height = null,
            fixQuality = null,
            sigmaH = null,
        )
    }
}

// ── Reusable composables ──

@Composable
fun FixTypeBadge(fixQuality: Int) {
    val surveyColors = LocalSurveyColors.current
    val color = surveyColors.fixColor(fixQuality)
    val label = surveyColors.fixLabel(fixQuality)
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            color = surveyColors.onFix,
            fontFamily = CoordinateFont,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SatelliteBreakdown(satellites: org.opentopo.app.gnss.SatelliteState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        satellites.byConstellation.forEach { (constellation, sats) ->
            val label = when (constellation) {
                org.opentopo.app.gnss.Constellation.GPS -> "GP"
                org.opentopo.app.gnss.Constellation.GLONASS -> "GL"
                org.opentopo.app.gnss.Constellation.GALILEO -> "GA"
                org.opentopo.app.gnss.Constellation.BEIDOU -> "BD"
                else -> "?"
            }
            Text(
                "${sats.size}$label",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = CoordinateFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AccuracyBadge(accuracyM: Double, prefix: String) {
    val surveyColors = LocalSurveyColors.current
    val color = surveyColors.accuracyColor(accuracyM)
    Text(
        "$prefix\u00B1${"%.3f".format(accuracyM)}m",
        color = color,
        fontFamily = CoordinateFont,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

// ── New project dialog (used from project header) ──

@Composable
private fun NewProjectHeaderDialog(
    db: AppDatabase,
    surveyManager: SurveyManager?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coordinateSystem by remember { mutableStateOf("SIRGAS2000_UTM") }
    var utmZone by remember { mutableStateOf<Int?>(null) }
    var utmHemisphere by remember { mutableStateOf("AUTO") }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                var crsExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { crsExpanded = true }) {
                        Text(
                            if (coordinateSystem == "SIRGAS2000_UTM")
                                "SIRGAS2000 / UTM"
                            else
                                "EGSA87 / EPSG:2100"
                        )
                    }
                    DropdownMenu(
                        expanded = crsExpanded,
                        onDismissRequest = { crsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("SIRGAS2000 / UTM") },
                            onClick = {
                                coordinateSystem = "SIRGAS2000_UTM"
                                crsExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("EGSA87 / EPSG:2100 (legacy)") },
                            onClick = {
                                coordinateSystem = "EGSA87"
                                utmZone = null
                                utmHemisphere = "AUTO"
                                crsExpanded = false
                            },
                        )
                    }
                }

                if (coordinateSystem == "SIRGAS2000_UTM") {
                    var hemisphereExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { hemisphereExpanded = true }) {
                            Text(
                                when (utmHemisphere) {
                                    "NORTH" -> "Hemisfério: Norte"
                                    "SOUTH" -> "Hemisfério: Sul"
                                    else -> "Hemisfério: Automático"
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = hemisphereExpanded,
                            onDismissRequest = { hemisphereExpanded = false },
                        ) {
                            listOf(
                                "AUTO" to "Automático pela latitude",
                                "NORTH" to "Norte",
                                "SOUTH" to "Sul",
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        utmHemisphere = value
                                        if (value == "NORTH" && (utmZone ?: 18) > 22) {
                                            utmZone = null
                                        }
                                        hemisphereExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    var zoneExpanded by remember { mutableStateOf(false) }
                    val zoneSuffix = when (utmHemisphere) {
                        "NORTH" -> "N"
                        "SOUTH" -> "S"
                        else -> ""
                    }
                    Box {
                        TextButton(onClick = { zoneExpanded = true }) {
                            Text(
                                utmZone?.let { "UTM ${it}$zoneSuffix" }
                                    ?: "Fuso UTM: automático"
                            )
                        }
                        DropdownMenu(
                            expanded = zoneExpanded,
                            onDismissRequest = { zoneExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Automático pela longitude") },
                                onClick = {
                                    utmZone = null
                                    zoneExpanded = false
                                },
                            )
                            val zones = if (utmHemisphere == "NORTH") 18..22 else 18..25
                            zones.forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text("UTM ${zone}$zoneSuffix") },
                                    onClick = {
                                        utmZone = zone
                                        zoneExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            val project = org.opentopo.app.db.ProjectEntity(
                                name = name,
                                description = description,
                                coordinateSystem = coordinateSystem,
                                utmZone = utmZone,
                                utmHemisphere = utmHemisphere,
                            )
                            val id = db.projectDao().insert(project)
                            surveyManager?.setActiveProject(id)
                            onDismiss()
                        }
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ── PiP compact stakeout view ──

/**
 * Minimal stakeout display for picture-in-picture mode.
 * Shows compass arrow and distance in a compact square layout.
 */
@Composable
private fun PipStakeoutView(
    stakeout: Stakeout?,
    gnssState: GnssState,
) {
    val surveyColors = LocalSurveyColors.current
    val result by stakeout?.result?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val position by gnssState.position.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        val r = result
        if (r != null) {
            val distColor = surveyColors.stakeoutColor(r.distance)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Compact compass
                PipCompassArrow(bearingDeg = r.bearingDeg, distance = r.distance)

                // Distance
                Text(
                    "%.2f m".format(r.distance),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = CoordinateFont,
                    color = distColor,
                    fontWeight = FontWeight.Bold,
                )

                // Cardinal bearing
                Text(
                    r.bearingCardinal,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            // No stakeout target or no fix
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (stakeout?.target?.value != null) "Fix\u2026" else "No target",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Compact compass arrow for PiP overlay. */
@Composable
private fun PipCompassArrow(bearingDeg: Double, distance: Double) {
    val surveyColors = LocalSurveyColors.current
    val color = surveyColors.stakeoutColor(distance)
    val outlineColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)

    Canvas(modifier = Modifier.size(80.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val innerRadius = radius - 8.dp.toPx()

        // Outer ring
        drawCircle(
            color = outlineColor,
            radius = radius,
            center = center,
            style = Stroke(width = 1.5f.dp.toPx()),
        )

        // Arrow
        rotate(bearingDeg.toFloat(), pivot = center) {
            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - innerRadius * 0.85f)
                lineTo(center.x - 10.dp.toPx(), center.y - innerRadius * 0.15f)
                lineTo(center.x, center.y - innerRadius * 0.30f)
                lineTo(center.x + 10.dp.toPx(), center.y - innerRadius * 0.15f)
                close()
            }
            drawPath(arrowPath, color)
        }
    }
}
