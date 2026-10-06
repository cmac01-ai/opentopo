package org.opentopo.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import org.opentopo.app.gnss.GnssState
import org.opentopo.app.survey.Stakeout
import org.opentopo.app.survey.StakeoutTarget
import org.opentopo.app.ui.components.FixStatusPill
import org.opentopo.app.ui.components.survey.CompassRing
import org.opentopo.app.ui.theme.CoordinateFont
import org.opentopo.app.ui.theme.LocalSurveyColors

/**
 * Remember a device heading (clockwise from true north, degrees) driven by
 * TYPE_ROTATION_VECTOR. Falls back to a null listener (heading = 0f) on devices
 * without a rotation-vector sensor.
 */
@Composable
private fun rememberDeviceHeadingDeg(): Float {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? android.hardware.SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : android.hardware.SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                heading = ((deg + 360f) % 360f)
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (rotationSensor != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }
    return heading
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StakeoutPanel(
    stakeout: Stakeout?,
    onImmersiveRequest: (() -> Unit)? = null,
    onPipRequest: (() -> Unit)? = null,
    onVerifyRequest: (() -> Unit)? = null,
    onNextTarget: (() -> Unit)? = null,
    crsLabel: String = "SIRGAS2000 / UTM",
    modifier: Modifier = Modifier,
) {
    val result by stakeout?.result?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val currentTarget by stakeout?.target?.collectAsState() ?: remember { mutableStateOf(null) }
    var targetName by remember { mutableStateOf("") }
    var targetE by remember { mutableStateOf("") }
    var targetN by remember { mutableStateOf("") }

    // Sync text fields when target is set externally (e.g. from trig point dialog)
    LaunchedEffect(currentTarget) {
        currentTarget?.let { t ->
            targetName = t.name
            targetE = "%.3f".format(t.easting)
            targetN = "%.3f".format(t.northing)
        }
    }

    val hasTarget = currentTarget != null

    if (!hasTarget) {
        // ─── Target-entry form (preserved from v1) ───────────────────────────
        StakeoutTargetForm(
            stakeout = stakeout,
            targetName = targetName,
            targetE = targetE,
            targetN = targetN,
            onTargetName = { targetName = it },
            onTargetE = { targetE = it },
            onTargetN = { targetN = it },
            crsLabel = crsLabel,
            modifier = modifier,
        )
    } else {
        val clearTarget: () -> Unit = {
            stakeout?.setTarget(null)
            targetName = ""
            targetE = ""
            targetN = ""
        }
        StakeoutHud(
            stakeout = stakeout,
            result = result,
            targetName = currentTarget?.name ?: targetName,
            onClearTarget = clearTarget,
            onNextTarget = onNextTarget ?: clearTarget,
            onVerifyRequest = onVerifyRequest ?: (onImmersiveRequest ?: {}),
            modifier = modifier,
        )
    }
}

// ── Immersive HUD color tokens (hardcoded — NOT MaterialTheme) ────────────
// The Stakeout HUD is a domain-specific dark-green overlay that ignores the
// app's light/dark theme. See docs/mockups → StakeoutScreen.
private val HudBackground = Color(0xFF06332A)   // deep pine-teal
private val HudForeground = Color(0xFFEAFFF6)   // near-mint text
private val HudMint = Color(0xFFA5F2D9)         // accent mint
private val HudMintOn = Color(0xFF004D3B)       // on-mint dark fg
private val HudDeltaAmber = Color(0xFFFDF0B3)
private val HudDeltaRed = Color(0xFFFFD9D2)
private val HudCardBg = Color(0x0FFFFFFF)       // rgba(255,255,255,0.06)
private val HudCardBorder = Color(0x33A5F2D9)   // rgba(165,242,217,0.2)
private val HudInnerRing = Color(0x2EFFFFFF)    // rgba(255,255,255,0.18)
private val HudOutlineBorder = Color(0x66A5F2D9) // rgba(165,242,217,0.4)

/**
 * v2 immersive Stakeout HUD — shown once a target is active.
 *
 * Dark pine-teal full-bleed overlay matching the Claude Design mockup
 * (`.qa/mockups/opentopo/project/opentopo-v2.html`, `StakeoutScreen`).
 *
 * Layout (top → bottom):
 *   1. Top row  — close (X) + target title + RTK fix pill
 *   2. Compass  — 240 dp dashed-mint ring with chunky bearing arrow and a
 *                 large mono distance readout at center
 *   3. Deltas   — 3 translucent-mint cards: ΔE / ΔN / ΔH
 *   4. Spacer
 *   5. Actions  — outline "Next target" + filled-mint "Verify now"
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StakeoutHud(
    stakeout: Stakeout?,
    result: org.opentopo.app.survey.StakeoutResult?,
    targetName: String,
    onClearTarget: () -> Unit,
    onNextTarget: () -> Unit,
    onVerifyRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bearingToTarget = result?.bearingDeg?.toFloat() ?: 0f
    val horizontalDelta: Double? = result?.distance
    val deltaE: Double? = result?.deltaEasting
    val deltaN: Double? = result?.deltaNorthing
    // ΔH not provided by Stakeout (horizontal-only); show neutral placeholder.
    val deltaH: Double? = null

    val deviceHeading = rememberDeviceHeadingDeg()

    // Live GNSS telemetry — σH drives the header pill ("RTK 0.9 cm").
    val gnssPos by stakeout?.gnssState?.position?.collectAsState()
        ?: remember { mutableStateOf(org.opentopo.app.gnss.PositionState()) }
    val gnssAcc by stakeout?.gnssState?.accuracy?.collectAsState()
        ?: remember { mutableStateOf(org.opentopo.app.gnss.AccuracyState()) }
    val fixQuality = gnssPos.fixQuality
    val sigmaH = gnssAcc.horizontalAccuracyM
    val cardinal = result?.bearingCardinal ?: "—"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            // ── 1. Top row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onClearTarget,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close stakeout",
                            tint = HudForeground,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STAKEOUT · TARGET",
                        fontFamily = CoordinateFont,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = HudForeground.copy(alpha = 0.7f),
                    )
                    Text(
                        text = targetName.ifBlank { "—" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = (-0.22).sp,
                        lineHeight = 22.sp,
                        color = HudForeground,
                    )
                }
                HudFixPill(fixQuality = fixQuality, sigmaH = sigmaH)
            }

            // ── 2. Compass gauge ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                HudCompass(
                    bearingToTargetDeg = bearingToTarget,
                    deviceHeadingDeg = deviceHeading,
                    distanceMeters = horizontalDelta,
                    cardinal = cardinal,
                    waiting = result == null,
                )
            }

            // ── 3. Delta cards (ΔE ΔN ΔH) ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HudDeltaCard(label = "\u0394E", value = deltaE, modifier = Modifier.weight(1f))
                HudDeltaCard(label = "\u0394N", value = deltaN, modifier = Modifier.weight(1f))
                HudDeltaCard(label = "\u0394H", value = deltaH, modifier = Modifier.weight(1f))
            }

            // ── 4. Spacer fills remaining height ─────────────────────────
            Spacer(modifier = Modifier.weight(1f))

            // ── 5. Bottom action row ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "Next target" — outline button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = HudOutlineBorder,
                            shape = RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = onNextTarget,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = HudMint,
                        ),
                        border = null,
                    ) {
                        Text(
                            "Next target",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = HudMint,
                        )
                    }
                }
                // "Verify now" — filled mint button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(HudMint),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledTonalButton(
                        onClick = onVerifyRequest,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = HudMint,
                            contentColor = HudMintOn,
                        ),
                    ) {
                        Text(
                            "Verify now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = HudMintOn,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small mint pill for the HUD header — mimics `.pill` from the mockup.
 * Shows "RTK <σH cm>" using the live horizontal accuracy and fix quality.
 */
@Composable
private fun HudFixPill(fixQuality: Int, sigmaH: Double?) {
    val label = when (fixQuality) {
        4 -> "RTK"
        5 -> "FLT"
        2 -> "DGPS"
        1 -> "GPS"
        else -> "No Fix"
    }
    val value = sigmaH?.let { "${"%.1f".format(it * 100.0)} cm" }
    val text = if (value != null) "$label $value" else label

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(HudMint)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HudMintOn),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = HudMintOn,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                fontFamily = CoordinateFont,
            )
        }
    }
}

