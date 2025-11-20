package com.solus.assistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * Server action model
 */
data class ServerAction(
    @SerializedName("type")
    val type: String,

    @SerializedName("params")
    val params: Map<String, Any>
)

/**
 * Action types supported by the server
 */
object ActionType {
    const val TODO_ADD = "todo_add"
    const val REMINDER_SET = "reminder_set"
    const val NOTE_CREATE = "note_create"
    const val APP_OPEN = "app_open"
    const val CALL_MAKE = "call_make"
    const val MESSAGE_SEND = "message_send"
}

/**
 * Action parameters for todo_add
 */
data class TodoParams(
    val title: String,
    val description: String?,
    val priority: String?, // "low", "medium", "high"
    val dueDate: String? // ISO datetime
)

/**
 * Action parameters for reminder_set
 */
data class ReminderParams(
    val title: String,
    val time: String, // ISO datetime
    val repeat: String? // "once", "daily", "weekly"
)

/**
 * Action parameters for note_create
 */
data class NoteParams(
    val title: String,
    val content: String
)

/**
 * Action parameters for app_open
 */
data class AppOpenParams(
    val packageName: String
)

/**
 * Action parameters for call_make
 */
data class CallParams(
    val phoneNumber: String
)

/**
 * Action parameters for message_send
 */
data class MessageParams(
    val phoneNumber: String,
    val message: String
)
