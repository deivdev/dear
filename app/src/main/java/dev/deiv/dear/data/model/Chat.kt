package dev.deiv.dear.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/** One conversation in "Chiedi". */
@Entity(
    tableName = "chat_sessions",
    indices = [Index("updatedAt")],
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class ChatRole {
    USER, ASSISTANT
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** An archive entry cited by an assistant message; deleting the entry drops the citation. */
@Entity(
    tableName = "message_entries",
    primaryKeys = ["messageId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class MessageEntry(
    val messageId: Long,
    val entryId: Long,
)

data class ChatMessageWithRefs(
    @Embedded val message: ChatMessage,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(MessageEntry::class, parentColumn = "messageId", entityColumn = "entryId"),
    )
    val refs: List<Entry>,
)
