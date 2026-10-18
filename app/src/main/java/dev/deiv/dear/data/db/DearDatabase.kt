package dev.deiv.dear.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.deiv.dear.data.model.ChatMessage
import dev.deiv.dear.data.model.ChatSession
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryTag
import dev.deiv.dear.data.model.MessageEntry
import dev.deiv.dear.data.model.Tag

/**
 * Schemas are exported to app/schemas. From version 5 on every schema change needs a migration
 * (an [AutoMigration] when Room can derive it): there is no destructive fallback, so a missing one
 * fails loudly instead of wiping the user's captures.
 */
@Database(
    entities = [
        Entry::class,
        Tag::class,
        EntryTag::class,
        ChatSession::class,
        ChatMessage::class,
        MessageEntry::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6), // chat sessions, messages and their entry citations
        AutoMigration(from = 6, to = 7), // entries.durationMs, entries.catalogState
        AutoMigration(from = 7, to = 8), // entries.parentId: photos pulled from a shared link
    ],
)
abstract class DearDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: DearDatabase? = null

        fun getInstance(context: Context): DearDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DearDatabase::class.java,
                    "dear.db",
                )
                    // Versions 1-4 predate the exported schema; nothing worth migrating.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
