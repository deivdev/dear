package dev.deiv.dear.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class DownloadedPhoto(val file: File, val mimeType: String)

/**
 * Photos of a public Instagram post. The post page needs a login, its embed page does not and
 * carries the whole carousel as JSON. Unofficial: if Instagram changes the page, [photoUrls] finds
 * nothing and the link is kept without photos.
 */
object InstagramMedia {
    private const val TAG = "InstagramMedia"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Dear/1.0"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_PAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024

    // A carousel holds at most 20 items.
    private const val MAX_PHOTOS = 20

    private val POST_URL = Regex(
        """^https?://(?:www\.|m\.)?instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val EMBED_IMAGE = Regex("""<img[^>]+class="EmbeddedMediaImage"[^>]+src="([^"]+)"""")

    fun shortcodeOf(url: String): String? = POST_URL.find(url)?.groupValues?.get(1)

    /**
     * Full-size photo URLs of the post, in order. Videos are skipped, so a reel gives an empty list.
     * Throws [IOException] when Instagram cannot be reached.
     */
    suspend fun photoUrls(shortcode: String): List<String> = withContext(Dispatchers.IO) {
        val html = fetchPage("https://www.instagram.com/p/$shortcode/embed/captioned/")
        val media = contextJson(html)?.optJSONObject("gql_data")?.optJSONObject("shortcode_media")
        val urls = if (media != null) {
            photosOf(media)
        } else {
            // Older embed layout: only the first photo, as a plain <img>.
            listOfNotNull(EMBED_IMAGE.find(html)?.groupValues?.get(1)?.replace("&amp;", "&"))
        }
        if (urls.isEmpty()) Log.i(TAG, "No photos found for $shortcode")
        urls.take(MAX_PHOTOS)
    }

    /** Saves the photo at [url] in [dir] under a random name. */
    suspend fun download(url: String, dir: File): DownloadedPhoto = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for photo")
            val mime = conn.contentType?.substringBefore(';')?.trim()
                ?.takeIf { it.startsWith("image/") }
                ?: throw IOException("Not an image: ${conn.contentType}")
            if (conn.contentLengthLong > MAX_PHOTO_BYTES) throw IOException("Photo too large")
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                else -> "jpg"
            }
            val dest = File(dir, "${UUID.randomUUID()}.$ext")
            try {
                conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                if (dest.length() > MAX_PHOTO_BYTES) throw IOException("Photo too large")
            } catch (t: Throwable) {
                dest.delete()
                throw t
            }
            DownloadedPhoto(dest, mime)
        } finally {
            conn.disconnect()
        }
    }

    private fun photosOf(media: JSONObject): List<String> {
        val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        val nodes = if (edges != null) {
            List(edges.length()) { edges.optJSONObject(it)?.optJSONObject("node") }.filterNotNull()
        } else {
            listOf(media)
        }
        return nodes
            .filter { !it.optBoolean("is_video") }
            .mapNotNull { it.optString("display_url").takeIf { url -> url.startsWith("https://") } }
    }

    /**
     * The `contextJSON` value of the embed page: a JSON document inside a JSON string. Scanned by
     * hand, as a regex over a string this long overflows the stack.
     */
    private fun contextJson(html: String): JSONObject? {
        val key = "\"contextJSON\":\""
        val start = html.indexOf(key).takeIf { it >= 0 }?.plus(key.length) ?: return null
        var end = start
        while (end < html.length && html[end] != '"') end += if (html[end] == '\\') 2 else 1
        if (end >= html.length) return null
        return runCatching {
            val text = JSONTokener("\"${html.substring(start, end)}\"").nextValue() as String
            JSONObject(text)
        }.onFailure { Log.w(TAG, "Unreadable contextJSON: ${it.message}") }.getOrNull()
    }

    private fun fetchPage(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for embed page")
            val bytes = conn.inputStream.use { input ->
                val buf = ByteArray(MAX_PAGE_BYTES)
                var off = 0
                while (off < buf.size) {
                    val n = input.read(buf, off, buf.size - off)
                    if (n <= 0) break
                    off += n
                }
                buf.copyOf(off)
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
}
