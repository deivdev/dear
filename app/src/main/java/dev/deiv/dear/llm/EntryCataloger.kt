package dev.deiv.dear.llm

import android.util.Log
import dev.deiv.dear.data.model.Entry
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.net.LinkMetadataFetcher
import kotlinx.coroutines.flow.toList
import org.json.JSONObject

data class CatalogResult(
    val tags: List<String>,
    val summary: String,
)

class EntryCataloger(private val model: LlamaModel?) {

    suspend fun catalog(entry: Entry): CatalogResult? {
        val m = model ?: return null
        val enriched = enrich(entry)
        val input = renderInput(enriched) ?: return null
        val prompt = buildPrompt(input)

        val raw = runCatching { m.generate(prompt, maxTokens = 256).toList().joinToString("") }
            .getOrElse {
                Log.e(TAG, "Generation failed", it)
                return null
            }

        return parse(raw)
    }

    private suspend fun enrich(entry: Entry): Entry {
        if (entry.type != EntryType.LINK) return entry
        val url = entry.textContent?.trim() ?: return entry
        val meta = LinkMetadataFetcher.fetch(url) ?: return entry
        val enriched = buildString {
            append("url: ").append(url)
            meta.title?.let { append("\ntitle: ").append(it) }
            meta.siteName?.let { append("\nsite: ").append(it) }
            meta.author?.let { append("\nauthor: ").append(it) }
            meta.type?.let { append("\ntype: ").append(it) }
            meta.description?.let { append("\ndescription: ").append(it) }
        }
        return entry.copy(textContent = enriched)
    }

    private fun renderInput(entry: Entry): String? {
        val text = entry.textContent?.trim()
        return when (entry.type) {
            EntryType.TEXT, EntryType.LINK -> text?.takeIf { it.isNotBlank() }
            EntryType.IMAGE, EntryType.VIDEO, EntryType.AUDIO, EntryType.FILE -> {
                // Text-only fallback for media. Vision/STT replace this in phase 3/4.
                val parts = listOfNotNull(
                    entry.originalName?.let { "filename: $it" },
                    entry.mimeType?.let { "mime: $it" },
                    entry.sourceApp?.let { "source: $it" },
                )
                parts.joinToString(", ").takeIf { it.isNotBlank() }
            }
        }
    }

    private fun buildPrompt(input: String): String = """<|system|>
You catalog personal content. Given input, output ONLY a JSON object with two fields:
- "tags": array of 2-5 short lowercase tags (single words or hyphenated). No spaces.
- "summary": one sentence, max 20 words.
Output JSON only, no commentary.<|end|>
<|user|>
$input<|end|>
<|assistant|>
"""

    private fun parse(raw: String): CatalogResult? {
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            Log.w(TAG, "No JSON in response: $raw")
            return null
        }
        val json = raw.substring(jsonStart, jsonEnd + 1)
        return runCatching {
            val obj = JSONObject(json)
            val tagsArr = obj.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        tagsArr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
                    }
                }
            }
            val summary = obj.optString("summary", "").trim()
            CatalogResult(tags = tags, summary = summary)
        }.getOrElse {
            Log.w(TAG, "JSON parse failed: $json", it)
            null
        }
    }

    companion object {
        private const val TAG = "EntryCataloger"
    }
}
