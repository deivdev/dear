package dev.deiv.dear.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(item: Entry): Long

    @Delete
    suspend fun delete(item: Entry)

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: EntryType): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE textContent LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Query("SELECT * FROM entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Entry>

    @Query("UPDATE entries SET tags = :tags, summary = :summary WHERE id = :id")
    suspend fun updateCatalog(id: Long, tags: String?, summary: String?)

    @Query("SELECT * FROM entries WHERE (tags IS NULL OR tags = '') AND (summary IS NULL OR summary = '') ORDER BY createdAt DESC")
    suspend fun getUncataloged(): List<Entry>
}
