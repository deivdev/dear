package dev.deiv.dear.data.repository

import dev.deiv.dear.data.db.DiaryDao
import dev.deiv.dear.data.model.DiaryEntry
import dev.deiv.dear.data.model.EntryType
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryDao) {

    val allEntries: Flow<List<DiaryEntry>> = dao.getAllEntries()

    suspend fun addEntry(entry: DiaryEntry): Long = dao.insert(entry)

    fun getByType(type: EntryType): Flow<List<DiaryEntry>> = dao.getEntriesByType(type)

    fun search(query: String): Flow<List<DiaryEntry>> = dao.searchEntries(query)

    fun getBetween(start: Long, end: Long): Flow<List<DiaryEntry>> =
        dao.getEntriesBetween(start, end)

    suspend fun getRecentEntries(limit: Int = 50): List<DiaryEntry> =
        dao.getRecentEntries(limit)
}
