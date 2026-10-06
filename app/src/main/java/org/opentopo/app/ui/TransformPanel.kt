package org.opentopo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opentopo.app.ui.theme.CoordinateFont
import org.opentopo.transform.GeographicCoordinate
import org.opentopo.transform.SirgasUtmCoordinate
import org.opentopo.transform.SirgasUtmTransform
import org.opentopo.transform.UtmHemisphere

@Composable
fun TransformPanel(modifier: Modifier = Modifier) {
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("0.0") }
    var zoneOverride by remember { mutableStateOf<Int?>(null) }
    var hemisphereMode by remember { mutableStateOf("AUTO") }
    var forwardResult by remember { mutableStateOf<SirgasUtmCoordinate?>(null) }
    var forwardError by remember { mutableStateOf<String?>(null) }

    var eastingInput by remember { mutableStateOf("") }
    var northingInput by remember { mutableStateOf("") }
    var inverseZone by remember { mutableStateOf(23) }
    var inverseHemisphere by remember { mutableStateOf(UtmHemisphere.SOUTH) }
    var inverseResult by remember { mutableStateOf<GeographicCoordinate?>(null) }
    var inverseError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Conversor SIRGAS2000 / UTM", style = MaterialTheme.typography.titleLarge)
        Text(
            "Elipsoide GRS80 · fusos brasileiros 18–25S e 18–22N",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Geográficas → UTM", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latInput, onValueChange = { latInput = it },
                        label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CoordinateFont),
                    )
                    OutlinedTextField(
                        value = lonInput, onValueChange = { lonInput = it },
                        label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CoordinateFont),
                    )
                }
                OutlinedTextField(
                    value = heightInput, onValueChange = { heightInput = it },
                    label = { Text("Altura (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CoordinateFont),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var hemiExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { hemiExpanded = true }) {
                            Text(when (hemisphereMode) {
                                "NORTH" -> "Norte"
                                "SOUTH" -> "Sul"
                                else -> "Hemisfério auto"
                            })
                        }
                        DropdownMenu(expanded = hemiExpanded, onDismissRequest = { hemiExpanded = false }) {
                            listOf("AUTO" to "Automático", "NORTH" to "Norte", "SOUTH" to "Sul").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        hemisphereMode = value
                                        if (value == "NORTH" && (zoneOverride ?: 18) > 22) zoneOverride = null
                                        hemiExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    var zoneExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { zoneExpanded = true }) {
                            Text(zoneOverride?.let { "Fuso $it" } ?: "Fuso auto")
                        }
                        DropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                            DropdownMenuItem(text = { Text("Automático") }, onClick = {
                                zoneOverride = null
                                zoneExpanded = false
                            })
                            val zones = if (hemisphereMode == "NORTH") 18..22 else 18..25
                            zones.forEach { zone ->
                                DropdownMenuItem(text = { Text("Fuso $zone") }, onClick = {
                                    zoneOverride = zone
                                    zoneExpanded = false
                                })
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val lat = latInput.replace(',', '.').toDoubleOrNull()
                        val lon = lonInput.replace(',', '.').toDoubleOrNull()
                        val h = heightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                        if (lat == null || lon == null) {
                            forwardError = "Informe latitude e longitude válidas."
                            forwardResult = null
                        } else {
                            try {
                                val hemi = when (hemisphereMode) {
                                    "NORTH" -> UtmHemisphere.NORTH
                                    "SOUTH" -> UtmHemisphere.SOUTH
                                    else -> null
                                }
                                forwardResult = SirgasUtmTransform.forward(
                                    coordinate = GeographicCoordinate(lat, lon, h),
                                    zoneOverride = zoneOverride,
                                    hemisphereOverride = hemi,
                                )
                                forwardError = null
                            } catch (e: Exception) {
                                forwardError = e.message
                                forwardResult = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Converter para UTM") }

                forwardError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                forwardResult?.let { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "SIRGAS2000 · UTM ${result.zone}${result.hemisphere.code}" +
                                    (result.epsg?.let { " · EPSG $it" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text("E  ${"%.3f".format(result.eastingM)} m", fontFamily = CoordinateFont)
                            Text("N  ${"%.3f".format(result.northingM)} m", fontFamily = CoordinateFont)
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("UTM → Geográficas", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eastingInput, onValueChange = { eastingInput = it },
                        label = { Text("Easting") }, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CoordinateFont),
                    )
                    OutlinedTextField(
                        value = northingInput, onValueChange = { northingInput = it },
                        label = { Text("Northing") }, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CoordinateFont),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var invZoneExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { invZoneExpanded = true }) { Text("Fuso $inverseZone") }
                        DropdownMenu(expanded = invZoneExpanded, onDismissRequest = { invZoneExpanded = false }) {
                            val zones = if (inverseHemisphere == UtmHemisphere.NORTH) 18..22 else 18..25
                            zones.forEach { zone ->
                                DropdownMenuItem(text = { Text("Fuso $zone") }, onClick = {
                                    inverseZone = zone
                                    invZoneExpanded = false
                                })
                            }
                        }
                    }

                    var invHemiExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { invHemiExpanded = true }) {
                            Text(if (inverseHemisphere == UtmHemisphere.NORTH) "Norte" else "Sul")
                        }
                        DropdownMenu(expanded = invHemiExpanded, onDismissRequest = { invHemiExpanded = false }) {
                            DropdownMenuItem(text = { Text("Norte") }, onClick = {
                                inverseHemisphere = UtmHemisphere.NORTH
                                if (inverseZone > 22) inverseZone = 22
                                invHemiExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Sul") }, onClick = {
                                inverseHemisphere = UtmHemisphere.SOUTH
                                invHemiExpanded = false
                            })
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val e = eastingInput.replace(',', '.').toDoubleOrNull()
                        val n = northingInput.replace(',', '.').toDoubleOrNull()
                        if (e == null || n == null) {
                            inverseError = "Informe Easting e Northing válidos."
                            inverseResult = null
                        } else {
                            try {
                                inverseResult = SirgasUtmTransform.inverse(
                                    SirgasUtmCoordinate(
                                        eastingM = e, northingM = n, zone = inverseZone,
                                        hemisphere = inverseHemisphere,
                                    )
                                )
                                inverseError = null
                            } catch (ex: Exception) {
                                inverseError = ex.message
                                inverseResult = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Converter para latitude/longitude") }

                inverseError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                inverseResult?.let { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("SIRGAS2000", style = MaterialTheme.typography.labelLarge)
                            Text("Lat  ${"%.9f".format(result.latitudeDeg)}°", fontFamily = CoordinateFont)
                            Text("Lon  ${"%.9f".format(result.longitudeDeg)}°", fontFamily = CoordinateFont)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
