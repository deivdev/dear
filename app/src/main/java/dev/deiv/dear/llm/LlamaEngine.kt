package dev.deiv.dear.llm

import android.os.SystemClock
import android.util.Log

/** [LlmEngine] backed by llama.cpp (+ libmtmd for image/audio input) through JNI. */
class LlamaEngine private constructor(private var handle: Long) : LlmEngine {

    override val capabilities = EngineCapabilities(
        vision = nativeSupportsVision(handle),
        audio = nativeSupportsAudio(handle),
        audioSampleRate = nativeAudioSampleRate(handle),
    )

    override fun generate(request: GenerationRequest, onText: (String) -> Boolean): GenerationStats {
        check(handle != 0L) { "engine closed" }
        val dims = IntArray(request.media.size * 2)
        val media = Array<Any>(request.media.size) { i ->
            when (val m = request.media[i]) {
                is MediaInput.Image -> {
                    dims[2 * i] = m.width
                    dims[2 * i + 1] = m.height
                    m.rgb
                }
                is MediaInput.Audio -> m.pcm
            }
        }
        val start = SystemClock.elapsedRealtime()
        // Native code only hands over complete UTF-8 sequences, so each chunk decodes on its own.
        val counts = nativeGenerate(
            handle,
            request.turns.map { it.role.value }.toTypedArray(),
            request.turns.map { it.text }.toTypedArray(),
            media,
            dims,
            request.maxTokens,
            request.temperature,
            request.jsonSchema,
            TokenListener { bytes -> onText(String(bytes, Charsets.UTF_8)) },
        )
        return GenerationStats(
            promptTokens = counts[0],
            generatedTokens = counts[1],
            durationMs = SystemClock.elapsedRealtime() - start,
        )
    }

    override fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    /** Called from native code for every chunk; the name and signature are looked up by JNI. */
    fun interface TokenListener {
        fun onToken(bytes: ByteArray): Boolean
    }

    companion object {
        private const val TAG = "LlamaEngine"

        private val libraryLoaded: Boolean = try {
            System.loadLibrary("llama-android")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            false
        }

        /**
         * Blocking: loads [modelPath] and, when given, the multimodal projector [mmprojPath].
         * [nativeLibDir] is where ggml finds its CPU backend variants.
         */
        fun load(modelPath: String, mmprojPath: String?, nativeLibDir: String): LlamaEngine {
            check(libraryLoaded) { "native library not loaded" }
            return LlamaEngine(nativeLoad(modelPath, mmprojPath, nativeLibDir))
        }

        @JvmStatic
        private external fun nativeLoad(modelPath: String, mmprojPath: String?, nativeLibDir: String): Long

        @JvmStatic
        private external fun nativeSupportsVision(handle: Long): Boolean

        @JvmStatic
        private external fun nativeSupportsAudio(handle: Long): Boolean

        @JvmStatic
        private external fun nativeAudioSampleRate(handle: Long): Int

        @JvmStatic
        private external fun nativeGenerate(
            handle: Long,
            roles: Array<String>,
            contents: Array<String>,
            media: Array<Any>,
            mediaDims: IntArray,
            maxTokens: Int,
            temperature: Float,
            jsonSchema: String?,
            listener: TokenListener,
        ): IntArray

        @JvmStatic
        private external fun nativeFree(handle: Long)
    }
}
