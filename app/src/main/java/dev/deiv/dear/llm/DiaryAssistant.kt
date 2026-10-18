package dev.deiv.dear.llm

import dev.deiv.dear.data.model.ChatMessage
import dev.deiv.dear.data.model.ChatRole
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.model.EntryWithTags
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Answers questions about the archive. The entries passed in are listed to the model with their
 * ids and the model cites the ones it talks about as [#id], which the UI turns into links.
 */
class DiaryAssistant(private val modelManager: ModelManager) {

    fun ask(question: String, history: List<ChatMessage>, entries: List<EntryWithTags>): Flow<String> =
        modelManager.stream(
            GenerationRequest(
                turns = buildList {
                    add(ChatTurn(ChatTurn.Role.SYSTEM, systemPrompt(entries)))
                    history.takeLast(MAX_HISTORY).forEach { msg ->
                        val role = if (msg.role == ChatRole.USER) ChatTurn.Role.USER else ChatTurn.Role.ASSISTANT
                        add(ChatTurn(role, msg.text))
                    }
                    add(ChatTurn(ChatTurn.Role.USER, question))
                },
                maxTokens = 512,
                temperature = 0.6f,
            )
        )

    private fun systemPrompt(entries: List<EntryWithTags>): String = buildString {
        append("You are Dear, the assistant of the user's personal archive: notes, links, photos and files ")
        append("they saved on their phone. Now is ").append(LocalDateTime.now().format(NOW_FORMAT)).append(".\n")
        append("Answer using only the archive entries listed below and never invent entries. ")
        append("Reply in the language of the user's question, warmly and briefly.\n")
        append("Whenever you mention an entry, cite it right after with its id in exactly this form: [#12]. ")
        append("Cite only ids from the list. If nothing listed answers the question, say so plainly.\n\n")
        if (entries.isEmpty()) {
            append("The archive is empty.")
        } else {
            append("Archive entries, most relevant first:\n")
            entries.forEach { append(describe(it)).append('\n') }
        }
    }

    private fun describe(item: EntryWithTags): String = buildString {
        val e = item.entry
        append("[#").append(e.id).append("] ")
        append(ENTRY_DATE.format(Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault())))
        append(" · ").append(e.type.name.lowercase())
        when (e.type) {
            EntryType.TEXT -> e.text?.let { append(" · ").append(it.take(NOTE_CHARS)) }
            EntryType.LINK -> {
                e.url?.let { append(" · ").append(it) }
                e.title?.let { append(" · ").append(it) }
            }
            else -> (e.title ?: e.originalName)?.let { append(" · ").append(it) }
        }
        e.summary?.let { append(" — ").append(it) }
        if (item.tags.isNotEmpty()) append(" (tags: ").append(item.tags.joinToString { it.name }).append(')')
    }

    companion object {
        private const val MAX_HISTORY = 6
        private const val NOTE_CHARS = 300
        private val NOW_FORMAT = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
        private val ENTRY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

        // [#12] and the occasional [#12, #15]
        private val CITATION = Regex("""\[#\d+(?:\s*,\s*#?\d+)*]""")
        private val NUMBER = Regex("""\d+""")
        private val DANGLING = Regex("""\s*\[#?[\d,\s#]*$""")
        private val SPACE_BEFORE_PUNCT = Regex("""[ \t]+(?=[.,;:!?])""")

        /** Entry ids cited in [text], in order of first appearance. */
        fun citedIds(text: String): List<Long> =
            CITATION.findAll(text)
                .flatMap { m -> NUMBER.findAll(m.value).map { it.value.toLong() } }
                .distinct()
                .toList()

        /** [text] without citation markers, including a half-written one at the end while streaming. */
        fun stripCitations(text: String): String =
            text.replace(CITATION, "")
                .replace(DANGLING, "")
                .replace(SPACE_BEFORE_PUNCT, "")
                .trim()
    }
}
