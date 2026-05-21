package dev.deiv.dear.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.llm.CatalogModel
import dev.deiv.dear.llm.DownloadProgress
import dev.deiv.dear.llm.ModelCatalog
import dev.deiv.dear.llm.ModelStats
import dev.deiv.dear.llm.ModelState

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val modelState by viewModel.modelState.collectAsState()
    val downloadState by viewModel.download.collectAsState()
    val stats by viewModel.stats.collectAsState()

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModel(it) } }

    LaunchedEffect(modelState) {
        if (modelState == ModelState.READY) viewModel.refreshStats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Model", style = MaterialTheme.typography.titleMedium)

        when (modelState) {
            ModelState.READY -> {
                stats?.let {
                    StatsCard(
                        stats = it,
                        onRefresh = { viewModel.refreshStats() },
                        onDelete = { viewModel.deleteCurrentModel() },
                    )
                }
            }
            ModelState.LOADING -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Loading model…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            ModelState.ERROR -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text("Failed to load model.", modifier = Modifier.padding(12.dp))
                }
            }
            ModelState.NOT_FOUND -> {
                Text(
                    "No model installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DownloadPanel(
            downloadState = downloadState,
            onDownload = { viewModel.startDownload(it) },
            onCancel = { viewModel.cancelDownload() },
            onPickLocal = { modelPicker.launch(arrayOf("*/*")) },
        )
    }
}

@Composable
private fun StatsCard(stats: ModelStats, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("● Ready", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(stats.modelName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Size ${stats.modelSizeMB} MB · Process RAM ${stats.processRamMB} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Device ${stats.deviceAvailRamMB}/${stats.deviceTotalRamMB} MB free${if (stats.lowMemory) " · LOW" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.lastTokenCount > 0) {
                Text(
                    "Last gen: %d tok · %d ms · %.1f tok/s".format(
                        stats.lastTokenCount, stats.lastDurationMs, stats.tokensPerSec,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                OutlinedButton(onClick = onDelete) { Text("Delete model") }
            }
        }
    }
}

@Composable
private fun DownloadPanel(
    downloadState: DownloadProgress,
    onDownload: (CatalogModel) -> Unit,
    onCancel: () -> Unit,
    onPickLocal: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Install a model", style = MaterialTheme.typography.titleSmall)
            Text(
                "Download a curated GGUF, or import one you already have.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val s = downloadState) {
                is DownloadProgress.Running -> {
                    val pct = if (s.totalBytes > 0) s.bytesDownloaded.toFloat() / s.totalBytes else 0f
                    Text(
                        "Downloading: %d / %d MB".format(
                            s.bytesDownloaded / 1_000_000, s.totalBytes / 1_000_000,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                is DownloadProgress.Failed -> {
                    Text(
                        "Download failed: ${s.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ModelList(onDownload)
                }
                else -> ModelList(onDownload)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onPickLocal, modifier = Modifier.fillMaxWidth()) {
                Text("Import local GGUF")
            }
        }
    }
}

@Composable
private fun ModelList(onDownload: (CatalogModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelCatalog.models.forEach { m ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${m.sizeMB} MB · RAM ${m.ramHint} · ${m.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onDownload(m) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download") }
                }
            }
        }
    }
}
