package dev.deiv.dear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.deiv.dear.data.model.ChatMessage
import dev.deiv.dear.data.model.ChatMessageWithRefs
import dev.deiv.dear.data.model.ChatSession
import dev.deiv.dear.data.model.MessageEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSession>>

    @Query("UPDATE chat_sessions SET updatedAt = :at WHERE id = :id")
    suspend fun touchSession(id: Long, at: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<MessageEntry>)

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt, id")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageWithRefs>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isError = 0 ORDER BY createdAt, id")
    suspend fun getMessages(sessionId: Long): List<ChatMessage>
}
