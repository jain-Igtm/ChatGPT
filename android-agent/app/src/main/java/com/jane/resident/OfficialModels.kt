package com.jane.resident

object OfficialModels {
    val GEMMA_4_E4B = OfficialModel(
        id = "gemma-4-e4b-it",
        displayName = "Gemma 4 E4B",
        repository = "litert-community/gemma-4-E4B-it-litert-lm",
        filename = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        approximateBytes = 3_654L * 1024L * 1024L,
        role = "mind",
    )

    val GEMMA_4_E2B = OfficialModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B",
        repository = "litert-community/gemma-4-E2B-it-litert-lm",
        filename = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approximateBytes = 2_583L * 1024L * 1024L,
        role = "mind",
    )
}

data class OfficialModel(
    val id: String,
    val displayName: String,
    val repository: String,
    val filename: String,
    val downloadUrl: String,
    val approximateBytes: Long,
    val role: String,
)
