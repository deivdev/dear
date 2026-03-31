package dev.deiv.dear.llm

import dev.deiv.dear.data.model.DiaryEntry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryAssistant(
    private val model: LlamaModel?,
    private val repository: DiaryRepository,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend fun ask(question: String): Flow<String> {
        val recentEntries = repository.getRecentEntries(30)
        val context = buildContext(recentEntries)
        val prompt = buildPrompt(context, question)

        return model?.generate(prompt)
            ?: kotlinx.coroutines.flow.flow {
                emit("Model not loaded. Place a GGUF file in the app's model directory.")
            }
    }

    private fun buildContext(entries: List<DiaryEntry>): String {
        return entries.joinToString("\n") { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            when (entry.type) {
                EntryType.NOTE -> "[$time] Note: ${entry.text}"
                EntryType.PHOTO -> "[$time] Photo taken${entry.text?.let { ": $it" } ?: ""}" +
                    locationSuffix(entry)
                EntryType.VIDEO -> "[$time] Video recorded${entry.text?.let { ": $it" } ?: ""}" +
                    locationSuffix(entry)
                EntryType.LOCATION -> "[$time] Location: ${entry.address ?: "${entry.latitude}, ${entry.longitude}"}"
            }
        }
    }

    private fun locationSuffix(entry: DiaryEntry): String {
        if (entry.latitude == null) return ""
        return " at ${entry.address ?: "${entry.latitude}, ${entry.longitude}"}"
    }

    private fun buildPrompt(context: String, question: String): String {
        // Phi-4-mini uses ChatML format
        return """<|system|>
You are Dear, a personal diary AI assistant. You help the user remember and reflect on their experiences based on their diary entries. Be warm, concise, and helpful.

Here are the user's recent diary entries:
$context<|end|>
<|user|>
$question<|end|>
<|assistant|>
"""
    }
}
