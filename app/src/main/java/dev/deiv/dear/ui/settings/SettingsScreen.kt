package dev.deiv.dear.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.deiv.dear.R
import dev.deiv.dear.catalog.JobRecord
import dev.deiv.dear.catalog.JobStatus
import dev.deiv.dear.llm.CatalogModel
import dev.deiv.dear.llm.CatalogStep
import dev.deiv.dear.llm.DownloadProgress
import dev.deiv.dear.llm.ModelCatalog
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.llm.ModelStats
import dev.deiv.dear.ui.common.Dot
import dev.deiv.dear.ui.common.MonoLabel
import dev.deiv.dear.ui.common.PillButton
import dev.deiv.dear.ui.common.WorkingDots
import dev.deiv.dear.ui.common.formatTime
import dev.deiv.dear.ui.common.typeLabel
import dev.deiv.dear.ui.theme.NothingRed

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val modelState by viewModel.modelState.collectAsState()
    val download by viewModel.download.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val current by viewModel.currentJob.collectAsState()
    val pending by viewModel.pendingCount.collectAsState()
    val history by viewModel.history.collectAsState()

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    LaunchedEffect(modelState) {
        if (modelState != ModelState.NOT_INSTALLED) viewModel.refreshStats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = stringResource(R.string.action_settings).uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.displaySmall,
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Section(stringResource(R.string.section_model)) {
                when (modelState) {
                    ModelState.READY, ModelState.IDLE, ModelState.LOADING -> stats?.let {
                        ModelCard(
                            stats = it,
                            state = modelState,
                            onDelete = viewModel::deleteCurrentModel,
                        )
                    }
                    ModelState.ERROR -> Panel {
                        MonoLabel(stringResource(R.string.model_error), color = NothingRed)
                        viewModel.lastError()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        PillButton(stringResource(R.string.model_delete), onClick = viewModel::deleteCurrentModel, outlined = true)
                    }
                    ModelState.NOT_INSTALLED -> Unit
                }
                InstallPanel(
                    download = download,
                    onDownload = viewModel::startDownload,
                    onCancel = viewModel::cancelDownload,
                    onImport = { modelPicker.launch(arrayOf("*/*")) },
                )
            }

            Section(stringResource(R.string.section_catalog_activity)) {
                Panel {
                    val job = current
                    if (job != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            WorkingDots()
                            MonoLabel(
                                stringResource(R.string.catalog_running, job.entryId) +
                                    (job.type?.let { " · " + typeLabel(it) } ?: "") +
                                    (job.step?.let { " · " + stepLabel(it) } ?: ""),
                            )
                        }
                    } else if (pending == 0) {
                        MonoLabel(stringResource(R.string.catalog_idle))
                    }
                    if (pending > 0) {
                        Text(
                            pluralStringResource(R.plurals.catalog_pending, pending, pending),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (history.isEmpty()) {
                    MonoLabel(stringResource(R.string.catalog_history_empty), modifier = Modifier.padding(start = 4.dp))
                } else {
                    Panel { history.forEach { JobRow(it) } }
                }
            }

            Section(stringResource(R.string.section_about)) {
                Panel {
                    Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                    MonoLabel(stringResource(R.string.about_fonts))
                }
            }
        }
    }
}

@Composable
private fun stepLabel(step: CatalogStep): String = when (step) {
    is CatalogStep.Frame -> stringResource(R.string.step_frame, step.index, step.total)
    is CatalogStep.Audio -> stringResource(R.string.step_audio, step.index, step.total)
    CatalogStep.Summary -> stringResource(R.string.step_summary)
}

@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonoLabel(label, modifier = Modifier.padding(start = 4.dp))
        content()
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ModelCard(stats: ModelStats, state: ModelState, onDelete: () -> Unit) {
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                ModelState.LOADING -> WorkingDots(dotSize = 5.dp)
                ModelState.READY -> Dot(NothingRed)
                else -> Dot(MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MonoLabel(
                stringResource(
                    when (state) {
                        ModelState.LOADING -> R.string.model_loading
                        ModelState.READY -> R.string.model_loaded
                        else -> R.string.model_idle
                    }
                )
            )
        }
        Text(stats.modelName.removeSuffix(".gguf"), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (stats.multimodal) R.string.model_multimodal else R.string.model_text_only),
            style = MaterialTheme.typography.bodyMedium,
        )
        MonoLabel(stringResource(R.string.model_size, stats.modelSizeMB.toInt(), stats.processRamMB.toInt()))
        MonoLabel(stringResource(R.string.model_device_ram, stats.deviceAvailRamMB.toInt(), stats.deviceTotalRamMB.toInt()))
        if (stats.lastTokenCount > 0) {
            MonoLabel(stringResource(R.string.model_last_gen, stats.lastTokenCount, stats.tokensPerSec))
        }
        PillButton(
            text = stringResource(R.string.model_delete),
            onClick = onDelete,
            outlined = true,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun InstallPanel(
    download: DownloadProgress,
    onDownload: (CatalogModel) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
) {
    Panel {
        MonoLabel(stringResource(R.string.install_model))
        Text(
            stringResource(R.string.install_model_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (download) {
            is DownloadProgress.Running -> {
                MonoLabel(
                    stringResource(
                        R.string.downloading,
                        (download.bytesDownloaded / 1_000_000).toInt(),
                        (download.totalBytes / 1_000_000).toInt(),
                    )
                )
                val fraction = if (download.totalBytes > 0) download.bytesDownloaded.toFloat() / download.totalBytes else 0f
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = NothingRed,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                PillButton(stringResource(R.string.action_cancel), onClick = onCancel, outlined = true)
            }
            else -> {
                if (download is DownloadProgress.Failed) {
                    MonoLabel(stringResource(R.string.download_failed, download.reason), color = NothingRed)
                }
                ModelCatalog.models.forEach { model -> CatalogRow(model, onDownload) }
            }
        }
        Spacer(modifier = Modifier.padding(top = 4.dp))
        PillButton(stringResource(R.string.import_gguf), onClick = onImport, outlined = true)
    }
}

@Composable
private fun CatalogRow(model: CatalogModel, onDownload: (CatalogModel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleSmall)
            MonoLabel(stringResource(R.string.model_ram_hint, model.totalSizeMB, model.ramHint))
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PillButton(stringResource(R.string.download), onClick = { onDownload(model) })
    }
}

@Composable
private fun JobRow(rec: JobRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Dot(
            color = when (rec.status) {
                JobStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                JobStatus.FAILED -> NothingRed
                JobStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 5.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel(
                "#${rec.entryId} · ${rec.type?.let { typeLabel(it) } ?: "—"} · ${formatTime(rec.startedAt)} · ${rec.durationMs / 1000}s",
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (rec.message.isNotBlank()) {
                Text(
                    rec.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
