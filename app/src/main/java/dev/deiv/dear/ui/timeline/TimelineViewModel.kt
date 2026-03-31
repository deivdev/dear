package dev.deiv.dear.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.deiv.dear.data.db.DearDatabase
import dev.deiv.dear.data.model.DiaryEntry
import dev.deiv.dear.data.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow

class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository(
        DearDatabase.getInstance(application).diaryDao()
    )

    val entries: Flow<List<DiaryEntry>> = repository.allEntries
}
