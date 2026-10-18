package dev.deiv.dear.llm

import android.util.Log
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.media.AudioDecoder
import dev.deiv.dear.media.VideoFrames
import dev.deiv.dear.net.LinkMetadataFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

data class CatalogResult(
    val title: String?,
    val summary: String,
    val tags: List<String>,
    val extractedText: String? = null,
    val durationMs: Long? = null,
)

/** Where a long (audio/video) catalog run is, for progress display. */
sealed interface CatalogStep {
    data class Frame(val index: Int, val total: Int) : CatalogStep
    data class Audio(val index: Int, val total: Int) : CatalogStep
    data object Summary : CatalogStep
}

/**
 * Asks the model for title, summary and tags of an entry. Output is constrained by a JSON schema,
 * so a reply always parses. Photos are shown to the model; videos are understood from a few frames
 * plus their audio transcript, audio from its transcript. Those runs are split in small steps whose
 * results go through [saveState], so a run Android stops half-way resumes instead of restarting.
 */
class EntryCataloger(
    private val modelManager: ModelManager,
    private val saveState: suspend (String) -> Unit = {},
    private val report: (CatalogStep) -> Unit = {},
) {

    /**
     * [parent] is the link a photo was pulled from; [photos] are the already cataloged photos of a
     * link, whose descriptions the link's summary is written from.
     */
    suspend fun catalog(entry: Entry, parent: Entry? = null, photos: List<Entry> = emptyList()): CatalogResult {
        val multimodal = modelManager.installed()?.mmproj != null
        val path = entry.filePath
        return when {
            multimodal && path != null && entry.type == EntryType.VIDEO -> catalogVideo(entry, path)
            multimodal && path != null && entry.type == EntryType.AUDIO -> catalogAudio(entry, path)
            else -> catalogSingle(entry, multimodal, parent, photos)
        }
    }

    // --- Notes, links, photos, files: one call ---

    private suspend fun catalogSingle(
        entry: Entry,
        multimodal: Boolean,
        parent: Entry?,
        photos: List<Entry>,
    ): CatalogResult {
        // Network and decoding happen before taking the model, which is shared with chat.
        val link = fetchLink(entry)
        val image = if (entry.type == EntryType.IMAGE && multimodal) {
            entry.filePath?.let { withContext(Dispatchers.Default) { ImageInput.load(it) } }
        } else {
            null
        }
        val body = when (entry.type) {
            EntryType.TEXT -> "Saved note:\n${entry.text.orEmpty()}"
            EntryType.LINK -> "Saved link: ${entry.url.orEmpty()}" + (link?.description?.let { "\n$it" } ?: "") +
                photosLabel(photos)
            else -> "Saved ${entry.type.name.lowercase()} file${fileLabel(entry)}" +
                (parent?.url?.let { "\nOne of the photos of a post saved from this link: $it" } ?: "")
        }
        val json = summarize(entry, body, image)
        val visibleText = json.optString("visible_text").trim().takeIf { it.isNotEmpty() }
        return CatalogResult(
            title = link?.title ?: json.title(),
            summary = json.optString("summary").trim(),
            tags = json.tags(),
            extractedText = link?.description ?: visibleText,
        )
    }

    // --- Audio: transcript, then summary ---

    private suspend fun catalogAudio(entry: Entry, path: String): CatalogResult {
        val progress = Progress.parse(entry.catalogState)
        val transcript = transcribe(path, progress)
        val duration = withContext(Dispatchers.IO) { VideoFrames.durationMs(path) }

        report(CatalogStep.Summary)
        val body = buildString {
            append("Saved audio file").append(fileLabel(entry)).append(durationLabel(duration)).append('\n')
            append("Transcript:\n").append(transcript?.take(TRANSCRIPT_PROMPT_CHARS) ?: "(no speech detected)")
        }
        val json = summarize(entry, body, image = null)
        return CatalogResult(
            title = json.title(),
            summary = json.optString("summary").trim(),
            tags = json.tags(),
            extractedText = transcript,
            durationMs = duration.takeIf { it > 0 },
        )
    }

    // --- Video: frames + audio transcript, then summary ---

    private suspend fun catalogVideo(entry: Entry, path: String): CatalogResult {
        val progress = Progress.parse(entry.catalogState)
        val duration = withContext(Dispatchers.IO) { VideoFrames.durationMs(path) }
        if (progress.framesTotal < 0) progress.framesTotal = VideoFrames.frameCountFor(duration)

        if (progress.frames.size < progress.framesTotal) {
            val frames = withContext(Dispatchers.IO) { VideoFrames.extract(path, progress.framesTotal, ImageInput.MAX_SIDE) }
            progress.framesTotal = frames.bitmaps.size
            try {
                for (i in progress.frames.size until frames.bitmaps.size) {
                    report(CatalogStep.Frame(i + 1, frames.bitmaps.size))
                    val image = withContext(Dispatchers.Default) { ImageInput.fromBitmap(frames.bitmaps[i]) }
                    progress.frames += describeFrame(image)
                    saveState(progress.toJson())
                }
            } finally {
                frames.bitmaps.forEach { it.recycle() }
            }
        }

        val transcript = transcribe(path, progress)

        report(CatalogStep.Summary)
        val body = buildString {
            append("Saved video file").append(fileLabel(entry)).append(durationLabel(duration)).append('\n')
            if (progress.frames.isNotEmpty()) {
                append("What the frames show, in order:\n")
                progress.frames.forEachIndexed { i, frame -> append(i + 1).append(". ").append(frame).append('\n') }
            }
            append("Audio transcript:\n").append(transcript?.take(TRANSCRIPT_PROMPT_CHARS) ?: "(no speech)")
        }
        val json = summarize(entry, body, image = null)
        return CatalogResult(
            title = json.title(),
            summary = json.optString("summary").trim(),
            tags = json.tags(),
            extractedText = transcript,
            durationMs = duration.takeIf { it > 0 },
        )
    }

    private suspend fun describeFrame(image: MediaInput.Image): String =
        modelManager.complete(
            GenerationRequest(
                turns = listOf(
                    ChatTurn(
                        ChatTurn.Role.USER,
                        "${LlmEngine.MEDIA_MARKER}\nDescribe what this video frame shows in one sentence, " +
                            "in ${outputLanguage()}. Mention any readable text.",
                    ),
                ),
                media = listOf(image),
                maxTokens = 96,
                temperature = 0.2f,
            )
        ).trim()

    /** Transcribes in [CHUNK_SECONDS] chunks, saving after each; null when there is no speech or no audio support. */
    private suspend fun transcribe(path: String, progress: Progress): String? {
        if (!progress.audioDone) {
            val decoded = withContext(Dispatchers.IO) { AudioDecoder.decode(path) }
            if (decoded != null) {
                val chunkSize = CHUNK_SECONDS * decoded.sampleRate
                val chunks = (decoded.pcm.size + chunkSize - 1) / chunkSize
                try {
                    for (i in progress.transcript.size until chunks) {
                        report(CatalogStep.Audio(i + 1, chunks))
                        val pcm = decoded.pcm.copyOfRange(i * chunkSize, min((i + 1) * chunkSize, decoded.pcm.size))
                        progress.transcript += if (rms(pcm) < SILENCE_RMS) "" else transcribeChunk(pcm)
                        saveState(progress.toJson())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ModelUnavailableException) {
                    throw e
                } catch (e: Exception) {
                    // E.g. a projector without audio support: carry on with what we have.
                    Log.w(TAG, "Transcription failed, continuing without audio: ${e.message}")
                }
            }
            progress.audioDone = true
            saveState(progress.toJson())
        }
        return progress.transcript.filter { it.isNotBlank() }.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun transcribeChunk(pcm: FloatArray): String =
        modelManager.complete(
            GenerationRequest(
                turns = listOf(
                    ChatTurn(
                        ChatTurn.Role.USER,
                        "${LlmEngine.MEDIA_MARKER}\nTranscribe the speech in this audio verbatim, in the language " +
                            "it is spoken. Reply with the transcript only. If there is no speech, reply with a few " +
                            "words in square brackets describing the sound, in ${outputLanguage()}.",
                    ),
                ),
                media = listOf(MediaInput.Audio(pcm)),
                maxTokens = 384,
                temperature = 0f,
            )
        ).trim()

    // --- Shared ---

    private suspend fun summarize(entry: Entry, body: String, image: MediaInput.Image?): JSONObject {
        val user = buildString {
            if (image != null) append(LlmEngine.MEDIA_MARKER).append('\n')
            append(body)
            entry.sourceApp?.let { append("\nShared from: ").append(it) }
            if (entry.type != EntryType.TEXT) entry.text?.let { append("\nUser note: ").append(it) }
        }
        val reply = modelManager.complete(
            GenerationRequest(
                turns = listOf(
                    ChatTurn(ChatTurn.Role.SYSTEM, systemPrompt(withImage = image != null)),
                    ChatTurn(ChatTurn.Role.USER, user),
                ),
                media = listOfNotNull(image),
                maxTokens = if (image != null) 640 else 320,
                temperature = 0.2f,
                jsonSchema = if (image != null) IMAGE_SCHEMA else TEXT_SCHEMA,
            )
        )
        return JSONObject(reply)
    }

    private data class LinkInfo(val title: String?, val description: String)

    /** Page metadata for LINK entries; [LinkInfo.description] is stored as extracted text. */
    private suspend fun fetchLink(entry: Entry): LinkInfo? {
        if (entry.type != EntryType.LINK) return null
        val url = entry.url ?: return null
        val meta = LinkMetadataFetcher.fetch(url) ?: return null
        val description = buildString {
            meta.title?.let { append("title: ").append(it) }
            meta.siteName?.let { append("\nsite: ").append(it) }
            meta.author?.let { append("\nauthor: ").append(it) }
            meta.type?.let { append("\ntype: ").append(it) }
            meta.description?.let { append("\ndescription: ").append(it) }
        }.trim()
        return LinkInfo(title = meta.title, description = description).takeIf { description.isNotEmpty() }
    }

    private fun systemPrompt(withImage: Boolean): String = buildString {
        append("You catalog items saved in a personal archive so they can be found again later. ")
        append("Reply with a single JSON object. Write every field in ").append(outputLanguage()).append(".\n")
        append("- title: a short title, at most 8 words.\n")
        append("- summary: one or two sentences on what the item is about.\n")
        append("- tags: 2 to 6 lowercase topic keywords, single words or hyphenated.\n")
        if (withImage) {
            append("- visible_text: any readable text in the image, transcribed verbatim; empty if there is none.\n")
            append("Describe what the image actually shows; do not guess from the file name.")
        }
    }

    private fun fileLabel(entry: Entry): String = buildString {
        entry.originalName?.let { append(": ").append(it) }
        entry.mimeType?.let { append(" (").append(it).append(')') }
    }

    /** What a post's photos show, from their own catalog runs. */
    private fun photosLabel(photos: List<Entry>): String {
        val described = photos.withIndex().filter { (_, p) -> p.summary != null || p.extractedText != null }
        if (described.isEmpty()) return ""
        return buildString {
            append("\nThe post's photos, in order:")
            for ((i, photo) in described) {
                append('\n').append(i + 1).append('.')
                photo.title?.let { append(' ').append(it).append('.') }
                photo.summary?.let { append(' ').append(it) }
                photo.extractedText?.let { append(" Text in it: \"").append(it.take(PHOTO_TEXT_CHARS)).append('"') }
            }
        }.take(PHOTOS_PROMPT_CHARS)
    }

    private fun durationLabel(ms: Long): String =
        if (ms <= 0) "" else " — duration %d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private fun JSONObject.title(): String? = optString("title").trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.tags(): List<String> {
        val arr = optJSONArray("tags") ?: return emptyList()
        return List(arr.length()) { arr.optString(it) }
            .map { it.trim().removePrefix("#").lowercase().replace(WHITESPACE, "-") }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS)
    }

    private fun rms(pcm: FloatArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sum = 0.0
        for (s in pcm) sum += s * s
        return sqrt(sum / pcm.size)
    }

    /** The phone's language, in English, as the model reads instructions best in English. */
    private fun outputLanguage(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)

    /** Partial results of an audio/video run, persisted as JSON in [Entry.catalogState]. */
    private class Progress(
        val frames: MutableList<String> = mutableListOf(),
        val transcript: MutableList<String> = mutableListOf(),
        var framesTotal: Int = -1,
        var audioDone: Boolean = false,
    ) {
        fun toJson(): String = JSONObject()
            .put("frames", JSONArray(frames))
            .put("transcript", JSONArray(transcript))
            .put("framesTotal", framesTotal)
            .put("audioDone", audioDone)
            .toString()

        companion object {
            fun parse(json: String?): Progress {
                val obj = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Progress()
                fun strings(key: String) = obj.optJSONArray(key)
                    ?.let { arr -> MutableList(arr.length()) { arr.optString(it) } }
                    ?: mutableListOf()
                return Progress(
                    frames = strings("frames"),
                    transcript = strings("transcript"),
                    framesTotal = obj.optInt("framesTotal", -1),
                    audioDone = obj.optBoolean("audioDone", false),
                )
            }
        }
    }

    companion object {
        private const val TAG = "EntryCataloger"
        private const val MAX_TAGS = 6
        private const val CHUNK_SECONDS = 30
        private const val SILENCE_RMS = 0.003
        private const val TRANSCRIPT_PROMPT_CHARS = 6000
        private const val PHOTO_TEXT_CHARS = 400
        private const val PHOTOS_PROMPT_CHARS = 6000
        private val WHITESPACE = Regex("""\s+""")

        private const val TEXT_PROPERTIES = """
            "title": {"type": "string", "maxLength": 80},
            "summary": {"type": "string", "maxLength": 400},
            "tags": {"type": "array", "items": {"type": "string", "maxLength": 30}, "minItems": 1, "maxItems": 6}
        """

        private val TEXT_SCHEMA = """
            {"type": "object", "properties": {$TEXT_PROPERTIES},
             "required": ["title", "summary", "tags"], "additionalProperties": false}
        """.trimIndent()

        private val IMAGE_SCHEMA = """
            {"type": "object", "properties": {$TEXT_PROPERTIES,
                "visible_text": {"type": "string", "maxLength": 1500}},
             "required": ["title", "summary", "tags", "visible_text"], "additionalProperties": false}
        """.trimIndent()
    }
}
