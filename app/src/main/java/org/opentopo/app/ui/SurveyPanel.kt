package org.opentopo.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.launch
import org.opentopo.app.db.AppDatabase
import org.opentopo.app.db.PointEntity
import org.opentopo.app.db.ProjectEntity
import org.opentopo.app.survey.RecordingState
import org.opentopo.app.survey.SurveyManager
import org.opentopo.app.ui.components.ButtonGroup as OpenTopoButtonGroup
import org.opentopo.app.ui.components.FixStatusPill
import org.opentopo.app.ui.components.TonalCard
import org.opentopo.app.ui.components.survey.RecordButton
import org.opentopo.app.ui.theme.CoordinateFont
import org.opentopo.app.ui.theme.LabelOverline

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SurveyPanel(
    db: AppDatabase,
    surveyManager: SurveyManager?,
    modifier: Modifier = Modifier,
) {
    val activeProjectId by surveyManager?.activeProjectId?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val projects by db.projectDao().getAll().collectAsState(initial = emptyList())
    val activeProject = projects.find { it.id == activeProjectId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (activeProject != null) {
            ProjectDetail(
                project = activeProject,
                db = db,
                surveyManager = surveyManager,
            )
        } else {
            // No project selected — prompt user
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    "Select a project from the header above",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Use the project dropdown to select or create a project.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Section header ──

@Composable
internal fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Project detail (v2 layout) ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectDetail(
    project: ProjectEntity,
    db: AppDatabase,
    surveyManager: SurveyManager?,
) {
    val points by db.pointDao().getByProject(project.id).collectAsState(initial = emptyList())
    val recordingState = surveyManager?.recordingState?.collectAsState()?.value ?: RecordingState()
    var remarks by remember { mutableStateOf("") }
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val prefs = activity?.prefs
    val savedAH by prefs?.antennaHeight?.collectAsState(initial = "1.80")
        ?: remember { mutableStateOf("1.80") }
    val savedPointPattern by prefs?.pointPattern?.collectAsState(initial = "P")
        ?: remember { mutableStateOf("P") }
    val savedPointAttribute by prefs?.pointAttribute?.collectAsState(initial = "")
        ?: remember { mutableStateOf("") }

    var antennaHeight by remember { mutableStateOf(savedAH) }
    var pointPattern by remember { mutableStateOf(savedPointPattern) }
    var pointAttribute by remember { mutableStateOf(savedPointAttribute) }
    var attributeMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sync when saved values load.
    LaunchedEffect(savedAH) { antennaHeight = savedAH }
    LaunchedEffect(savedPointPattern) { pointPattern = savedPointPattern }
    LaunchedEffect(savedPointAttribute) { pointAttribute = savedPointAttribute }

    // Recording mode switcher state
    val recordingMode by surveyManager?.recordingMode?.collectAsState()
        ?: remember { mutableStateOf("point") }
    val activeFeatureId by surveyManager?.activeFeatureId?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val vertexCount by surveyManager?.vertexCount?.collectAsState()
        ?: remember { mutableStateOf(0) }

    // Live EGSA87 projected coordinates from SurveyManager
    val projected by surveyManager?.projectedPosition?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val projectedCrsLabel by surveyManager?.projectedCrsLabel?.collectAsState()
        ?: remember { mutableStateOf("SIRGAS2000 · UTM") }

    // Derive current fix/σH/sats from the last recorded epoch (the only live
    // signal reachable from SurveyPanel without touching MainActivity).
    val lastPt = recordingState.lastRecordedPoint
    val currentFix: Int = lastPt?.fixQuality ?: 0
    val sigmaH: Double? = lastPt?.horizontalAccuracy
    val sigmaV: Double? = lastPt?.verticalAccuracy
    val numSats: Int = lastPt?.numSatellites ?: 0
    val hdop: Double? = lastPt?.hdop

    // Accuracy gate — backed by UserPreferences.minAccuracyM (DataStore) so
    // it survives process death and stays in sync with SettingsPanel. Pushed
    // to SurveyManager.minAccuracyM so it filters epochs during averaging.
    val gateString by prefs?.minAccuracyM?.collectAsState(initial = "0.02")
        ?: remember { mutableStateOf("0.02") }
    val accuracyGateSigma: Double = gateString.toDoubleOrNull() ?: 0.02
    var showGateMenu by remember { mutableStateOf(false) }
    LaunchedEffect(accuracyGateSigma) {
        surveyManager?.minAccuracyM = accuracyGateSigma
    }

    // Record button is only armed when the fix is RTK Fix (quality 4) and
    // the current σH clears the gate. When we have no live σH yet, we still
    // let the user kick off averaging so the session can collect samples —
    // SurveyManager will filter unqualified epochs internally.
    val recordEnabled: Boolean = surveyManager != null &&
        (lastPt == null || (currentFix >= 4 && (sigmaH ?: Double.MAX_VALUE) <= accuracyGateSigma))
    val nextPointId = deriveNextPointId(pointPattern, points)

    val onRecord: () -> Unit = {
        if (recordingState.isRecording) {
            surveyManager?.cancelRecording()
        } else if (surveyManager != null) {
            val ah = antennaHeight.replace(',', '.').toDoubleOrNull()
            surveyManager.antennaHeight = ah
            scope.launch {
                prefs?.setAntennaHeight(antennaHeight)
                prefs?.setPointPattern(pointPattern)
                prefs?.setPointAttribute(pointAttribute)
            }
            surveyManager.startRecording(
                projectId = project.id,
                remarks = remarks,
                pointIdOverride = nextPointId,
                attribute = pointAttribute,
                antennaHeightOverride = ah,
            )
            remarks = ""
        }
        Unit
    }
    val progressFraction: Float = (recordingState.epochsCollected.toFloat() /
        recordingState.totalEpochsTarget.toFloat().coerceAtLeast(1f))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        // 1. Header row — 40 dp back + title block + 40 dp kebab
        Spacer(Modifier.height(18.dp))
        SurveyHeaderRow(
            eyebrow = if (recordingState.isRecording) "SURVEY · REC" else "SURVEY",
            title = project.name,
            onBack = { activity?.onBackPressedDispatcher?.onBackPressed() },
            onMore = { showGateMenu = true },
            moreMenu = {
                DropdownMenu(
                    expanded = showGateMenu,
                    onDismissRequest = { showGateMenu = false },
                ) {
                    listOf("0.01", "0.02", "0.05").forEach { gate ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Gate \u00B1${(gate.toDouble() * 100).toInt()} cm",
                                )
                            },
                            onClick = {
                                scope.launch { prefs?.setMinAccuracyM(gate) }
                                showGateMenu = false
                            },
                        )
                    }
                }
            },
        )

        // 2. Mode ButtonGroup (Point / Line / Polygon)
        Spacer(Modifier.height(14.dp))
        val modes = listOf("Point", "Line", "Polygon")
        val modeKeys = listOf("point", "line", "polygon")
        val currentModeIndex = modeKeys.indexOf(recordingMode).coerceAtLeast(0)
        OpenTopoButtonGroup(
            options = modes,
            selectedIndex = currentModeIndex,
            onSelect = { idx -> surveyManager?.setRecordingMode(modeKeys[idx]) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (recordingMode == "point") {
            Spacer(Modifier.height(12.dp))
            TonalCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "CADASTRO DO PONTO",
                        style = LabelOverline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pointPattern,
                            onValueChange = { pointPattern = it.uppercase() },
                            label = { Text("Nome / sequência") },
                            supportingText = {
                                Text("CE → CE001… · 001 → 001, 002…")
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CoordinateFont,
                            ),
                        )
                        OutlinedTextField(
                            value = antennaHeight,
                            onValueChange = { antennaHeight = it },
                            label = { Text("Antena (m)") },
                            modifier = Modifier.width(120.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CoordinateFont,
                            ),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = pointAttribute,
                            onValueChange = { pointAttribute = it.uppercase() },
                            label = { Text("Atributo") },
                            placeholder = { Text("CERCA, RUA, MEIO-FIO…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Box {
                            OutlinedButton(onClick = { attributeMenuExpanded = true }) {
                                Text("Lista")
                            }
                            DropdownMenu(
                                expanded = attributeMenuExpanded,
                                onDismissRequest = { attributeMenuExpanded = false },
                            ) {
                                listOf(
                                    "",
                                    "CERCA",
                                    "RUA",
                                    "MEIO-FIO",
                                    "EIXO",
                                    "POSTE",
                                    "MURO",
                                    "EDIFICAÇÃO",
                                    "BUEIRO",
                                    "TALUDE",
                                ).forEach { attribute ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (attribute.isBlank()) "Sem atributo"
                                                else attribute
                                            )
                                        },
                                        onClick = {
                                            pointAttribute = attribute
                                            attributeMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Observação") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Text(
                        "Próximo ponto: $nextPointId",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = CoordinateFont,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 3. Fix status pill — left-aligned
        Spacer(Modifier.height(14.dp))
        val sigmaExtras: String? = buildString {
            sigmaH?.let { append("${"%.1f".format(it * 100)} cm") }
            if (numSats > 0) {
                if (isNotEmpty()) append(" \u00B7 ")
                append("$numSats sats")
            }
            hdop?.let {
                if (isNotEmpty()) append(" \u00B7 ")
                append("HDOP ${"%.1f".format(it)}")
            }
        }.ifBlank { null }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FixStatusPill(fixQuality = currentFix, extras = sigmaExtras)
        }

        // 4. Coordinate card — inline per mockup spec (20sp mono, 18x16 padding)
        Spacer(Modifier.height(12.dp))
        CoordinateCard(
            label = projectedCrsLabel,
            easting = projected?.eastingM,
            northing = projected?.northingM,
            heightM = lastPt?.orthometricHeight,
            sigmaV = sigmaV,
            antennaHeightM = antennaHeight.toDoubleOrNull(),
            hdop = hdop,
        )

        // 5. Epoch averaging card — gradient bar + footer row
        Spacer(Modifier.height(14.dp))
        EpochAveragingCard(
            pointId = nextPointId,
            epochsCollected = recordingState.epochsCollected,
            epochsTarget = recordingState.totalEpochsTarget,
            progress = progressFraction,
            currentSigmaH = sigmaH,
            targetSigmaH = accuracyGateSigma,
            rtkGateOn = surveyManager?.requireRtkFix ?: true,
        )

        // Error feedback, if any
        recordingState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        // -- Line/polygon controls (unchanged behaviour, preserved for v2) --
        if (recordingMode == "line" || recordingMode == "polygon") {
            Spacer(Modifier.height(12.dp))
            if (activeFeatureId == null) {
                val featureScope = rememberCoroutineScope()
                FilledTonalButton(
                    onClick = { featureScope.launch { surveyManager?.startFeature() } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(percent = 50),
                ) {
                    Icon(
                        if (recordingMode == "line") Icons.Outlined.Timeline else Icons.Outlined.Pentagon,
                        null,
                        Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Start ${if (recordingMode == "line") "Line" else "Polygon"}")
                }
            } else {
                TonalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Recording ${if (recordingMode == "line") "Line" else "Polygon"}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "$vertexCount vertices recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = CoordinateFont,
                        )

                        var measurement by remember { mutableStateOf("") }
                        LaunchedEffect(vertexCount) {
                            if (vertexCount >= 2 && recordingMode == "line") {
                                val dist = surveyManager?.computeLineDistance(activeFeatureId!!)
                                measurement = if (dist != null) "%.2f m".format(dist) else ""
                            } else if (vertexCount >= 3 && recordingMode == "polygon") {
                                val area = surveyManager?.computePolygonArea(activeFeatureId!!)
                                if (area != null) {
                                    val stremma = area / 1000.0
                                    measurement = "%.1f m\u00B2 (%.3f stremma)".format(area, stremma)
                                }
                            } else {
                                measurement = ""
                            }
                        }

                        if (measurement.isNotBlank()) {
                            Text(
                                measurement,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = CoordinateFont,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (vertexCount > 0) {
                                IconButton(onClick = { surveyManager?.undoLastVertex() }) {
                                    Icon(Icons.Outlined.Undo, "Undo last vertex", Modifier.size(20.dp))
                                }
                            }
                            FilledTonalButton(
                                onClick = { surveyManager?.recordVertex() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(percent = 50),
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Vertex")
                            }
                            OutlinedButton(
                                onClick = { surveyManager?.finishFeature() },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Finish")
                            }
                        }
                    }
                }
            }
        }

        // 6. Spacer — nominally fills remaining height; we keep it fixed so the
        // panel still scrolls inside the 420 dp bottom sheet instead of being
        // clipped or flung into negative space.
        Spacer(Modifier.height(16.dp))

        // 7. Record action row — 56 dp circular side + 112 dp RecordButton + 56 dp continue
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            CircleSideButton(
                onClick = { surveyManager?.quickMark("waypoint") },
                contentDescription = "Add waypoint",
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(22.dp))
            }

            RecordButton(
                onClick = onRecord,
                enabled = recordEnabled || recordingState.isRecording,
                isRecording = recordingState.isRecording,
                progress = progressFraction,
                size = 112.dp,
            )

            CircleSideButton(
                onClick = {
                    // "Continue" — finishes a line/polygon feature when active, or
                    // is a no-op for points (the RecordButton handles that path).
                    if (activeFeatureId != null) surveyManager?.finishFeature()
                },
                contentDescription = "Continue",
            ) {
                Icon(Icons.Outlined.ArrowForward, null, Modifier.size(22.dp))
            }
        }

        // -- Points section header (below the record row for v2) --
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(
                icon = Icons.Outlined.RadioButtonChecked,
                title = "Points (${points.size})",
            )
            Spacer(Modifier.height(12.dp))
            points.forEach { point ->
                PointCard(point = point, db = db)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ── v2 Survey header (back / eyebrow + title / kebab) ──

@Composable
private fun SurveyHeaderRow(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
    moreMenu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleSideButton(
            onClick = onBack,
            contentDescription = "Back",
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            size = 40.dp,
        ) {
            Icon(Icons.Outlined.ArrowBack, null, Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                eyebrow,
                style = LabelOverline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01f).sp,
                    lineHeight = 26.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            CircleSideButton(
                onClick = onMore,
                contentDescription = "More options",
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                size = 40.dp,
            ) {
                Icon(Icons.Filled.MoreVert, null, Modifier.size(20.dp))
            }
            moreMenu()
        }
    }
}

// ── Reusable round "circle" side button (40 dp or 56 dp) ──

@Composable
private fun CircleSideButton(
    onClick: () -> Unit,
    contentDescription: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Propagate contentDescription to the inner Icon via CompositionLocal-ish
            // pattern is overkill; callers already pass localised icons. We just
            // draw the content and let TalkBack merge the clickable semantics.
            content()
        }
    }
}

// ── Mockup-spec inline coordinate card ──

@Composable
private fun CoordinateCard(
    label: String,
    easting: Double?,
    northing: Double?,
    heightM: Double?,
    sigmaV: Double?,
    antennaHeightM: Double?,
    hdop: Double?,
) {
    val monoLine = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 20.sp,
        lineHeight = 29.sp, // ≈ 1.45 × 20
        fontWeight = FontWeight.W500,
        letterSpacing = (-0.2f).sp,
    )
    val monoStats = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.sp,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                label.uppercase(),
                style = TextStyle(
                    fontFamily = CoordinateFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text("E  ${formatMetric(easting)}", style = monoLine)
            Text("N  ${formatMetric(northing)}", style = monoLine)
            Text(
                "H  ${formatMetric(heightM)} m",
                style = monoLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "\u03C3V ${sigmaV?.let { "%.3f".format(it) } ?: "—"} m",
                    style = monoStats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "AH ${antennaHeightM?.let { "%.2f".format(it) } ?: "—"} m",
                    style = monoStats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "PDOP ${hdop?.let { "%.1f".format(it) } ?: "—"}",
                    style = monoStats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMetric(value: Double?): String =
    value?.let { "%,.3f".format(it).replace(",", " ") } ?: "—"

// ── Mockup-spec inline epoch averaging card ──

@Composable
private fun EpochAveragingCard(
    pointId: String,
    epochsCollected: Int,
    epochsTarget: Int,
    progress: Float,
    currentSigmaH: Double?,
    targetSigmaH: Double,
    rtkGateOn: Boolean,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val seconds = epochsCollected.toDouble()
    val monoStats = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
    )
    val overline = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.sp,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "AVERAGING \u00B7 $pointId",
                    style = overline,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "$epochsCollected / $epochsTarget epochs \u00B7 ${"%.1f".format(seconds)} s",
                    style = monoStats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Gradient progress track (999dp-rounded, 14dp tall, with 2dp white
            // vertical line at the progress head).
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                val trackWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    maxWidth.toPx()
                }
                val fillWidthPx = trackWidthPx * clamped
                val lineWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    2.dp.toPx()
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                    if (clamped > 0f) {
                        val gradient = Brush.horizontalGradient(
                            0f to Color(0xFF1C6E5A),
                            1f to Color(0xFF4A9A84),
                            startX = 0f,
                            endX = trackWidthPx,
                        )
                        drawRoundRect(
                            brush = gradient,
                            topLeft = Offset.Zero,
                            size = Size(fillWidthPx, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        )
                        // 2 dp vertical cap at progress head
                        val capX = (fillWidthPx - lineWidthPx / 2f).coerceIn(0f, size.width - lineWidthPx)
                        drawRect(
                            color = Color.White.copy(alpha = 0.5f),
                            topLeft = Offset(capX, 0f),
                            size = Size(lineWidthPx, size.height),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val thresholdMet = currentSigmaH != null && currentSigmaH <= targetSigmaH
                val gateCm = (targetSigmaH * 100).toInt()
                Text(
                    if (thresholdMet) "\u2713 \u03C3H below $gateCm cm threshold"
                    else "\u2026 waiting for \u03C3H \u2264 $gateCm cm",
                    style = monoStats.copy(fontWeight = FontWeight.W700),
                    color = if (thresholdMet) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (rtkGateOn) "RTK gate ON" else "RTK gate off",
                    style = monoStats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Point card ──

@Composable
private fun PointCard(point: PointEntity, db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Camera photo capture
    val photoUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            scope.launch {
                db.pointDao().update(point.copy(photoPath = photoUri.value?.path))
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fix badge on the left
            Box(modifier = Modifier.width(56.dp)) {
                FixTypeBadge(point.fixQuality)
            }

            Spacer(Modifier.width(8.dp))

            // Coordinates + metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    point.pointId,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = CoordinateFont,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "E: ${point.easting?.let { "%.3f".format(it) } ?: "\u2014"}  N: ${point.northing?.let { "%.3f".format(it) } ?: "\u2014"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = CoordinateFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Height info
                val heightText = buildString {
                    point.orthometricHeight?.let { append("H=${"%.3f".format(it)}") }
                    point.geoidSeparation?.let { append(" N=${"%.2f".format(it)}") }
                }
                if (heightText.isNotBlank()) {
                    Text(
                        heightText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = CoordinateFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pointMeta = buildString {
                    if (point.attribute.isNotBlank()) append(point.attribute)
                    point.antennaHeight?.let {
                        if (isNotEmpty()) append(" · ")
                        append("Antena ${"%.3f".format(it)} m")
                    }
                    point.heightModel?.let {
                        if (isNotEmpty()) append(" · ")
                        append(
                            when (it) {
                                org.opentopo.app.geoid.HeightModels.HGEONOR2020_IMBITUBA -> "hgeoHNOR2020"
                                org.opentopo.app.geoid.HeightModels.MAPGEO2015 -> "MAPGEO2015"
                                else -> "NMEA GGA"
                            }
                        )
                    }
                    point.heightUncertainty?.let {
                        append(" · u=${"%.2f".format(it)} m")
                    }
                }
                if (pointMeta.isNotBlank()) {
                    Text(
                        pointMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    point.horizontalAccuracy?.let {
                        AccuracyBadge(it, "H")
                    }
                    if (point.remarks.isNotBlank()) {
                        Text(
                            point.remarks,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Photo button
            IconButton(
                onClick = {
                    val photoFile = File(
                        context.filesDir,
                        "photos/${point.pointId}_${System.currentTimeMillis()}.jpg",
                    )
                    photoFile.parentFile?.mkdirs()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile,
                    )
                    photoUri.value = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = "Take photo for ${point.pointId}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Edit button
            IconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit ${point.pointId}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Delete button
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${point.pointId}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete point ${point.pointId}?") },
            text = {
                Text("This will permanently remove this survey point.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { db.pointDao().delete(point) }
                        showDeleteDialog = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit dialog
    if (showEditDialog) {
        EditPointDialog(
            point = point,
            onDismiss = { showEditDialog = false },
            onSave = { newId, newAttribute, newRemarks, newAH ->
                scope.launch {
                    db.pointDao().update(
                        point.copy(
                            pointId = newId,
                            attribute = newAttribute,
                            remarks = newRemarks,
                            antennaHeight = newAH,
                        ),
                    )
                }
                showEditDialog = false
            },
        )
    }
}

// ── Edit point dialog ──

@Composable
private fun EditPointDialog(
    point: PointEntity,
    onDismiss: () -> Unit,
    onSave: (pointId: String, attribute: String, remarks: String, antennaHeight: Double?) -> Unit,
) {
    var pointId by remember { mutableStateOf(point.pointId) }
    var attribute by remember { mutableStateOf(point.attribute) }
    var remarks by remember { mutableStateOf(point.remarks) }
    var antennaHeight by remember {
        mutableStateOf(point.antennaHeight?.let { "%.2f".format(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Edit ${point.pointId}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pointId,
                    onValueChange = { pointId = it.uppercase() },
                    label = { Text("Nome do ponto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = attribute,
                    onValueChange = { attribute = it.uppercase() },
                    label = { Text("Atributo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = antennaHeight,
                    onValueChange = { antennaHeight = it },
                    label = { Text("Antenna Height (m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = CoordinateFont),
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        pointId.ifBlank { point.pointId },
                        attribute,
                        remarks,
                        antennaHeight.replace(',', '.').toDoubleOrNull(),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ── New project dialog ──

private fun deriveNextPointId(patternInput: String, points: List<PointEntity>): String {
    val raw = patternInput.trim().uppercase().ifBlank { "P" }
    val numericOnly = raw.all { it.isDigit() }

    if (numericOnly) {
        val start = raw.toIntOrNull() ?: 1
        val width = raw.length.coerceAtLeast(1)
        val existing = points.asSequence()
            .filter { it.layerType == "point" }
            .mapNotNull { point ->
                point.pointId.takeIf { id -> id.all(Char::isDigit) }?.toIntOrNull()
            }
            .filter { it >= start }
            .maxOrNull()
        val next = existing?.plus(1) ?: start
        return next.toString().padStart(width, '0')
    }

    val trailingDigits = raw.takeLastWhile { it.isDigit() }
    val prefix = raw.dropLast(trailingDigits.length).ifBlank { "P" }
    val start = trailingDigits.toIntOrNull() ?: 1
    val width = if (trailingDigits.isNotEmpty()) trailingDigits.length else 3
    val regex = Regex("^" + Regex.escape(prefix) + "(\\d+)$")

    val existing = points.asSequence()
        .filter { it.layerType == "point" }
        .mapNotNull { point ->
            regex.matchEntire(point.pointId.uppercase())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        .filter { it >= start }
        .maxOrNull()
    val next = existing?.plus(1) ?: start
    return prefix + next.toString().padStart(width, '0')
}


@Composable
private fun NewProjectDialog(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text("New Project")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            db.projectDao().insert(
                                ProjectEntity(name = name, description = description),
                            )
                            onCreated()
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
