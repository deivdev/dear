package dev.deiv.dear.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.URLUtil
import dev.deiv.dear.data.db.EntryDao
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EntryRepository(
    private val context: Context,
    private val dao: EntryDao,
    private val onInserted: (Long) -> Unit = {},
) {

    val all: Flow<List<Entry>> = dao.getAll()

    fun byType(type: EntryType): Flow<List<Entry>> = dao.getByType(type)

    fun search(query: String): Flow<List<Entry>> = dao.search(query)

    suspend fun getRecent(limit: Int = 30): List<Entry> = dao.getRecent(limit)

    suspend fun addText(text: String, sourceApp: String? = null): Long {
        val type = if (URLUtil.isNetworkUrl(text.trim())) EntryType.LINK else EntryType.TEXT
        val id = dao.insert(
            Entry(
                type = type,
                textContent = text,
                sourceApp = sourceApp,
            )
        )
        onInserted(id)
        return id
    }

    suspend fun addFromUri(uri: Uri, mimeType: String?, sourceApp: String? = null): Long =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val resolvedMime = mimeType ?: resolver.getType(uri)
            val type = mimeToType(resolvedMime)
            val (displayName, size) = queryUriMetadata(resolver, uri)
            val ext = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                ?: extFromMime(resolvedMime)
            val dest = copyToInternal(resolver, uri, ext)
            val id = dao.insert(
                Entry(
                    type = type,
                    filePath = dest.absolutePath,
                    mimeType = resolvedMime,
                    sizeBytes = size ?: dest.length(),
                    sourceApp = sourceApp,
                    originalName = displayName,
                )
            )
            onInserted(id)
            id
        }

    private fun copyToInternal(resolver: ContentResolver, uri: Uri, ext: String?): File {
        val dir = File(context.filesDir, "entries").apply { mkdirs() }
        val name = buildString {
            append(UUID.randomUUID().toString())
            if (!ext.isNullOrEmpty()) {
                append('.')
                append(ext)
            }
        }
        val dest = File(dir, name)
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open input stream for $uri")
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
}
