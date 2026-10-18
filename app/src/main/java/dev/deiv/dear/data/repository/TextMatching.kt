package dev.deiv.dear.data.repository

import java.text.Normalizer

/**
 * Plain keyword matching shared by the collection filter and chat retrieval, until FTS and
 * embeddings take over: lowercase, accent-insensitive, with a crude stem for Italian endings.
 */
object TextMatching {
    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    /** Meaningful words of a question: no stopwords, no "did I save" filler, at least 3 letters. */
    fun queryTerms(question: String): List<String> =
        normalize(question)
            .split(NON_WORD)
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()

    /** True when [haystack] (already normalized) contains [term] or its stem: "fotografie" finds "fotografia". */
    fun matches(haystack: String, term: String): Boolean =
        term in haystack || (term.length > 5 && term.take(term.length - 2) in haystack)

    private val STOPWORDS = setOf(
        // Italian
        "il", "lo", "la", "gli", "le", "un", "uno", "una", "del", "dello", "della", "dei", "degli", "delle",
        "nel", "nello", "nella", "nei", "negli", "nelle", "sul", "sullo", "sulla", "sui", "sugli", "sulle",
        "con", "per", "tra", "fra", "che", "chi", "cui", "non", "come", "dove", "quando", "quale", "quali",
        "cosa", "cose", "qualcosa", "qualche", "quello", "quella", "quelli", "quelle", "questo", "questa",
        "questi", "queste", "sono", "era", "ero", "eri", "erano", "stato", "stata", "stati", "state",
        "avevo", "aveva", "avevi", "avevamo", "abbiamo", "hanno", "hai", "ho", "ha", "avere", "essere",
        "salvato", "salvata", "salvati", "salvate", "salvare", "salvo", "messo", "messa", "tenuto",
        "trovami", "trova", "trovare", "cerca", "cercami", "mostrami", "dimmi", "ricordi", "ricordo",
        "anche", "ancora", "molto", "poco", "tutto", "tutti", "tutte", "mio", "mia", "miei", "mie",
        "tuo", "tua", "suo", "sua", "loro", "nostro", "nostra", "piu", "meno", "gia", "solo", "mai",
        "sempre", "allora", "quindi", "pero", "oppure", "ogni", "alcuni", "alcune", "qui", "qua",
        // English
        "the", "and", "for", "with", "that", "this", "these", "those", "what", "which", "who", "where",
        "when", "how", "did", "does", "have", "has", "had", "was", "were", "are", "you", "your", "mine",
        "save", "saved", "something", "anything", "find", "show", "tell", "about", "any", "some",
    )
}
