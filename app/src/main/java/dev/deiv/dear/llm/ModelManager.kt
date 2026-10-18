package dev.deiv.dear.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelState {
    /** No model file on disk. */
    NOT_INSTALLED,

    /** Installed but not in memory; it is loaded on first use. */
    IDLE,
    LOADING,
    READY,
    ERROR,
}

data class InstalledModel(
    val file: File,
    val mmproj: File?,
) {
    val name: String
        get() = file.name
}

/** The model cannot run right now; not the fault of the request that needed it. */
open class ModelUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class ModelNotInstalledException : ModelUnavailableException("No model installed")

class ModelLoadException(cause: Throwable) : ModelUnavailableException("Model failed to load: ${cause.message}", cause)

/**
 * Owns the single [LlmEngine]. The model is loaded on demand, used by one caller at a time and
 * released after [IDLE_UNLOAD_MS] without use, so a multi-GB model does not sit in RAM.
 */
class ModelManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ModelState.NOT_INSTALLED)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _lastStats = MutableStateFlow<GenerationStats?>(null)
    val lastStats: StateFlow<GenerationStats?> = _lastStats.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    private val mutex = Mutex()
    private var engine: LlmEngine? = null
    private var engineModel: InstalledModel? = null
    private var unloadJob: Job? = null

    val modelDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    init {
        refresh()
    }

    /** The newest model file in [modelDir], with its projector when the catalog knows it. */
    fun installed(): InstalledModel? {
        val files = modelDir.listFiles { f -> f.extension == "gguf" }.orEmpty()
        val main = files.filterNot { ModelCatalog.isProjector(it.name) }
            .maxByOrNull { it.lastModified() }
            ?: return null
        val mmproj = ModelCatalog.byModelFileName(main.name)?.mmproj
            ?.let { File(modelDir, it.fileName) }
            ?.takeIf { it.exists() }
        return InstalledModel(main, mmproj)
    }

    /** Re-reads [modelDir] after files were added or removed. */
    fun refresh() {
        if (engine != null) return
        _state.value = if (installed() != null) ModelState.IDLE else ModelState.NOT_INSTALLED
    }

    /** Runs [block] with the loaded engine, loading it first if needed. Callers are serialized. */
    suspend fun <T> withEngine(block: suspend (LlmEngine) -> T): T = mutex.withLock {
        unloadJob?.cancel()
        try {
            block(ensureLoaded())
        } finally {
            scheduleUnload()
        }
    }

    /** Streams generated text. Cancelling the collector stops native generation at the next token. */
    fun stream(request: GenerationRequest): Flow<String> = channelFlow {
        withEngine { engine ->
            val stats = withContext(Dispatchers.IO) {
                engine.generate(request) { chunk -> isActive && trySend(chunk).isSuccess }
            }
            _lastStats.value = stats
        }
    }.buffer(Channel.UNLIMITED)

    suspend fun complete(request: GenerationRequest): String = buildString {
        stream(request).collect { append(it) }
    }

    /** Frees the model now, e.g. when background work is done and the process may get frozen. */
    suspend fun release() {
        mutex.withLock {
            unloadJob?.cancel()
            closeEngine()
        }
    }

    suspend fun importModel(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val name = displayName(uri)?.takeIf { it.endsWith(".gguf") } ?: "imported-${System.currentTimeMillis()}.gguf"
            val dest = File(modelDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open $uri")
            Log.i(TAG, "Model imported: ${dest.name} (${dest.length() / 1_000_000} MB)")
            mutex.withLock { closeEngine() }
            refresh()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to import model", t)
            lastError = t.message
            false
        }
    }

    /** Deletes the active model and its projector. */
    suspend fun deleteCurrentModel(): Boolean = withContext(Dispatchers.IO) {
        val model = installed() ?: return@withContext false
        mutex.withLock { closeEngine() }
        val deleted = model.file.delete()
        model.mmproj?.delete()
        refresh()
        deleted
    }

    private suspend fun ensureLoaded(): LlmEngine {
        val model = installed() ?: throw ModelNotInstalledException()
        engine?.let { loaded ->
            if (engineModel == model) return loaded
            closeEngine()
        }
        _state.value = ModelState.LOADING
        return try {
            // Native loading cannot be interrupted, and withContext drops its result when the caller
            // is cancelled meanwhile (e.g. it left the screen). The engine is kept inside the block
            // so it cannot leak gigabytes of weights; the idle timer unloads it.
            withContext(Dispatchers.IO) {
                LlamaEngine.load(
                    modelPath = model.file.absolutePath,
                    mmprojPath = model.mmproj?.absolutePath,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                ).also { loaded ->
                    engine = loaded
                    engineModel = model
                    lastError = null
                    _state.value = ModelState.READY
                    Log.i(TAG, "Loaded ${model.name}: ${loaded.capabilities}")
                }
            }
        } catch (e: CancellationException) {
            // The caller went away; the model did not fail.
            refresh()
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load ${model.name}", t)
            lastError = t.message
            _state.value = ModelState.ERROR
            throw ModelLoadException(t)
        }
    }

    private fun scheduleUnload() {
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { closeEngine() }
            Log.i(TAG, "Model unloaded after idle timeout")
        }
    }

    /** Caller must hold [mutex]. */
    private fun closeEngine() {
        engine?.close()
        engine = null
        engineModel = null
        refresh()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    companion object {
        private const val TAG = "ModelManager"
        private const val IDLE_UNLOAD_MS = 60_000L
    }
}
