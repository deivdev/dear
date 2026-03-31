package dev.deiv.dear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.deiv.dear.data.model.DiaryEntry
import dev.deiv.dear.data.model.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE type = :type ORDER BY timestamp DESC")
    fun getEntriesByType(type: EntryType): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getEntriesBetween(start: Long, end: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchEntries(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<DiaryEntry>
}
