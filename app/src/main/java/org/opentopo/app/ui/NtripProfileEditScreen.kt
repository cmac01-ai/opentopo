package org.opentopo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.opentopo.app.ntrip.NtripBadgePalette
import org.opentopo.app.ntrip.NtripMountpoint
import org.opentopo.app.ntrip.NtripProfile
import org.opentopo.app.ntrip.RtcmVersion
import org.opentopo.app.ui.theme.CoordinateFont
import java.util.UUID

/**
 * Full-screen form for creating or editing an [NtripProfile].
 *
 * Renders a custom top bar, a scrolling field list, and a fixed bottom action
 * bar with Cancel / Save. Includes inline sourcetable scanning so the user can
 * pick a mountpoint without leaving the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtripProfileEditScreen(
    initial: NtripProfile?,
    onScanSourcetable: suspend (host: String, port: Int, user: String, pass: String, useTls: Boolean) -> Result<List<NtripMountpoint>>,
    onSave: (NtripProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // ── Form state ─────────────────────────────────────────────────────────
    var displayName by remember { mutableStateOf(initial?.displayName ?: "") }
    var code by remember {
        mutableStateOf(initial?.code ?: NtripBadgePalette.deriveCode(""))
    }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var portText by remember { mutableStateOf((initial?.port ?: 2101).toString()) }
    var useTls by remember { mutableStateOf(initial?.useTls ?: false) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mountpoint by remember { mutableStateOf(initial?.mountpoint ?: "") }
    var sendGga by remember { mutableStateOf(initial?.sendGga ?: true) }
    var rtcm by remember { mutableStateOf(initial?.rtcmPreference ?: RtcmVersion.ANY) }

    // Scan state.
    var scanResults by remember { mutableStateOf<List<NtripMountpoint>>(emptyList()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showScanList by remember { mutableStateOf(false) }

    // Effective code — fall back to derivation from displayName when blank.
    val effectiveCode = (if (code.isBlank()) NtripBadgePalette.deriveCode(displayName) else code)
        .uppercase()
        .take(2)
    val (badgeBg, badgeFg) = NtripBadgePalette.forCode(effectiveCode)

    // ── Validation ────────────────────────────────────────────────────────
    val nameError = displayName.isBlank()
    val hostError = host.isBlank() ||
        host.any { it.isWhitespace() } ||
        host.startsWith("http", ignoreCase = true)
    val port = portText.toIntOrNull()
    val portError = port == null || port !in 1..65535

    val isValid = !nameError && !hostError && !portError

    fun buildProfile(): NtripProfile {
        val now = System.currentTimeMillis()
        val finalCode = effectiveCode.ifBlank { "NT" }
        val (bg, fg) = NtripBadgePalette.forCode(finalCode)
        return NtripProfile(
            id = initial?.id ?: UUID.randomUUID().toString(),
            displayName = displayName.trim(),
            code = finalCode,
            tintColor = bg,
            badgeFgColor = fg,
            host = host.trim(),
            port = port ?: 2101,
            useTls = useTls,
            username = username,
            password = password,
            mountpoint = mountpoint.trim(),
            sendGga = sendGga,
            rtcmPreference = rtcm,
            isActive = initial?.isActive ?: false,
            lastUsedAt = initial?.lastUsedAt ?: 0L,
            createdAt = initial?.createdAt ?: now,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = scheme.surface,
        // Edge-to-edge safe zone: include status bar (time/signal/battery)
        // AND gesture-nav handle in the insets Scaffold applies to its slots.
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopBar(
                title = if (initial == null) "New profile" else "Edit profile",
                onBack = onCancel,
            )
        },
        bottomBar = {
            BottomActionBar(
                onCancel = onCancel,
                onSave = { onSave(buildProfile()) },
                isValid = isValid,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp),
        ) {
            // 1. Display name
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name is required") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 2. Badge code + preview
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { raw ->
                            code = raw.uppercase().filter { !it.isWhitespace() }.take(2)
                        },
                        label = { Text("Badge code (2 chars)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(badgeBg),
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = effectiveCode.ifBlank { "—" },
                            style = TextStyle(
                                fontFamily = CoordinateFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                            ),
                            color = Color(badgeFg),
                        )
                    }
                }
            }

            // 3. Host
            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    singleLine = true,
                    isError = hostError,
                    supportingText = if (hostError) {
                        { Text("Enter hostname or IP (no http://)") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4. Port
            item {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { raw -> portText = raw.filter { it.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    isError = portError,
                    supportingText = if (portError) {
                        { Text("Port must be 1–65535") }
                    } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 5. Use TLS
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Use TLS",
                            fontSize = 14.sp,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "Connect over TLS / HTTPS",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useTls,
                        onCheckedChange = { useTls = it },
                    )
                }
            }

            // 6. Username
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 7. Password
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 8. Mountpoint + Scan
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = mountpoint,
                        onValueChange = { mountpoint = it },
                        label = { Text("Mountpoint") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val p = portText.toIntOrNull() ?: return@Button
                            if (host.isBlank()) return@Button
                            scope.launch {
                                scanning = true
                                scanError = null
                                val result = onScanSourcetable(host.trim(), p, username, password, useTls)
                                scanning = false
                                result.fold(
                                    onSuccess = {
                                        scanResults = it
                                        showScanList = it.isNotEmpty()
                                        if (it.isEmpty()) {
                                            scanError = "No mountpoints returned"
                                        }
                                    },
                                    onFailure = { e ->
                                        scanError = "Scan failed: ${e.message ?: "unknown error"}"
                                        showScanList = false
                                    },
                                )
                            }
                        },
                        enabled = !scanning && host.isNotBlank() && port != null,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onPrimary,
                            )
                        } else {
                            Text("Scan…", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (scanError != null) {
                item {
                    Text(
                        text = scanError!!,
                        color = scheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            if (showScanList && scanResults.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Approx: each row ~44dp; 10 rows + padding ≈ 456dp
                        InteractiveMountpointList(
                            results = scanResults,
                            onPick = { picked ->
                                mountpoint = picked.name
                                showScanList = false
                            },
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }

            // 9. Send NMEA GGA
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Send NMEA GGA",
                            fontSize = 14.sp,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "Required by most VRS casters",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = sendGga,
                        onCheckedChange = { sendGga = it },
                    )
                }
            }

            // 10. Preferred RTCM version
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Preferred RTCM version",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                    )
                    val options = listOf(
                        "3.2" to RtcmVersion.V32,
                        "3.3" to RtcmVersion.V33,
                        "Any" to RtcmVersion.ANY,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (label, value) ->
                            SegmentedButton(
                                selected = rtcm == value,
                                onClick = { rtcm = value },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            ) {
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveMountpointList(
    results: List<NtripMountpoint>,
    onPick: (NtripMountpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 456.dp),
    ) {
        items(results.size) { idx ->
            val r = results[idx]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(r) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = r.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = scheme.onSurface,
                    style = TextStyle(fontFamily = CoordinateFont),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildString {
                        append(r.format.ifBlank { "—" })
                        if (r.country.isNotBlank()) {
                            append(" · ")
                            append(r.country)
                        }
                    },
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        shadowElevation = 0.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = scheme.surfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "GNSS · NTRIP",
                    style = TextStyle(
                        fontFamily = CoordinateFont,
                        fontSize = 11.sp,
                        letterSpacing = 0.06.em,
                    ),
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = (-0.01).em,
                    ),
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    isValid: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    color = scheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                )
            }
        }
    }
}