/**
 * 240 dp circular compass gauge.
 *
 * - Outer border: 2 dp dashed mint
 * - Inner ring at 14 dp inset: 1 dp solid white-18
 * - Cardinal labels N/S/E/W
 * - Chunky bearing arrow (mint), rotated by `(bearing - heading)`
 * - Center: large mint distance + "metres · <cardinal>" subtitle
 */
@Composable
private fun HudCompass(
    bearingToTargetDeg: Float,
    deviceHeadingDeg: Float,
    distanceMeters: Double?,
    cardinal: String,
    waiting: Boolean,
) {
    val arrowRotation = bearingToTargetDeg - deviceHeadingDeg

    Box(
        modifier = Modifier
            .size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Dashed mint outer border + inner white ring + arrow
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeOuter = 2.dp.toPx()
            val strokeInner = 1.dp.toPx()
            val inset = 14.dp.toPx()
            val radius = (size.minDimension - strokeOuter) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer dashed mint ring
            drawCircle(
                color = HudMint,
                radius = radius,
                center = center,
                style = Stroke(
                    width = strokeOuter,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        phase = 0f,
                    ),
                ),
            )

            // Inner solid ring at inset 14 dp
            drawCircle(
                color = HudInnerRing,
                radius = radius - inset,
                center = center,
                style = Stroke(width = strokeInner),
            )

            // Bearing arrow — path "M0,-90 L22,-30 L0,-46 L-22,-30 Z"
            // Mockup uses viewBox 220×220 (radius 110); scale to our 240 dp
            // canvas so the tip sits near the ring.
            if (!waiting) {
                val scale = (radius / 110f)
                rotate(degrees = arrowRotation, pivot = center) {
                    val path = Path().apply {
                        moveTo(center.x, center.y + (-90f) * scale)
                        lineTo(center.x + 22f * scale, center.y + (-30f) * scale)
                        lineTo(center.x, center.y + (-46f) * scale)
                        lineTo(center.x + (-22f) * scale, center.y + (-30f) * scale)
                        close()
                    }
                    drawPath(path = path, color = HudMint)
                }
            }
        }

        // Cardinal labels — absolute inside the ring
        Box(Modifier.size(240.dp)) {
            Text(
                text = "N",
                color = HudForeground.copy(alpha = 0.8f),
                fontFamily = CoordinateFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
            Text(
                text = "S",
                color = HudForeground.copy(alpha = 0.6f),
                fontFamily = CoordinateFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
            Text(
                text = "W",
                color = HudForeground.copy(alpha = 0.6f),
                fontFamily = CoordinateFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
            )
            Text(
                text = "E",
                color = HudForeground.copy(alpha = 0.6f),
                fontFamily = CoordinateFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
            )
        }

        // Center readout — distance + "metres · <cardinal>"
        Column(
            modifier = Modifier.zIndex(2f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (waiting) {
                ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Waiting for fix\u2026",
                    color = HudForeground.copy(alpha = 0.7f),
                    fontFamily = CoordinateFont,
                    fontSize = 12.sp,
                )
            } else {
                val distanceText = distanceMeters?.let { "%.2f".format(it) } ?: "—"
                Text(
                    text = distanceText,
                    fontFamily = CoordinateFont,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.W700,
                    lineHeight = 54.sp,
                    letterSpacing = (-1.6).sp,
                    color = HudMint,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "metres · $cardinal",
                    fontFamily = CoordinateFont,
                    fontSize = 12.sp,
                    color = HudForeground.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Translucent-white card for a single ΔE / ΔN / ΔH readout. The numeric tone
 * follows on-point magnitude: mint < 0.1 m < amber < 0.3 m < red.
 */
@Composable
private fun HudDeltaCard(
    label: String,
    value: Double?,
    modifier: Modifier = Modifier,
) {
    val tone: Color = when {
        value == null -> HudForeground.copy(alpha = 0.6f)
        abs(value) < 0.1 -> HudMint
        abs(value) < 0.3 -> HudDeltaAmber
        else -> HudDeltaRed
    }
    val formatted = when {
        value == null -> "—"
        value >= 0 -> "+${"%.2f".format(value)}"
        else -> "\u2212${"%.2f".format(-value)}"  // unicode minus for consistent glyph width
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HudCardBg)
            .border(
                width = 1.dp,
                color = HudCardBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = CoordinateFont,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = HudForeground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatted,
            fontFamily = CoordinateFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
            lineHeight = 24.sp,
            color = tone,
        )
        Text(
            text = "m",
            fontFamily = CoordinateFont,
            fontSize = 10.sp,
            color = HudForeground.copy(alpha = 0.6f),
        )
    }
}

/**
 * Target-entry form shown while no stakeout target has been set.
 *
 * Preserves the v1 CSV import launcher and manual name/E/N entry form so
 * externally-set targets (trig-point dialog etc.) still flow through.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StakeoutTargetForm(
    stakeout: Stakeout?,
    targetName: String,
    targetE: String,
    targetN: String,
    onTargetName: (String) -> Unit,
    onTargetE: (String) -> Unit,
    onTargetN: (String) -> Unit,
    crsLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val input = context.contentResolver.openInputStream(it) ?: return@let
                val reader = input.bufferedReader()
                reader.readLine() // skip header
                val firstLine = reader.readLine() ?: return@let
                val fields = firstLine.split(",")
                if (fields.size >= 3) {
                    onTargetName(fields[0].trim())
                    onTargetE(fields[1].trim())
                    onTargetN(fields[2].trim())
                }
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { importLauncher.launch("text/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importar alvo de CSV")
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Alvo ($crsLabel)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    value = targetName,
                    onValueChange = onTargetName,
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = targetE,
                        onValueChange = onTargetE,
                        label = { Text("E (m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                    )
                    TextField(
                        value = targetN,
                        onValueChange = onTargetN,
                        label = { Text("N (m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                    )
                }
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = {
                        val e = targetE.toDoubleOrNull()
                        val n = targetN.toDoubleOrNull()
                        if (e != null && n != null) {
                            stakeout?.setTarget(
                                StakeoutTarget(targetName.ifBlank { "Alvo" }, e, n),
                            )
                        }
                    },
                    enabled = targetE.toDoubleOrNull() != null && targetN.toDoubleOrNull() != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(percent = 50),
                ) {
                    Text("Iniciar")
                }
            }
        }
    }
}

// ── Stakeout immersive full-screen overlay (kept from v1, unchanged) ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StakeoutImmersiveOverlay(
    stakeout: Stakeout,
    gnssState: GnssState,
    onExit: () -> Unit,
) {
    val surveyColors = LocalSurveyColors.current
    val result by stakeout.result.collectAsState(initial = null)
    val position by gnssState.position.collectAsState()
    val accuracy by gnssState.accuracy.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Top-left: fix status pill
            FixStatusPill(
                position.fixQuality,
                Modifier.align(Alignment.TopStart),
            )

            // Top-right row: accuracy + exit button
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accuracy.horizontalAccuracyM?.let { h ->
                    Text(
                        "\u00B1${"%.3f".format(h)}m",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = CoordinateFont,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Exit full screen",
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }

            // Centre content
            val r = result
            if (r != null) {
                val distColor = surveyColors.stakeoutColor(r.distance)
                val dark = isSystemInDarkTheme()
                val onPoint = r.distance < 0.05
                val deviceHeading = rememberDeviceHeadingDeg()

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompassRing(
                        currentHeadingDeg = deviceHeading,
                        bearingToTargetDeg = r.bearingDeg.toFloat(),
                        distanceMeters = r.distance,
                        ringDiameter = 280.dp,
                        onPoint = onPoint,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "${r.bearingCardinal} (${"%.1f".format(r.bearingDeg)}\u00B0)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }

                // Bottom: delta E / delta N
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%.3f".format(r.deltaEasting),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = CoordinateFont,
                            color = distColor,
                        )
                        Text(
                            "\u0394E",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%.3f".format(r.deltaNorthing),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = CoordinateFont,
                            color = distColor,
                        )
                        Text(
                            "\u0394N",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                // Waiting for fix
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        "Waiting for fix\u2026",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}
