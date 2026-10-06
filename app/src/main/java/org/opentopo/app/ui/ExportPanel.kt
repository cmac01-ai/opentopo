package org.opentopo.app.ui

import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
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
import java.io.File

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
                                scope.launch {
                                    isExporting = true
                                    exportStatus = null
                                    val file = doExport(context, db, proj, "csv") { pts, out ->
                                        CsvExporter.export(pts, out)
                                    }
                                    isExporting = false
                                    if (file != null) {
                                        shareFile(context, file, "text/csv")
                                        exportStatus = "CSV shared"
                                    } else {
                                        exportStatus = "No points"
                                    }
                                }
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
                            Text("Export CSV")
                        }

                        /* -- GeoJSON -- */
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatus = null
                                    val file = doExport(context, db, proj, "geojson") { pts, out ->
                                        GeoJsonExporter.export(pts, proj.name, out)
                                    }
                                    isExporting = false
                                    if (file != null) {
                                        shareFile(context, file, "application/geo+json")
                                        exportStatus = "GeoJSON shared"
                                    } else {
                                        exportStatus = "No points"
                                    }
                                }
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
                            Text("Export GeoJSON")
                        }

                        /* -- DXF -- */
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatus = null
                                    val file = doExport(context, db, proj, "dxf") { pts, out ->
                                        DxfExporter.export(pts, out)
                                    }
                                    isExporting = false
                                    if (file != null) {
                                        shareFile(context, file, "application/dxf")
                                        exportStatus = "DXF shared"
                                    } else {
                                        exportStatus = "No points"
                                    }
                                }
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
                            Text("Export DXF")
                        }

                        /* -- Shapefile ZIP -- */
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatus = null
                                    val file = doExport(context, db, proj, "zip") { pts, out ->
                                        ShapefileExporter.export(pts, proj, out)
                                    }
                                    isExporting = false
                                    if (file != null) {
                                        shareFile(context, file, "application/zip")
                                        exportStatus = "Shapefile shared"
                                    } else {
                                        exportStatus = "No points"
                                    }
                                }
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
                            Text("Export Shapefile (.zip)")
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

private suspend fun doExport(
    context: Context, db: AppDatabase, project: ProjectEntity, ext: String,
    exporter: (List<PointEntity>, java.io.OutputStream) -> Unit,
): File? = withContext(Dispatchers.IO) {
    val points = db.pointDao().getByProjectOnce(project.id)
    if (points.isEmpty()) return@withContext null
    val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val file = File(dir, "${project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.$ext")
    file.outputStream().use { exporter(points, it) }
    file
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share survey data"))
}
