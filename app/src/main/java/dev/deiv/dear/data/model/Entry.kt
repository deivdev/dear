package dev.deiv.dear.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntryType {
    TEXT, LINK, IMAGE, VIDEO, AUDIO, FILE
}

enum class CatalogStatus {
    PENDING, PROCESSING, DONE, FAILED
}

@Entity(
    tableName = "entries",
    indices = [Index("createdAt"), Index("status")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    val createdAt: Long = System.currentTimeMillis(),

    // What was captured
    val text: String? = null,
    val url: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val originalName: String? = null,
    val sourceApp: String? = null,
    /** Length of audio and video, known once cataloged. */
    val durationMs: Long? = null,

    // What the cataloger produced
    val title: String? = null,
    val summary: String? = null,
    val extractedText: String? = null,
    val status: CatalogStatus = CatalogStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val catalogedAt: Long? = null,
    val catalogModel: String? = null,
    /** Partial results of a long catalog run (frames, transcript chunks) so an interrupted run resumes. */
    val catalogState: String? = null,
)
