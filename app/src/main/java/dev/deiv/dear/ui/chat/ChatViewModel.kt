package dev.deiv.dear.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.db.DearDatabase
import dev.deiv.dear.data.repository.DiaryRepository
import dev.deiv.dear.llm.DiaryAssistant
import dev.deiv.dear.llm.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DearApp
    private val repository = DiaryRepository(
        DearDatabase.getInstance(application).diaryDao()
    )
    private val assistant: DiaryAssistant
        get() = DiaryAssistant(model = app.modelManager.getModel(), repository = repository)

    val modelState: StateFlow<ModelState> = app.modelManager.state

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            app.modelManager.importModel(uri)
        }
    }

    fun send(text: String) {
        _messages.value = _messages.value + ChatMessage(text, isUser = true)

        viewModelScope.launch {
            val response = StringBuilder()
            assistant.ask(text).collect { token ->
                response.append(token)
                // Update the last assistant message with streaming tokens
                val current = _messages.value.toMutableList()
                val lastIsAssistant = current.lastOrNull()?.isUser == false
                if (lastIsAssistant) {
                    current[current.lastIndex] = ChatMessage(response.toString(), isUser = false)
                } else {
                    current.add(ChatMessage(response.toString(), isUser = false))
                }
                _messages.value = current
            }
        }
    }
}
