package dev.deiv.dear.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.model.EntryWithTags
import dev.deiv.dear.data.repository.TextMatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TypeFilter(val type: EntryType?) {
    ALL(null), IMAGE(EntryType.IMAGE), LINK(EntryType.LINK), TEXT(EntryType.TEXT),
    VIDEO(EntryType.VIDEO), AUDIO(EntryType.AUDIO), FILE(EntryType.FILE),
}

data class CollectionState(
    val items: List<EntryWithTags> = emptyList(),
    val query: String = "",
    val filter: TypeFilter = TypeFilter.ALL,
    /** Types that actually occur, so filter chips are only shown for those. */
    val availableTypes: Set<EntryType> = emptySet(),
    val totalCount: Int = 0,
    val loaded: Boolean = false,
)

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DearApp

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TypeFilter.ALL)

    val state: StateFlow<CollectionState> = combine(app.entryRepository.all, query, filter) { all, q, f ->
        val words = TextMatching.normalize(q).split(WHITESPACE).filter { it.isNotEmpty() }
        CollectionState(
            items = all.filter { (f.type == null || it.entry.type == f.type) && it.matches(words) },
            query = q,
            filter = f,
            availableTypes = all.mapTo(mutableSetOf()) { it.entry.type },
            totalCount = all.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionState())

    val pendingCount: StateFlow<Int> = app.catalogProcessor.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: TypeFilter) {
        filter.value = value
    }

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { app.entryRepository.addText(trimmed) }
    }

    /** Plain substring search over everything we know about an entry; FTS and embeddings come later. */
    private fun EntryWithTags.matches(words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val haystack = buildString {
            listOf(entry.title, entry.summary, entry.text, entry.url, entry.extractedText, entry.originalName)
                .forEach { it?.let { s -> append(TextMatching.normalize(s)).append(' ') } }
            tags.forEach { append(TextMatching.normalize(it.name)).append(' ') }
        }
        return words.all { it in haystack }
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")
    }
}
