package org.opentopo.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opentopo.app.db.AppDatabase
import org.opentopo.app.db.PointEntity
import org.opentopo.app.db.ProjectEntity
import org.opentopo.app.export.CsvExporter
import org.opentopo.app.export.CsvImporter
import org.opentopo.app.export.DxfExporter
import org.opentopo.app.export.GeoJsonExporter
import org.opentopo.app.export.ShapefileExporter
import org.opentopo.app.ui.theme.CoordinateFont

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExportPanel(
    db: AppDatabase,
    modifier: Modifier = Modifier,
) {
    val projects by db.projectDao().getAll().collectAsState(initial = emptyList())
    var selectedProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun saveToUri(
        uri: Uri?,
        label: String,
        exporter: (List<PointEntity>, ProjectEntity, java.io.OutputStream) -> Unit,
    ) {
        val project = selectedProject ?: return
        if (uri == null) return
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val count = writeExportToUri(context, db, project, uri, exporter)
                exportStatus = if (count != null) {
                    "$label salvo · $count pontos"
                } else {
                    "Nenhum ponto para exportar"
                }
            } catch (e: Exception) {
                exportStatus = "Falha ao salvar $label: ${e.message ?: "erro desconhecido"}"
            } finally {
                isExporting = false
            }
        }
    }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        saveToUri(uri, "CSV") { points, _, output -> CsvExporter.export(points, output) }
    }
    val geoJsonSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        saveToUri(uri, "GeoJSON") { points, project, output ->
            GeoJsonExporter.export(points, project.name, output)
        }
    }
    val dxfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/dxf")
    ) { uri ->
        saveToUri(uri, "DXF") { points, _, output -> DxfExporter.export(points, output) }
    }
    val shpSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        saveToUri(uri, "Shapefile") { points, project, output ->
            ShapefileExporter.export(points, project, output)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        Spacer(Modifier.height(8.dp))

        if (projects.isEmpty()) {
            /* ---- Empty state ---- */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No projects yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Create a project and collect points to export survey data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            /* ---- Project selector ---- */
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedProject?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Project") },
                    placeholder = { Text("Select project\u2026") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProject = project
                                expanded = false
                                exportStatus = null
                            },
                        )
                    }
                }
            }

            /* ---- Import / Export ---- */
            val proj = selectedProject
            if (proj == null) {
                Text(
                    "Select a project above to export its survey points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                /* ---- Import CSV ---- */
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        scope.launch {
                            try {
                                val input = context.contentResolver.openInputStream(it)
                                    ?: return@launch
                                val points = CsvImporter.import(input, proj.id)
                                input.close()
                                withContext(Dispatchers.IO) {
                                    points.forEach { pt -> db.pointDao().insert(pt) }
                                }
                                exportStatus = "Imported ${points.size} points"
                            } catch (e: Exception) {
                                exportStatus = "Import failed: ${e.message}"
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { importLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(
                        Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import CSV")
                }

                Text(
                    "Export format",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isExporting) {
                    /* ---- Loading indicator while exporting ---- */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Exporting\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        /* -- CSV (primary export) -- */
                        FilledTonalButton(
                            onClick = {
                                csvSaveLauncher.launch(exportFileName(proj, "csv"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                        ) {
                            Icon(
                                Icons.Outlined.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar CSV")
                        }

                        /* -- GeoJSON -- */
                        OutlinedButton(
                            onClick = {
                                geoJsonSaveLauncher.launch(exportFileName(proj, "geojson"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(
                                Icons.Outlined.Map,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar GeoJSON")
                        }

                        /* -- DXF -- */
                        OutlinedButton(
                            onClick = {
                                dxfSaveLauncher.launch(exportFileName(proj, "dxf"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(
                                Icons.Outlined.Architecture,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar DXF")
                        }

                        /* -- Shapefile ZIP -- */
                        OutlinedButton(
                            onClick = {
                                shpSaveLauncher.launch(exportFileName(proj, "zip"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar Shapefile (.zip)")
                        }
                    }
                }
            }

            /* ---- Export status chip ---- */
            exportStatus?.let { status ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            status,
                            fontFamily = CoordinateFont,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

private fun exportFileName(project: ProjectEntity, ext: String): String {
    val safe = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return "$safe.$ext"
}

private suspend fun writeExportToUri(
    context: Context,
    db: AppDatabase,
    project: ProjectEntity,
    uri: Uri,
    exporter: (List<PointEntity>, ProjectEntity, java.io.OutputStream) -> Unit,
): Int? = withContext(Dispatchers.IO) {
    val points = db.pointDao().getByProjectOnce(project.id)
    if (points.isEmpty()) return@withContext null
    val output = context.contentResolver.openOutputStream(uri, "w")
        ?: error("Não foi possível abrir o arquivo selecionado")
    output.use { exporter(points, project, it) }
    points.size
}
