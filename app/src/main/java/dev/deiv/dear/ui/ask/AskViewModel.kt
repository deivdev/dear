package dev.deiv.dear.ui.ask

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.model.ChatMessageWithRefs
import dev.deiv.dear.data.model.ChatRole
import dev.deiv.dear.data.model.ChatSession
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.llm.DiaryAssistant
import dev.deiv.dear.llm.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AskMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val refs: List<Entry> = emptyList(),
)

/** The reply being generated, not yet saved. */
data class StreamingReply(
    val text: String = "",
    val refs: List<Entry> = emptyList(),
)

data class ModelStatus(
    val state: ModelState,
    val modelName: String?,
    val tokensPerSec: Double?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AskViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as DearApp
    private val chats = app.chatRepository
    private val assistant = DiaryAssistant(app.modelManager)

    /** Null until the first question of a new session is asked. */
    val sessionId: StateFlow<Long?> = savedState.getStateFlow(KEY_SESSION, null)

    val sessions: StateFlow<List<ChatSession>> = chats.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages: StateFlow<List<AskMessage>> = sessionId
        .flatMapLatest { id -> id?.let { chats.messages(it).map { list -> list.map(::toUi) } } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _streaming = MutableStateFlow<StreamingReply?>(null)
    val streaming: StateFlow<StreamingReply?> = _streaming.asStateFlow()

    private val liveTokensPerSec = MutableStateFlow<Double?>(null)

    val modelStatus: StateFlow<ModelStatus> = combine(
        app.modelManager.state,
        app.modelManager.lastStats,
        liveTokensPerSec,
    ) { state, last, live ->
        ModelStatus(
            state = state,
            modelName = app.modelManager.installed()?.name?.removeSuffix(".gguf"),
            tokensPerSec = live ?: last?.tokensPerSec,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelStatus(ModelState.IDLE, null, null))

    private var job: Job? = null

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _streaming.value != null) return
        _streaming.value = StreamingReply()

        job = viewModelScope.launch {
            val sid = sessionId.value ?: chats.createSession(question).also { savedState[KEY_SESSION] = it }
            val history = chats.history(sid)
            chats.addUserMessage(sid, question)
            val entries = app.entryRepository.findRelevant(question)
            val byId = entries.associate { it.entry.id to it.entry }

            val raw = StringBuilder()
            var chunks = 0
            var firstChunkAt = 0L
            var error: String? = null
            try {
                assistant.ask(question, history, entries).collect { chunk ->
                    if (chunks == 0) firstChunkAt = SystemClock.elapsedRealtime()
                    chunks++
                    raw.append(chunk)
                    val elapsed = SystemClock.elapsedRealtime() - firstChunkAt
                    if (elapsed > 500) liveTokensPerSec.value = chunks * 1000.0 / elapsed
                    _streaming.value = StreamingReply(
                        text = DiaryAssistant.stripCitations(raw.toString()),
                        refs = DiaryAssistant.citedIds(raw.toString()).mapNotNull(byId::get),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Ask failed", t)
                error = t.message ?: t.javaClass.simpleName
            } finally {
                // Keep what was said, also when the user stopped the reply half-way.
                withContext(NonCancellable) {
                    val reply = DiaryAssistant.stripCitations(raw.toString())
                    when {
                        error != null -> chats.addReply(sid, error, emptyList(), isError = true)
                        reply.isNotEmpty() -> chats.addReply(
                            sid,
                            reply,
                            DiaryAssistant.citedIds(raw.toString()).filter { it in byId },
                        )
                    }
                }
                _streaming.value = null
                liveTokensPerSec.value = null
            }
        }
    }

    /** Stops the reply mid-way; the native side halts at the next token. */
    fun stop() {
        job?.cancel()
    }

    fun newSession() {
        stop()
        savedState[KEY_SESSION] = null
    }

    fun openSession(id: Long) {
        stop()
        savedState[KEY_SESSION] = id
    }

    fun deleteSession(id: Long) {
        if (sessionId.value == id) newSession()
        viewModelScope.launch { chats.deleteSession(id) }
    }

    private fun toUi(item: ChatMessageWithRefs) = AskMessage(
        id = item.message.id,
        text = item.message.text,
        isUser = item.message.role == ChatRole.USER,
        isError = item.message.isError,
        refs = item.refs.sortedByDescending { it.createdAt },
    )

    companion object {
        private const val TAG = "AskViewModel"
        private const val KEY_SESSION = "session"
    }
}
