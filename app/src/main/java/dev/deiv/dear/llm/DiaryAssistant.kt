package dev.deiv.dear.llm

import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.repository.EntryRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryAssistant(
    private val model: LlamaModel?,
    private val repository: EntryRepository,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend fun ask(question: String): Flow<String> {
        val recent = repository.getRecent(30)
        val context = buildContext(recent)
        val prompt = buildPrompt(context, question)

        return model?.generate(prompt)
            ?: kotlinx.coroutines.flow.flow {
                emit("Model not loaded. Place a GGUF file in the app's model directory.")
            }
    }

    private fun buildContext(items: List<Entry>): String {
        return items.joinToString("\n") { item ->
            val time = dateFormat.format(Date(item.createdAt))
            val source = item.sourceApp?.let { " from $it" } ?: ""
            when (item.type) {
                EntryType.TEXT -> "[$time] Note$source: ${item.textContent}"
                EntryType.LINK -> "[$time] Link$source: ${item.textContent}"
                EntryType.IMAGE -> "[$time] Image$source${item.originalName?.let { " ($it)" } ?: ""}"
                EntryType.VIDEO -> "[$time] Video$source${item.originalName?.let { " ($it)" } ?: ""}"
                EntryType.AUDIO -> "[$time] Audio$source${item.originalName?.let { " ($it)" } ?: ""}"
                EntryType.FILE -> "[$time] File$source${item.originalName?.let { " ($it)" } ?: ""}"
            }
        }
    }

    private fun buildPrompt(context: String, question: String): String {
        return """<|system|>
You are Dear, a personal diary AI assistant. You help the user remember and reflect on their experiences based on their captured entries. Be warm, concise, and helpful.

Here are the user's recent entries:
$context<|end|>
<|user|>
$question<|end|>
<|assistant|>
"""
    }
}
