package dev.deiv.dear.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryTag
import dev.deiv.dear.data.model.EntryWithTags
import dev.deiv.dear.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Insert
    suspend fun insertAll(entries: List<Entry>): List<Long>

    @Delete
    suspend fun delete(entry: Entry)

    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryWithTags>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    suspend fun getAllWithTags(): List<EntryWithTags>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryWithTags?>

    @Query("SELECT * FROM entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Entry>

    /** Photos pulled from link [parentId], in the order they had there. */
    @Query("SELECT * FROM entries WHERE parentId = :parentId ORDER BY id")
    suspend fun getChildren(parentId: Long): List<Entry>

    @Query("SELECT * FROM entries WHERE parentId = :parentId ORDER BY id")
    fun observeChildren(parentId: Long): Flow<List<Entry>>

    // --- Catalog lifecycle ---

    /** In the order they were saved, a post's photos right after their link. */
    @Query(
        "SELECT id FROM entries WHERE status = 'PENDING' " +
            "OR (status = 'FAILED' AND attempts < :maxAttempts) ORDER BY COALESCE(parentId, id), id"
    )
    suspend fun getCatalogable(maxAttempts: Int): List<Long>

    @Query(
        "SELECT COUNT(*) FROM entries WHERE status IN ('PENDING', 'PROCESSING') " +
            "OR (status = 'FAILED' AND attempts < :maxAttempts)"
    )
    fun observeCatalogableCount(maxAttempts: Int): Flow<Int>

    @Query("UPDATE entries SET status = 'PROCESSING', attempts = attempts + 1 WHERE id = :id")
    suspend fun markProcessing(id: Long)

    @Query("UPDATE entries SET status = 'FAILED', lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    /** User asked to catalog again: a fresh start, attempts included. */
    @Query("UPDATE entries SET status = 'PENDING', attempts = 0, lastError = NULL, catalogState = NULL WHERE id = :id")
    suspend fun resetCatalog(id: Long)

    @Query("UPDATE entries SET catalogState = :state WHERE id = :id")
    suspend fun updateCatalogState(id: Long, state: String?)

    @Query("UPDATE entries SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    /** Back to the queue without counting the attempt, e.g. when the model could not run. */
    @Query("UPDATE entries SET status = 'PENDING', attempts = MAX(attempts - 1, 0) WHERE id = :id")
    suspend fun requeue(id: Long)

    /** Entries left PROCESSING by a killed process go back to the queue. */
    @Query("UPDATE entries SET status = 'PENDING' WHERE status = 'PROCESSING'")
    suspend fun resetInterrupted(): Int

    @Query(
        "UPDATE entries SET status = 'DONE', title = :title, summary = :summary, " +
            "extractedText = :extractedText, lastError = NULL, catalogedAt = :catalogedAt, " +
            "catalogModel = :model, catalogState = NULL WHERE id = :id"
    )
    suspend fun markDone(
        id: Long,
        title: String?,
        summary: String?,
        extractedText: String?,
        catalogedAt: Long,
        model: String?,
    )

    // --- Tags ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun getTagId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTag(entryTag: EntryTag)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun clearTags(entryId: Long)

    @Transaction
    suspend fun saveCatalog(
        id: Long,
        title: String?,
        summary: String?,
        extractedText: String?,
        tags: List<String>,
        model: String?,
    ) {
        markDone(id, title, summary, extractedText, System.currentTimeMillis(), model)
        clearTags(id)
        for (name in tags) {
            val tagId = insertTag(Tag(name = name)).takeIf { it != -1L }
                ?: getTagId(name)
                ?: continue
            insertEntryTag(EntryTag(entryId = id, tagId = tagId))
        }
    }
}
