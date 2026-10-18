package dev.deiv.dear.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.deiv.dear.data.db.EntryDao
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.model.EntryWithTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EntryRepository(
    private val context: Context,
    private val dao: EntryDao,
    /** Asks for a catalog run; called whenever an entry needs (re)cataloging. */
    private val requestCatalog: () -> Unit = {},
) {

    val all: Flow<List<EntryWithTags>> = dao.observeAll()

    fun observe(id: Long): Flow<EntryWithTags?> = dao.observeById(id)

    /** Photos pulled from link [id], in their original order. */
    fun observePhotos(id: Long): Flow<List<Entry>> = dao.observeChildren(id)

    /** Removes the entry and the file it owns in app storage, photos pulled from it included. */
    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        // The database drops the photos' rows with the link; their files are ours to delete.
        val files = (dao.getChildren(entry.id) + entry).mapNotNull { it.filePath }
        dao.delete(entry)
        files.forEach { File(it).delete() }
    }

    suspend fun recatalog(id: Long) {
        dao.resetCatalog(id)
        requestCatalog()
    }

    suspend fun getRecent(limit: Int = 30): List<Entry> = dao.getRecent(limit)

    /**
     * Entries worth showing the model for [question]: the best keyword matches, then the latest
     * few so that "what did I save today?" works without keywords. Most relevant first.
     */
    suspend fun findRelevant(question: String, limit: Int = 12, latest: Int = 5): List<EntryWithTags> =
        withContext(Dispatchers.Default) {
            val all = dao.getAllWithTags()
            val terms = TextMatching.queryTerms(question)
            val matches = if (terms.isEmpty()) {
                emptyList()
            } else {
                all.map { it to relevance(it, terms) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(limit)
                    .map { it.first }
            }
            // The latest things saved, not the photos pulled in with a post.
            (matches + all.filter { it.entry.parentId == null }.take(latest)).distinctBy { it.entry.id }
        }

    private fun relevance(item: EntryWithTags, terms: List<String>): Int {
        val e = item.entry
        val weighted = listOf(
            3 to e.title,
            3 to item.tags.joinToString(" ") { it.name },
            2 to e.summary,
            2 to e.text,
            1 to e.extractedText,
            1 to e.url,
            1 to e.originalName,
        )
        return weighted.sumOf { (weight, field) ->
            val haystack = field?.let(TextMatching::normalize) ?: return@sumOf 0
            weight * terms.count { TextMatching.matches(haystack, it) }
        }
    }

    /**
     * Stores typed or shared text. A URL anywhere in it makes the entry a LINK and the rest of the
     * text (plus the share subject, e.g. the page title Chrome sends) is kept as its caption.
     */
    suspend fun addText(text: String, sourceApp: String? = null, subject: String? = null): Long {
        val url = findUrl(text)
        // "Look at this https://x." -> "Look at this." rather than a dangling " ."
        val rest = url?.let { text.replace(it, "").replace(SPACE_BEFORE_PUNCT, "") } ?: text
        val id = dao.insert(
            Entry(
                type = if (url != null) EntryType.LINK else EntryType.TEXT,
                text = joinCaption(subject, rest),
                url = url,
                sourceApp = sourceApp,
            )
        )
        requestCatalog()
        return id
    }

    suspend fun addFromUri(
        uri: Uri,
        mimeType: String?,
        sourceApp: String? = null,
        caption: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // The intent type is often a wildcard shared by several items (image/*, */*): ask the provider first.
        val resolvedMime = resolver.getType(uri) ?: mimeType
        val (displayName, size) = queryUriMetadata(resolver, uri)
        val ext = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: extFromMime(resolvedMime)
        val dest = copyToInternal(resolver, uri, ext)
        val id = try {
            dao.insert(
                Entry(
                    type = mimeToType(resolvedMime),
                    text = caption?.trim()?.takeIf { it.isNotEmpty() },
                    filePath = dest.absolutePath,
                    mimeType = resolvedMime,
                    sizeBytes = size ?: dest.length(),
                    sourceApp = sourceApp,
                    originalName = displayName,
                )
            )
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        requestCatalog()
        id
    }

    private fun copyToInternal(resolver: ContentResolver, uri: Uri, ext: String?): File {
        val dir = entriesDir(context)
        val name = buildString {
            append(UUID.randomUUID().toString())
            if (!ext.isNullOrEmpty()) {
                append('.')
                append(ext)
            }
        }
        val dest = File(dir, name)
        try {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open input stream for $uri")
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        return dest
    }

    private fun queryUriMetadata(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun mimeToType(mime: String?): EntryType = when {
        mime == null -> EntryType.FILE
        mime.startsWith("image/") -> EntryType.IMAGE
        mime.startsWith("video/") -> EntryType.VIDEO
        mime.startsWith("audio/") -> EntryType.AUDIO
        mime.startsWith("text/") -> EntryType.TEXT
        else -> EntryType.FILE
    }

    private fun extFromMime(mime: String?): String? = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "text/plain" -> "txt"
        else -> null
    }

    private fun findUrl(text: String): String? {
        val raw = URL_REGEX.find(text)?.value ?: return null
        // Sentence punctuation glued to the URL; keep ')' when it closes a '(' inside the URL (Wikipedia).
        val trimmed = raw.trimEnd('.', ',', ';', ':', '!', '?', '"', '\'')
        return if (trimmed.endsWith(')') && '(' !in trimmed) trimmed.trimEnd(')') else trimmed
    }

    private fun joinCaption(vararg parts: String?): String? =
        parts.mapNotNull { it?.trim()?.takeIf { p -> p.isNotEmpty() } }
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }

    companion object {
        /** Where entries' files live in app storage. */
        fun entriesDir(context: Context): File = File(context.filesDir, "entries").apply { mkdirs() }

        private val URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
        private val SPACE_BEFORE_PUNCT = Regex("""[ \t]+(?=[.,;:!?])""")
    }
}
