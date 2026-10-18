package dev.deiv.dear.llm

data class ModelFile(
    val fileName: String,
    val url: String,
    val sizeMB: Int,
)

data class CatalogModel(
    val id: String,
    val displayName: String,
    val model: ModelFile,
    /** Multimodal projector (image + audio input); null for text-only models. */
    val mmproj: ModelFile?,
    val ramHint: String,
    val description: String,
) {
    val files: List<ModelFile>
        get() = listOfNotNull(model, mmproj)

    val totalSizeMB: Int
        get() = files.sumOf { it.sizeMB }
}

object ModelCatalog {
    private const val HF = "https://huggingface.co/ggml-org"

    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B",
            model = ModelFile(
                fileName = "gemma-4-E2B-it-Q4_0.gguf",
                url = "$HF/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_0.gguf",
                sizeMB = 2841,
            ),
            mmproj = ModelFile(
                fileName = "mmproj-gemma-4-E2B-it-Q8_0.gguf",
                url = "$HF/gemma-4-E2B-it-GGUF/resolve/main/mmproj-gemma-4-E2B-it-Q8_0.gguf",
                sizeMB = 557,
            ),
            ramHint = "≥6 GB",
            description = "Recommended. Text, images and audio; light enough for background cataloging.",
        ),
        CatalogModel(
            id = "gemma-4-e4b",
            displayName = "Gemma 4 E4B",
            model = ModelFile(
                fileName = "gemma-4-E4B-it-Q4_0.gguf",
                url = "$HF/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_0.gguf",
                sizeMB = 4591,
            ),
            mmproj = ModelFile(
                fileName = "mmproj-gemma-4-E4B-it-Q8_0.gguf",
                url = "$HF/gemma-4-E4B-it-GGUF/resolve/main/mmproj-gemma-4-E4B-it-Q8_0.gguf",
                sizeMB = 560,
            ),
            ramHint = "≥8 GB",
            description = "Better quality, slower and heavier on RAM and battery.",
        ),
    )

    fun byModelFileName(name: String): CatalogModel? = models.firstOrNull { it.model.fileName == name }

    fun isProjector(name: String): Boolean = models.any { it.mmproj?.fileName == name } || name.startsWith("mmproj")
}
