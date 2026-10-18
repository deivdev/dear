package dev.deiv.dear.ui.entry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.model.EntryWithTags
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EntryUiState {
    data object Loading : EntryUiState
    data object Missing : EntryUiState
    data class Loaded(val item: EntryWithTags) : EntryUiState
}

class EntryViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as DearApp
    private val id: Long = checkNotNull(savedState[ARG_ID]) { "entry id missing" }

    val state: StateFlow<EntryUiState> = app.entryRepository.observe(id)
        .map { it?.let(EntryUiState::Loaded) ?: EntryUiState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState.Loading)

    fun recatalog() {
        viewModelScope.launch { app.entryRepository.recatalog(id) }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = (state.value as? EntryUiState.Loaded)?.item?.entry ?: return
        viewModelScope.launch {
            app.entryRepository.delete(current)
            onDeleted()
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
