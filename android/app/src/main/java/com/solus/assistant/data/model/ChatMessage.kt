package com.solus.assistant.data.model

/**
 * Chat message for UI display
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isVoiceInput: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
