package org.opentopo.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opentopo.app.gnss.BluetoothGnssService
import org.opentopo.app.gnss.Constellation
import org.opentopo.app.gnss.ConnectionStatus
import org.opentopo.app.gnss.GnssState
import org.opentopo.app.gnss.SatelliteInfo
import org.opentopo.app.gnss.Transport
import org.opentopo.app.gnss.UsbGnssService
import org.opentopo.app.ntrip.NtripClient
import org.opentopo.app.ntrip.NtripConnectionState
import org.opentopo.app.ui.components.ButtonGroup as OpenTopoButtonGroup
import org.opentopo.app.ui.components.ntrip.NtripActiveProfileRow
import org.opentopo.app.ui.theme.ConstellationColors
import org.opentopo.app.ui.theme.CoordinateFont
import org.opentopo.app.ui.theme.FixNoneRed

// ── Mono styles matching the mockup ──
private val MonoOverline = TextStyle(
    fontFamily = CoordinateFont,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 0.8.sp,
)

private val MonoCellLabel = TextStyle(
    fontFamily = CoordinateFont,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.W500,
    letterSpacing = 0.8.sp,
)

private val MonoCellValue = TextStyle(
    fontFamily = CoordinateFont,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.W700,
)

private val MonoStatValue = TextStyle(
    fontFamily = CoordinateFont,
    fontSize = 17.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.W700,
)

