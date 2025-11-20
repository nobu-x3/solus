package com.solus.assistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for /health endpoint
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("model_loaded")
    val modelLoaded: Boolean,

    @SerializedName("memory_count")
    val memoryCount: Int,

    @SerializedName("embedding_dim")
    val embeddingDim: Int
)
