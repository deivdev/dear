package dev.deiv.dear.catalog

import android.content.Context
import android.util.Log
import dev.deiv.dear.data.db.EntryDao
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.data.repository.EntryRepository
import dev.deiv.dear.net.InstagramMedia
import java.io.File

/**
 * Pulls the photos of a shared post (for now: public Instagram posts) into IMAGE entries pointing
 * back at the link, so each photo is cataloged, searched and shown like a shared one.
 */
class PostPhotos(
    private val context: Context,
    private val dao: EntryDao,
) {

    fun supports(entry: Entry): Boolean =
        entry.type == EntryType.LINK && entry.url?.let(InstagramMedia::shortcodeOf) != null

    /**
     * The link's photo entries, downloading them the first time. All or nothing: a failed download
     * leaves no photos behind, so the next attempt starts over instead of keeping half a post.
     */
    suspend fun expand(link: Entry): List<Entry> {
        dao.getChildren(link.id).takeIf { it.isNotEmpty() }?.let { return it }
        val shortcode = link.url?.let(InstagramMedia::shortcodeOf) ?: return emptyList()
        val urls = InstagramMedia.photoUrls(shortcode)
        if (urls.isEmpty()) return emptyList()

        val dir = EntryRepository.entriesDir(context)
        val photos = mutableListOf<Entry>()
        try {
            for ((i, url) in urls.withIndex()) {
                val photo = InstagramMedia.download(url, dir)
                photos += Entry(
                    type = EntryType.IMAGE,
                    // Newest first in the collection: right below the link, in the post's order.
                    createdAt = link.createdAt - (i + 1),
                    filePath = photo.file.absolutePath,
                    mimeType = photo.mimeType,
                    sizeBytes = photo.file.length(),
                    originalName = "${shortcode}_${i + 1}.${photo.file.extension}",
                    sourceApp = link.sourceApp,
                    parentId = link.id,
                )
            }
            dao.insertAll(photos)
        } catch (t: Throwable) {
            photos.forEach { it.filePath?.let { path -> File(path).delete() } }
            throw t
        }
        Log.i(TAG, "Pulled ${photos.size} photos for entry ${link.id}")
        return dao.getChildren(link.id)
    }

    companion object {
        private const val TAG = "PostPhotos"
    }
}
