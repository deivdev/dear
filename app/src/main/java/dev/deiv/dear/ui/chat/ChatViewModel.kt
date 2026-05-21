package dev.deiv.dear.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.llm.DiaryAssistant
import dev.deiv.dear.llm.ModelState
import dev.deiv.dear.llm.ModelStats
import dev.deiv.dear.llm.ModelStatsCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DearApp
    private val assistant: DiaryAssistant
        get() = DiaryAssistant(model = app.modelManager.getModel(), repository = app.entryRepository)

    val modelState: StateFlow<ModelState> = app.modelManager.state

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _stats = MutableStateFlow<ModelStats?>(null)
    val stats: StateFlow<ModelStats?> = _stats.asStateFlow()

    fun refreshStats() {
        _stats.value = ModelStatsCollector.collect(getApplication(), app.modelManager)
    }

    fun send(text: String) {
        _messages.value = _messages.value + ChatMessage(text, isUser = true)

        viewModelScope.launch {
            val response = StringBuilder()
            assistant.ask(text).collect { token ->
                response.append(token)
                val current = _messages.value.toMutableList()
                val lastIsAssistant = current.lastOrNull()?.isUser == false
                if (lastIsAssistant) {
                    current[current.lastIndex] = ChatMessage(response.toString(), isUser = false)
                } else {
                    current.add(ChatMessage(response.toString(), isUser = false))
                }
                _messages.value = current
            }
            refreshStats()
        }
    }
}
