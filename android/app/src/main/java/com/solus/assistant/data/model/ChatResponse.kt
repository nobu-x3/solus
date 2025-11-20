package com.solus.assistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for /chat endpoint
 */
data class ChatResponse(
    @SerializedName("response")
    val response: String,

    @SerializedName("action")
    val action: ServerAction? = null,

    @SerializedName("conversation_id")
    val conversationId: String
)
