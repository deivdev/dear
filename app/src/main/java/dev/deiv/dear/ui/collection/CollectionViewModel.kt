package dev.deiv.dear.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.model.Entry
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
    /** Photos pulled from each link, by link id, in the post's order. */
    val photos: Map<Long, List<Entry>> = emptyMap(),
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
        // Photos pulled from a post belong to its link: shown and searched as part of it.
        val photos = all.mapNotNull { p -> p.entry.parentId?.let { it to p } }.groupBy({ it.first }, { it.second })
        val entries = all.filter { it.entry.parentId == null }
        CollectionState(
            items = entries.filter { item ->
                (f.type == null || item.entry.type == f.type) &&
                    matches(listOf(item) + photos[item.entry.id].orEmpty(), words)
            },
            photos = photos.mapValues { (_, list) -> list.map { it.entry }.sortedBy { it.id } },
            query = q,
            filter = f,
            availableTypes = entries.mapTo(mutableSetOf()) { it.entry.type },
            totalCount = entries.size,
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

    /** Plain substring search over everything we know about the entries; FTS and embeddings come later. */
    private fun matches(items: List<EntryWithTags>, words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val haystack = buildString {
            for (item in items) {
                val e = item.entry
                listOf(e.title, e.summary, e.text, e.url, e.extractedText, e.originalName)
                    .forEach { it?.let { s -> append(TextMatching.normalize(s)).append(' ') } }
                item.tags.forEach { append(TextMatching.normalize(it.name)).append(' ') }
            }
        }
        return words.all { it in haystack }
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")
    }
}
