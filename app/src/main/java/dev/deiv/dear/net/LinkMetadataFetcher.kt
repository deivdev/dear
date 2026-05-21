package dev.deiv.dear.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val author: String? = null,
    val type: String? = null,
)

object LinkMetadataFetcher {
    private const val TAG = "LinkMetadataFetcher"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Dear/1.0"
    private const val MAX_BYTES = 256 * 1024 // 256KB cap
    private const val TIMEOUT_MS = 8000

    suspend fun fetch(url: String): LinkMetadata? = withContext(Dispatchers.IO) {
        // Try oEmbed first for known providers (no HTML scrape needed)
        oembedFor(url)?.let { return@withContext it }

        runCatching { fetchHtml(url) }
            .onFailure { Log.w(TAG, "Failed to fetch $url: ${it.message}") }
            .getOrNull()
            ?.let { parseHtml(it) }
    }

    private fun fetchHtml(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val ct = conn.contentType ?: ""
            if (!ct.contains("html", ignoreCase = true)) return null
            return conn.inputStream.use { stream ->
                val buf = ByteArray(MAX_BYTES)
                var off = 0
                while (off < buf.size) {
                    val n = stream.read(buf, off, buf.size - off)
                    if (n <= 0) break
                    off += n
                }
                // Best-effort charset from content-type; default UTF-8.
                val charset = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
                    .find(ct)?.groupValues?.get(1) ?: "UTF-8"
                String(buf, 0, off, charset(charset))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseHtml(html: String): LinkMetadata {
        val og = ogTags(html)
        val title = og["og:title"]
            ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim()
        val description = og["og:description"]
            ?: metaName(html, "description")
        val siteName = og["og:site_name"]
        val author = metaName(html, "author")
        val type = og["og:type"]
        return LinkMetadata(
            title = title?.let { decodeEntities(it).take(300) },
            description = description?.let { decodeEntities(it).take(500) },
            siteName = siteName?.let { decodeEntities(it).take(100) },
            author = author?.let { decodeEntities(it).take(100) },
            type = type,
        )
    }

    private fun ogTags(html: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val regex = Regex(
            "<meta[^>]+property\\s*=\\s*[\"']([^\"']+)[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
            RegexOption.IGNORE_CASE,
        )
        regex.findAll(html).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            if (key.startsWith("og:") && key !in out) out[key] = value
        }
        // Also accept content-first ordering
        val regex2 = Regex(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+property\\s*=\\s*[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        )
        regex2.findAll(html).forEach { m ->
            val key = m.groupValues[2].lowercase()
            val value = m.groupValues[1]
            if (key.startsWith("og:") && key !in out) out[key] = value
        }
        return out
    }

    private fun metaName(html: String, name: String): String? {
        val r = Regex(
            "<meta[^>]+name\\s*=\\s*[\"']${Regex.escape(name)}[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
            RegexOption.IGNORE_CASE,
        )
        return r.find(html)?.groupValues?.get(1)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()

    private fun oembedFor(url: String): LinkMetadata? {
        val endpoint = when {
            url.contains("youtube.com/watch") || url.contains("youtu.be/") ->
                "https://www.youtube.com/oembed?url=${urlEncode(url)}&format=json"
            url.contains("vimeo.com/") ->
                "https://vimeo.com/api/oembed.json?url=${urlEncode(url)}"
            else -> null
        } ?: return null

        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                LinkMetadata(
                    title = json.optString("title").takeIf { it.isNotBlank() },
                    description = null,
                    siteName = json.optString("provider_name").takeIf { it.isNotBlank() },
                    author = json.optString("author_name").takeIf { it.isNotBlank() },
                    type = json.optString("type").takeIf { it.isNotBlank() },
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    @Suppress("unused")
    private fun urlDecode(s: String): String =
        URLDecoder.decode(s, "UTF-8")
}
