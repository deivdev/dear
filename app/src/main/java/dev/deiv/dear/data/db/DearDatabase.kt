package dev.deiv.dear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.deiv.dear.data.model.Entry

@Database(
    entities = [Entry::class],
    version = 4,
    exportSchema = false,
)
abstract class DearDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var INSTANCE: DearDatabase? = null

        fun getInstance(context: Context): DearDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DearDatabase::class.java,
                    "dear.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
