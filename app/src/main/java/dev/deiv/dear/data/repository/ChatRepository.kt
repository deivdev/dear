package dev.deiv.dear.data.repository

import dev.deiv.dear.data.db.ChatDao
import dev.deiv.dear.data.model.ChatMessage
import dev.deiv.dear.data.model.ChatMessageWithRefs
import dev.deiv.dear.data.model.ChatRole
import dev.deiv.dear.data.model.ChatSession
import dev.deiv.dear.data.model.MessageEntry
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {

    val sessions: Flow<List<ChatSession>> = dao.observeSessions()

    fun messages(sessionId: Long): Flow<List<ChatMessageWithRefs>> = dao.observeMessages(sessionId)

    /** Previous turns of a session, oldest first, without failed replies. */
    suspend fun history(sessionId: Long): List<ChatMessage> = dao.getMessages(sessionId)

    suspend fun createSession(firstQuestion: String): Long =
        dao.insertSession(ChatSession(title = firstQuestion.trim().take(TITLE_LENGTH)))

    suspend fun addUserMessage(sessionId: Long, text: String) {
        dao.insertMessage(ChatMessage(sessionId = sessionId, role = ChatRole.USER, text = text))
        dao.touchSession(sessionId, System.currentTimeMillis())
    }

    suspend fun addReply(sessionId: Long, text: String, refs: List<Long>, isError: Boolean = false) {
        val id = dao.insertMessage(
            ChatMessage(sessionId = sessionId, role = ChatRole.ASSISTANT, text = text, isError = isError)
        )
        if (refs.isNotEmpty()) dao.insertRefs(refs.map { MessageEntry(messageId = id, entryId = it) })
        dao.touchSession(sessionId, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    companion object {
        private const val TITLE_LENGTH = 80
    }
}
