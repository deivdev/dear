package dev.deiv.dear.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class LlamaModel private constructor(private val contextPtr: Long) {

    @Volatile var lastTokensPerSec: Double = 0.0
        private set
    @Volatile var lastTokenCount: Int = 0
        private set
    @Volatile var lastDurationMs: Long = 0
        private set


    companion object {
        private const val TAG = "LlamaModel"
        private var libraryLoaded = false

        init {
            try {
                System.loadLibrary("llama-android")
                libraryLoaded = true
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }

        suspend fun load(modelPath: String): LlamaModel = withContext(Dispatchers.IO) {
            if (!libraryLoaded) {
                throw IllegalStateException("Native library not loaded")
            }
            Log.i(TAG, "Loading model: $modelPath")
            val ptr = nativeLoadModel(modelPath)
            if (ptr == 0L) {
                throw IllegalStateException("Failed to load model from $modelPath")
            }
            Log.i(TAG, "Model loaded, context ptr: $ptr")
            LlamaModel(ptr)
        }

        @JvmStatic
        private external fun nativeLoadModel(path: String): Long
    }

    fun generate(prompt: String, maxTokens: Int = 512): Flow<String> = flow {
        val start = System.nanoTime()
        val tokens = nativeGenerate(contextPtr, prompt, maxTokens)
        val elapsedNs = System.nanoTime() - start
        val ms = elapsedNs / 1_000_000
        lastTokenCount = tokens.size
        lastDurationMs = ms
        lastTokensPerSec = if (ms > 0) tokens.size * 1000.0 / ms else 0.0
        for (token in tokens) {
            emit(token)
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        if (contextPtr != 0L) {
            nativeFree(contextPtr)
        }
    }

    private external fun nativeGenerate(ctx: Long, prompt: String, maxTokens: Int): Array<String>
    private external fun nativeFree(ctx: Long)
}
