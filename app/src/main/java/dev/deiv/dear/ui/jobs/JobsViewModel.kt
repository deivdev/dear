package dev.deiv.dear.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.deiv.dear.DearApp
import dev.deiv.dear.llm.CatalogJob
import dev.deiv.dear.llm.JobRecord
import dev.deiv.dear.llm.ModelState
import kotlinx.coroutines.flow.StateFlow

class JobsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DearApp

    val current: StateFlow<CatalogJob?> = app.catalogQueue.current
    val pendingCount: StateFlow<Int> = app.catalogQueue.pendingCount
    val history: StateFlow<List<JobRecord>> = app.catalogQueue.history
    val modelState: StateFlow<ModelState> = app.modelManager.state
}
