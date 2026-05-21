package dev.deiv.dear.llm

data class CatalogModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeMB: Int,
    val ramHint: String,
    val description: String,
)

object ModelCatalog {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "llama-3.2-1b",
            displayName = "Llama 3.2 1B (Q4)",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeMB = 808,
            ramHint = "≥3 GB",
            description = "Smallest. Fast. OK quality.",
        ),
        CatalogModel(
            id = "qwen2.5-1.5b",
            displayName = "Qwen 2.5 1.5B (Q4)",
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sizeMB = 986,
            ramHint = "≥4 GB",
            description = "Balanced size/quality.",
        ),
        CatalogModel(
            id = "gemma-2-2b",
            displayName = "Gemma 2 2B IT (Q4)",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            sizeMB = 1710,
            ramHint = "≥5 GB",
            description = "Stronger reasoning.",
        ),
        CatalogModel(
            id = "phi-4-mini",
            displayName = "Phi-4 Mini Instruct (Q4)",
            fileName = "Phi-4-mini-instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf",
            sizeMB = 2492,
            ramHint = "≥6 GB",
            description = "Recommended. Best quality.",
        ),
    )
}
