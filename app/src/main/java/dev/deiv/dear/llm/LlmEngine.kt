package dev.deiv.dear.llm

/** One turn of a conversation. In a user turn, each [LlmEngine.MEDIA_MARKER] is replaced by the next [MediaInput]. */
data class ChatTurn(
    val role: Role,
    val text: String,
) {
    enum class Role(val value: String) {
        SYSTEM("system"), USER("user"), ASSISTANT("assistant")
    }
}

sealed interface MediaInput {
    /** Packed RGB, 3 bytes per pixel, row-major. */
    class Image(val rgb: ByteArray, val width: Int, val height: Int) : MediaInput

    /** Mono PCM samples at [EngineCapabilities.audioSampleRate]. */
    class Audio(val pcm: FloatArray) : MediaInput
}

data class GenerationRequest(
    val turns: List<ChatTurn>,
    val media: List<MediaInput> = emptyList(),
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    /** JSON schema the output is constrained to by a grammar, or null for free text. */
    val jsonSchema: String? = null,
)

data class GenerationStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val durationMs: Long,
) {
    val tokensPerSec: Double
        get() = if (durationMs > 0) generatedTokens * 1000.0 / durationMs else 0.0
}

data class EngineCapabilities(
    val vision: Boolean,
    val audio: Boolean,
    val audioSampleRate: Int,
)

/**
 * A loaded on-device model. Implementations are not thread-safe: [ModelManager] owns the single
 * instance and serializes every call.
 */
interface LlmEngine : AutoCloseable {
    val capabilities: EngineCapabilities

    /** Blocking. [onText] receives decoded text chunks as they are produced; returning false stops early. */
    fun generate(request: GenerationRequest, onText: (String) -> Boolean): GenerationStats

    companion object {
        const val MEDIA_MARKER = "<__media__>"
    }
}