private val MonoSub = TextStyle(
    fontFamily = CoordinateFont,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.W400,
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionPanel(
    gnssState: GnssState,
    bluetoothService: BluetoothGnssService,
    usbService: UsbGnssService,
    internalService: org.opentopo.app.gnss.InternalGnssService,
    ntripClient: NtripClient,
    ntripProfileRepo: org.opentopo.app.ntrip.NtripProfileRepository,
    onNtripRowClick: () -> Unit,
    onNtripReconnectClick: () -> Unit = onNtripRowClick,
    modifier: Modifier = Modifier,
) {
    val connectionStatus by gnssState.connectionStatus.collectAsState()
    val activeTransport by gnssState.activeTransport.collectAsState()
    val accuracy by gnssState.accuracy.collectAsState()
    val position by gnssState.position.collectAsState()
    val satellites by gnssState.satellites.collectAsState()
    val ntripState by ntripClient.state.collectAsState()
    val connectedBtDevice by bluetoothService.connectedDevice.collectAsState()
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val savedBaud by activity?.prefs?.baudRate?.collectAsState(initial = 115200)
        ?: remember { mutableStateOf(115200) }
    val savedConnType by activity?.prefs?.connectionType?.collectAsState(initial = 0)
        ?: remember { mutableStateOf(0) }

    var connectionType by remember { mutableIntStateOf(savedConnType.coerceIn(0, 2)) }
    LaunchedEffect(savedConnType) { connectionType = savedConnType.coerceIn(0, 2) }

    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val isConnecting = connectionStatus == ConnectionStatus.CONNECTING

    // Index of the actually-connected transport in the ButtonGroup, or -1 if
    // nothing is live. The card / connecting indicator only appear on the
    // matching tab — other tabs render their picker so the user can switch.
    val activeTransportIndex = when (activeTransport) {
        Transport.BLUETOOTH -> 0
        Transport.USB -> 1
        Transport.INTERNAL -> 2
        null -> -1
    }
    val tabMatchesActive = connectionType == activeTransportIndex

    // Track elapsed time since connection established — resets on every
    // transition into CONNECTED. Drives the "CONNECTED · mm:ss" overline.
    var connectedAtMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isConnected) {
        if (isConnected) connectedAtMs = System.currentTimeMillis()
    }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isConnected, connectedAtMs) {
        while (isConnected) {
            elapsedSec = (System.currentTimeMillis() - connectedAtMs) / 1000
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Connection type ButtonGroup ──
        OpenTopoButtonGroup(
            options = listOf("Bluetooth", "USB-OTG", "Internal"),
            selectedIndex = connectionType,
            onSelect = { connectionType = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // The connected card only belongs on the tab matching the active
        // transport \u2014 otherwise every tab would show "CONNECTED" once any
        // transport came up, regardless of which one. Tabs that don't match
        // render their picker so the user can switch transports.
        if (isConnected && tabMatchesActive) {
            val methodLabel = when (activeTransport) {
                Transport.BLUETOOTH -> "Bluetooth"
                Transport.USB -> "USB-OTG"
                Transport.INTERNAL -> "Internal GPS"
                null -> "Connected"
            }
            val deviceName = when (activeTransport) {
                Transport.BLUETOOTH -> connectedBtDevice?.name ?: "GNSS Receiver"
                Transport.USB -> "USB Serial GNSS"
                Transport.INTERNAL -> "Internal GPS"
                null -> "GNSS Receiver"
            }
            val deviceMeta = when (activeTransport) {
                Transport.BLUETOOTH -> buildString {
                    val addr = connectedBtDevice?.address
                    if (!addr.isNullOrBlank()) {
                        append(addr)
                        append(" \u00B7 ")
                    }
                    append("BT SPP")
                }
                Transport.USB -> "USB \u00B7 $savedBaud bps"
                Transport.INTERNAL, null -> methodLabel
            }
            ConnectedReceiverCard(
                deviceName = deviceName,
                deviceMeta = deviceMeta,
                elapsedSec = elapsedSec,
                horizontalAccuracyM = accuracy.horizontalAccuracyM,
                hdop = accuracy.hdop,
                satsTracked = accuracy.activeSatelliteKeys.size.takeIf { it > 0 }
                    ?: accuracy.activeSatellitePrns.size,
                satsVisible = satellites.satellites.size,
                onDisconnect = {
                    bluetoothService.disconnect()
                    usbService.disconnect()
                    internalService.disconnect()
                },
            )
        } else if (isConnecting && tabMatchesActive) {
            ConnectingCard()
        } else {
            when (connectionType) {
                0 -> BluetoothPicker(bluetoothService)
                1 -> UsbPicker(usbService)
                else -> InternalGpsPicker(internalService)
            }
        }

        // Constellations + skyplot stay scoped to the connected transport's
        // tab — otherwise the USB picker would render with BT-derived sky
        // data attached underneath.
        if (isConnected && tabMatchesActive && satellites.satellites.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ConstellationsCard(
                satellites = satellites.satellites,
                activeKeys = accuracy.activeSatelliteKeys,
                activePrns = accuracy.activeSatellitePrns,
            )
            Spacer(Modifier.height(12.dp))
            Skyplot(satellites = satellites.satellites)
        }

        // ── NTRIP — compact active-profile row ──
        Spacer(Modifier.height(14.dp))
        val activeProfile by ntripProfileRepo.activeProfile.collectAsState(initial = null)
        val ntripConnectionState by ntripProfileRepo.state.collectAsState(
            initial = org.opentopo.app.ntrip.NtripConnectionState.Empty,
        )
        NtripActiveProfileRow(
            profile = activeProfile,
            state = ntripConnectionState,
            correctionAgeSeconds = position.ageOfDgpsSeconds,
            onClick = onNtripRowClick,
            onReconnectClick = onNtripReconnectClick,
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Connected receiver hero card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectedReceiverCard(
    deviceName: String,
    deviceMeta: String,
    elapsedSec: Long,
    horizontalAccuracyM: Double?,
    hdop: Double?,
    satsTracked: Int,
    satsVisible: Int,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Column(Modifier.padding(18.dp)) {
            // Top row: pulsing dot + "CONNECTED · mm:ss"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PulsingDot(color = MaterialTheme.colorScheme.primary, size = 10)
                val elapsedLabel = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                Text(
                    "CONNECTED \u00B7 $elapsedLabel",
                    style = MonoOverline.copy(letterSpacing = 1.2.sp),
                    color = onContainer,
                )
            }
            Spacer(Modifier.height(6.dp))

            // Device name
            Text(
                deviceName,
                style = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.09).sp,
                ),
                color = onContainer,
            )

            // MAC / baud / protocol
            Text(
                deviceMeta,
                style = MonoSub,
                color = onContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(14.dp))

            // Bottom row: stats + Disconnect
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCell(
                    label = "\u03C3H",
                    value = horizontalAccuracyM?.let { formatAccuracy(it) } ?: "\u2014",
                    onColor = onContainer,
                )
                StatCell(
                    label = "HDOP",
                    value = hdop?.let { "%.1f".format(it) } ?: "\u2014",
                    onColor = onContainer,
                )
                StatCell(
                    label = "SATS",
                    value = if (satsVisible > 0) "$satsTracked/$satsVisible" else "\u2014",
                    onColor = onContainer,
                )
                Button(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixNoneRed,
                        contentColor = Color.White,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 8.dp,
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 1.dp),
                ) {
                    Text(
                        "Disconnect",
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.W700,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, onColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MonoCellLabel,
            color = onColor.copy(alpha = 0.75f),
        )
        Text(
            value,
            style = MonoStatValue,
            color = onColor,
        )
    }
}

/** Horizontal-accuracy formatter — cm if < 1 m, m otherwise. */
private fun formatAccuracy(m: Double): String = if (m < 1.0) {
    "%.1f cm".format(m * 100)
} else {
    "%.2f m".format(m)
}

@Composable
private fun PulsingDot(color: Color, size: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "connDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connDotAlpha",
    )
    Canvas(Modifier.size(size.dp)) {
        drawCircle(
            color = color,
            radius = this.size.minDimension / 2f,
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            alpha = alpha,
        )
    }
}

@Composable
private fun ConnectingCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ContainedLoadingIndicator(modifier = Modifier.size(24.dp))
            Text(
                "Connecting\u2026",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Constellations grid
// ─────────────────────────────────────────────────────────────────────────────

private data class ConstCell(
    val name: String,
    val constellation: Constellation,
    val color: Color,
)

private val CONSTELLATION_CELLS = listOf(
    ConstCell("GPS", Constellation.GPS, ConstellationColors.gps),
    ConstCell("GLO", Constellation.GLONASS, ConstellationColors.glonass),
    ConstCell("GAL", Constellation.GALILEO, ConstellationColors.galileo),
    ConstCell("BDS", Constellation.BEIDOU, ConstellationColors.beidou),
)

@Composable
private fun ConstellationsCard(
    satellites: List<SatelliteInfo>,
    activeKeys: Set<org.opentopo.app.gnss.SatelliteKey>,
    activePrns: List<Int>,
) {
    val byConst = satellites.groupBy { it.constellation }
    val activePrnSet = activePrns.toSet()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "CONSTELLATIONS",
                    style = MonoOverline,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 4-column grid — Row with weighted cells (Compose-native, no LazyGrid overhead)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONSTELLATION_CELLS.forEach { cell ->
                    val sats = byConst[cell.constellation].orEmpty()
                    val tracked = if (activeKeys.isNotEmpty()) {
                        sats.count { sat ->
                            org.opentopo.app.gnss.SatelliteKey(cell.constellation, sat.prn) in activeKeys
                        }
                    } else {
                        sats.count { it.prn in activePrnSet }
                    }
                    ConstellationCell(
                        name = cell.name,
                        color = cell.color,
                        tracked = tracked,
                        visible = sats.size,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConstellationCell(
    name: String,
    color: Color,
    tracked: Int,
    visible: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row {
            // 3 dp coloured left border
            Box(
                Modifier
                    .width(3.dp)
                    .height(50.dp)
                    .background(color),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    name,
                    style = MonoCellLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (visible > 0) "$tracked/$visible" else "\u2014",
                    style = MonoCellValue,
                    color = color,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Disconnected pickers (same behaviour as before, restyled to match the
//  surface-container-low cards used elsewhere on the screen).
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothPicker(bluetoothService: BluetoothGnssService) {
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val devices = remember { bluetoothService.getPairedDevices() }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "BLUETOOTH \u00B7 PAIRED RECEIVERS",
                style = MonoOverline,
                color = MaterialTheme.colorScheme.primary,
            )
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                TextField(
                    value = selectedDevice?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device") },
                    placeholder = { Text("Select device\u2026") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name ?: device.address) },
                            onClick = { selectedDevice = device; expanded = false },
                        )
                    }
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No paired devices") },
                            onClick = { expanded = false },
                        )
                    }
                }
            }
            Button(
                onClick = {
                    selectedDevice?.let { device ->
                        bluetoothService.connect(device)
                        scope.launch { activity?.prefs?.setConnectionType(0) }
                    }
                },
                enabled = selectedDevice != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Icon(Icons.Outlined.Bluetooth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect Bluetooth", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsbPicker(usbService: UsbGnssService) {
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val usbVersion by activity?.usbDeviceVersion?.collectAsState() ?: remember { mutableStateOf(0) }
    val baudRate by activity?.prefs?.baudRate?.collectAsState(initial = 115200)
        ?: remember { mutableStateOf(115200) }
    val drivers = remember(usbVersion) { usbService.getAvailableDevices() }
    var expanded by remember { mutableStateOf(false) }
    var selectedDriver by remember { mutableStateOf<UsbSerialDriver?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "USB-OTG \u00B7 ATTACHED RECEIVERS",
                style = MonoOverline,
                color = MaterialTheme.colorScheme.primary,
            )
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                TextField(
                    value = selectedDriver?.device?.productName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device") },
                    placeholder = { Text("Select USB device\u2026") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    drivers.forEach { driver ->
                        DropdownMenuItem(
                            text = {
                                Text(driver.device.productName ?: "USB ${driver.device.deviceId}")
                            },
                            onClick = { selectedDriver = driver; expanded = false },
                        )
                    }
                    if (drivers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No USB devices found") },
                            onClick = { expanded = false },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { activity?.refreshUsbDevices() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelLargeEmphasized)
                }
                Button(
                    onClick = {
                        selectedDriver?.let { driver ->
                            usbService.connect(driver, baudRate)
                            scope.launch { activity?.prefs?.setConnectionType(1) }
                        }
                    },
                    enabled = selectedDriver != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(Icons.Outlined.Usb, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connect", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

@Composable
private fun InternalGpsPicker(internalService: org.opentopo.app.gnss.InternalGnssService) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            internalService.connect()
            scope.launch { activity?.prefs?.setConnectionType(2) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "INTERNAL GPS",
                style = MonoOverline,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Use the device\u2019s built-in GPS. No RTK corrections available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (internalService.hasPermission()) {
                        internalService.connect()
                        scope.launch { activity?.prefs?.setConnectionType(2) }
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connect Internal GPS",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Public helper — kept so SurveyPanel / StakeoutPanel can render a
//  label/value row in the same style.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SurveyStatusRow(
    label: String,
    value: String,
    detail: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = CoordinateFont,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
