package dev.deiv.dear.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.llm.CatalogModel
import dev.deiv.dear.llm.DownloadProgress
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.llm.ModelStats
import dev.deiv.dear.llm.ModelStatsCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DearApp

    val modelState: StateFlow<ModelState> = app.modelManager.state

    private val _download = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val download: StateFlow<DownloadProgress> = _download.asStateFlow()

    private val _stats = MutableStateFlow<ModelStats?>(null)
    val stats: StateFlow<ModelStats?> = _stats.asStateFlow()

    private var downloadId: Long? = null
    private var observeJob: Job? = null

    fun refreshStats() {
        _stats.value = ModelStatsCollector.collect(getApplication(), app.modelManager)
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            app.modelManager.importModel(uri)
            refreshStats()
        }
    }

    fun startDownload(model: CatalogModel) {
        cancelDownload()
        val id = app.modelDownloader.start(model)
        downloadId = id
        observeJob = viewModelScope.launch {
            app.modelDownloader.observe(id).collect { progress ->
                _download.value = progress
                if (progress is DownloadProgress.Done) {
                    app.modelManager.loadModel()
                    refreshStats()
                }
            }
        }
    }

    fun cancelDownload() {
        observeJob?.cancel()
        observeJob = null
        downloadId?.let { app.modelDownloader.cancel(it) }
        downloadId = null
        _download.value = DownloadProgress.Idle
    }

    fun deleteCurrentModel() {
        viewModelScope.launch {
            app.modelManager.deleteCurrentModel()
            _stats.value = null
        }
    }
}
