package com.solus.assistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for /chat endpoint
 */
data class ChatRequest(
    @SerializedName("text")
    val text: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("conversation_id")
    val conversationId: String? = null
)
