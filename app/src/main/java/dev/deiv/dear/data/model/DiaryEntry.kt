package dev.deiv.dear.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType {
    NOTE, PHOTO, VIDEO, LOCATION
}

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String? = null,
    val mediaPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
)
