package org.opentopo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.opentopo.app.db.AppDatabase
import org.opentopo.app.db.ProjectEntity
import org.opentopo.app.survey.SurveyManager
import org.opentopo.app.ui.theme.CoordinateFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    db: AppDatabase,
    surveyManager: SurveyManager?,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? org.opentopo.app.MainActivity
    val prefs = activity?.prefs
    val scope = rememberCoroutineScope()

    val activeProjectId by surveyManager?.activeProjectId?.collectAsState()
        ?: remember { mutableStateOf<Long?>(null) }
    var activeProject by remember { mutableStateOf<ProjectEntity?>(null) }

    LaunchedEffect(activeProjectId) {
        activeProject = activeProjectId?.let { db.projectDao().getById(it) }
    }

    suspend fun updateProject(transform: (ProjectEntity) -> ProjectEntity) {
        val current = activeProject ?: return
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        db.projectDao().update(updated)
        activeProject = updated
        surveyManager?.setActiveProject(updated.id)
    }

    // Collect all settings
    val antennaHeight by prefs?.antennaHeight?.collectAsState(initial = "1.80")
        ?: remember { mutableStateOf("1.80") }
    val averagingSeconds by prefs?.averagingSeconds?.collectAsState(initial = 5)
        ?: remember { mutableStateOf(5) }
    val minAccuracy by prefs?.minAccuracyM?.collectAsState(initial = "0.05")
        ?: remember { mutableStateOf("0.05") }
    val requireRtk by prefs?.requireRtkFix?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val baudRate by prefs?.baudRate?.collectAsState(initial = 115200)
        ?: remember { mutableStateOf(115200) }
    val ggaInterval by prefs?.ggaIntervalSeconds?.collectAsState(initial = 10)
        ?: remember { mutableStateOf(10) }
    val coordFormat by prefs?.coordFormat?.collectAsState(initial = 0)
        ?: remember { mutableStateOf(0) }
    val gloveMode by prefs?.gloveMode?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val preferReceiverGeoid by prefs?.preferReceiverGeoid?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Project coordinate reference system ──
        Text(
            "PROJETO · COORDENADAS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(activeProject?.name ?: "Nenhum projeto ativo") },
                    supportingContent = {
                        Text(
                            if (activeProject != null) "SIRGAS2000 / UTM · GRS80"
                            else "Selecione ou crie um projeto para alterar fuso e hemisfério"
                        )
                    },
                )

                if (activeProject != null) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Hemisfério") },
                        supportingContent = { Text("Automático usa o sinal da latitude GNSS") },
                        trailingContent = {
                            var expanded by remember { mutableStateOf(false) }
                            val current = activeProject?.utmHemisphere ?: "AUTO"
                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Text(
                                        when (current) {
                                            "NORTH" -> "Norte"
                                            "SOUTH" -> "Sul"
                                            else -> "Automático"
                                        },
                                        fontFamily = CoordinateFont,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    listOf(
                                        "AUTO" to "Automático",
                                        "NORTH" to "Norte",
                                        "SOUTH" to "Sul",
                                    ).forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                scope.launch {
                                                    updateProject { project ->
                                                        project.copy(
                                                            utmHemisphere = value,
                                                            utmZone = if (value == "NORTH" && (project.utmZone ?: 18) > 22) {
                                                                null
                                                            } else {
                                                                project.utmZone
                                                            },
                                                        )
                                                    }
                                                }
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )

                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Fuso UTM") },
                        supportingContent = { Text("Automático usa a longitude GNSS") },
                        trailingContent = {
                            var expanded by remember { mutableStateOf(false) }
                            val project = activeProject!!
                            val suffix = when (project.utmHemisphere) {
                                "NORTH" -> "N"
                                "SOUTH" -> "S"
                                else -> ""
                            }
                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Text(
                                        project.utmZone?.let { "$it$suffix" } ?: "Automático",
                                        fontFamily = CoordinateFont,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Automático") },
                                        onClick = {
                                            scope.launch { updateProject { it.copy(utmZone = null) } }
                                            expanded = false
                                        },
                                    )
                                    val zones = if (project.utmHemisphere == "NORTH") 18..22 else 18..25
                                    zones.forEach { zone ->
                                        DropdownMenuItem(
                                            text = { Text("Fuso $zone$suffix") },
                                            onClick = {
                                                scope.launch {
                                                    updateProject { it.copy(utmZone = zone) }
                                                }
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        // ── Recording settings ──
        Text(
            "RECORDING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Altura da antena (m)") },
                    supportingContent = { Text("Altura do ponto medido até o ARP/ referência usada") },
                    trailingContent = {
                        OutlinedTextField(
                            value = antennaHeight,
                            onValueChange = { value ->
                                scope.launch { prefs?.setAntennaHeight(value) }
                            },
                            modifier = Modifier.width(120.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CoordinateFont,
                            ),
                            singleLine = true,
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Tempo de média") },
                    supportingContent = { Text("Duração da média de épocas") },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text("${averagingSeconds}s", fontFamily = CoordinateFont)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf(1, 3, 5, 10, 15, 30, 60).forEach { secs ->
                                    DropdownMenuItem(
                                        text = { Text("${secs}s") },
                                        onClick = {
                                            scope.launch { prefs?.setAveragingSeconds(secs) }
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Precisão máxima (m)") },
                    trailingContent = {
                        OutlinedTextField(
                            value = minAccuracy,
                            onValueChange = { scope.launch { prefs?.setMinAccuracyM(it) } },
                            modifier = Modifier.width(120.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CoordinateFont,
                            ),
                            singleLine = true,
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Exigir RTK Fix") },
                    supportingContent = { Text("Só grava ponto quando a solução estiver FIX") },
                    trailingContent = {
                        Switch(
                            checked = requireRtk,
                            onCheckedChange = {
                                scope.launch { prefs?.setRequireRtkFix(it) }
                            },
                        )
                    },
                )
            }
        }

        // ── Connection settings ──
        Text(
            "CONEXÃO",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Baud rate / serial") },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text("$baudRate", fontFamily = CoordinateFont)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf(4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800)
                                    .forEach { rate ->
                                        DropdownMenuItem(
                                            text = { Text("$rate") },
                                            onClick = {
                                                scope.launch { prefs?.setBaudRate(rate) }
                                                expanded = false
                                            },
                                        )
                                    }
                            }
                        }
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Intervalo GGA para NTRIP") },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text("${ggaInterval}s", fontFamily = CoordinateFont)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf(5, 10, 15, 30, 60).forEach { secs ->
                                    DropdownMenuItem(
                                        text = { Text("${secs}s") },
                                        onClick = {
                                            scope.launch { prefs?.setGgaIntervalSeconds(secs) }
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        // ── Display settings ──
        Text(
            "EXIBIÇÃO",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Modo luvas") },
                    supportingContent = { Text("Alvos maiores e botões de volume para campo") },
                    trailingContent = {
                        Switch(
                            checked = gloveMode,
                            onCheckedChange = {
                                scope.launch { prefs?.setGloveMode(it) }
                            },
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Formato de coordenadas") },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        val formatLabels = listOf("SIRGAS2000 / UTM", "SIRGAS2000 Decimal", "Graus/min/seg")
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(formatLabels.getOrElse(coordFormat) { formatLabels[0] })
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                formatLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            scope.launch { prefs?.setCoordFormat(index) }
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Referência de altura") },
                    supportingContent = {
                        Text(
                            "Receptor GNSS (NMEA GGA) · hgeoHNOR2020 em etapa futura",
                        )
                    },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text("Receptor")
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Receptor GNSS / NMEA GGA") },
                                    onClick = {
                                        scope.launch { prefs?.setPreferReceiverGeoid(true) }
                                        expanded = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

        // ── About ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Sobre", style = MaterialTheme.typography.titleMedium)
                Text("OpenTopo ${appVersion()}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Levantamento GNSS RTK open source para Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val context = LocalContext.current
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/cmac01-ai/opentopo") },
                ) {
                    Icon(Icons.Outlined.Code, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Código-fonte no GitHub")
                }
                Spacer(Modifier.height(8.dp))
                Text("CREDITS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))

                Text("Developed by Pierros Papadeas", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))

                Text("Sistema geodésico", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("SIRGAS2000 / UTM · elipsoide GRS80", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("Fusos brasileiros: 18–25S e 18–22N", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                Spacer(Modifier.height(4.dp))
                Text("Dados e serviços", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Mapa: OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("NTRIP: compatível com RBMC-IP / IBGE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                Spacer(Modifier.height(4.dp))
                Text("Libraries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("MapLibre GL Native (BSD-2-Clause)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("usb-serial-for-android (MIT)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("Jetpack Compose, Room, DataStore (Apache-2.0)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                Spacer(Modifier.height(4.dp))
                Text("License: GNU AGPL v3.0", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}
