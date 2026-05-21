package dev.deiv.dear.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType {
    TEXT, LINK, IMAGE, VIDEO, AUDIO, FILE
}

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    val createdAt: Long = System.currentTimeMillis(),
    val textContent: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val sourceApp: String? = null,
    val originalName: String? = null,
    val tags: String? = null,
    val summary: String? = null,
)
