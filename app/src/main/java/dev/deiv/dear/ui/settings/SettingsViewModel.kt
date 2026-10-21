package dev.deiv.dear.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.catalog.CatalogJob
import dev.deiv.dear.catalog.JobRecord
import dev.deiv.dear.llm.CatalogModel
import dev.deiv.dear.llm.DownloadProgress
import dev.deiv.dear.llm.ModelImport
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.llm.ModelStats
import dev.deiv.dear.llm.ModelStatsCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ArchiveStatus {
    data object Idle : ArchiveStatus()
    data object Exporting : ArchiveStatus()
    data object Importing : ArchiveStatus()
    data class Exported(val count: Int) : ArchiveStatus()
    data class Failed(val reason: String) : ArchiveStatus()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DearApp

    val modelState: StateFlow<ModelState> = app.modelManager.state

    private val _download = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val download: StateFlow<DownloadProgress> = _download.asStateFlow()

    private val _stats = MutableStateFlow<ModelStats?>(null)
    val stats: StateFlow<ModelStats?> = _stats.asStateFlow()

    // Catalog activity
    val currentJob: StateFlow<CatalogJob?> = app.catalogProcessor.current
    val history: StateFlow<List<JobRecord>> = app.catalogProcessor.history
    val pendingCount: StateFlow<Int> = app.catalogProcessor.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _modelImport = MutableStateFlow<ModelImport>(ModelImport.Idle)
    val modelImport: StateFlow<ModelImport> = _modelImport.asStateFlow()

    private val _archive = MutableStateFlow<ArchiveStatus>(ArchiveStatus.Idle)
    val archive: StateFlow<ArchiveStatus> = _archive.asStateFlow()

    private var downloadIds: List<Long> = emptyList()
    private var observeJob: Job? = null

    fun refreshStats() {
        _stats.value = ModelStatsCollector.collect(getApplication(), app.modelManager)
    }

    fun lastError(): String? = app.modelManager.lastError

    fun importModel(uri: Uri) {
        _modelImport.value = ModelImport.Running
        viewModelScope.launch {
            _modelImport.value = app.modelManager.importModel(uri)
            refreshStats()
        }
    }

    fun startDownload(model: CatalogModel) {
        cancelDownload()
        val ids = app.modelDownloader.start(model)
        downloadIds = ids
        observeJob = viewModelScope.launch {
            app.modelDownloader.observe(model, ids).collect { progress ->
                _download.value = progress
                if (progress is DownloadProgress.Done) refreshStats()
            }
        }
    }

    fun cancelDownload() {
        observeJob?.cancel()
        observeJob = null
        app.modelDownloader.cancel(downloadIds)
        downloadIds = emptyList()
        _download.value = DownloadProgress.Idle
    }

    fun exportArchive(uri: Uri) {
        _archive.value = ArchiveStatus.Exporting
        viewModelScope.launch {
            _archive.value = runCatching { app.archive.export(uri) }
                .fold(onSuccess = { ArchiveStatus.Exported(it) }, onFailure = { ArchiveStatus.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    /** On success the app restarts, so only a failure ever comes back here. */
    fun importArchive(uri: Uri) {
        _archive.value = ArchiveStatus.Importing
        viewModelScope.launch {
            runCatching { app.archive.import(uri) }
                .onFailure { _archive.value = ArchiveStatus.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun deleteCurrentModel() {
        viewModelScope.launch {
            app.modelManager.deleteCurrentModel()
            refreshStats()
        }
    }
}
