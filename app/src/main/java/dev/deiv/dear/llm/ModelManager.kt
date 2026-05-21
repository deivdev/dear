package dev.deiv.dear.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelState {
    NOT_FOUND, LOADING, READY, ERROR
}

class ModelManager(private val context: Context) {

    private val _state = MutableStateFlow(ModelState.NOT_FOUND)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var model: LlamaModel? = null
    @Volatile var currentModelFile: File? = null
        private set

    val modelDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun findModel(): File? {
        return modelDir.listFiles()?.firstOrNull { it.extension == "gguf" }
    }

    suspend fun loadModel(): LlamaModel? {
        val modelFile = findModel()
        if (modelFile == null) {
            _state.value = ModelState.NOT_FOUND
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                _state.value = ModelState.LOADING
                val loaded = LlamaModel.load(modelFile.absolutePath)
                model = loaded
                currentModelFile = modelFile
                _state.value = ModelState.READY
                loaded
            } catch (e: Exception) {
                _state.value = ModelState.ERROR
                null
            }
        }
    }

    suspend fun importModel(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelState.LOADING
            val dest = File(modelDir, RECOMMENDED_MODEL)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                    }
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for $uri")
                _state.value = ModelState.ERROR
                return@withContext false
            }
            Log.i(TAG, "Model imported: ${dest.absolutePath} (${dest.length() / 1024 / 1024}MB)")
            loadModel()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
            _state.value = ModelState.ERROR
            false
        }
    }

    fun getModel(): LlamaModel? = model

    fun release() {
        model?.close()
        model = null
        currentModelFile = null
        _state.value = ModelState.NOT_FOUND
    }

    suspend fun deleteCurrentModel(): Boolean = withContext(Dispatchers.IO) {
        val file = currentModelFile ?: return@withContext false
        release()
        runCatching { file.delete() }.getOrDefault(false)
    }

    fun listInstalled(): List<File> = modelDir.listFiles()
        ?.filter { it.extension == "gguf" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    companion object {
        private const val TAG = "ModelManager"
        const val RECOMMENDED_MODEL = "Phi-4-mini-instruct-Q4_K_M.gguf"
    }
}
