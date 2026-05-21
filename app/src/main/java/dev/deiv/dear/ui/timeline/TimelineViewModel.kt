package dev.deiv.dear.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.model.Entry
import kotlinx.coroutines.flow.Flow

class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DearApp).entryRepository

    val entries: Flow<List<Entry>> = repository.all
}
