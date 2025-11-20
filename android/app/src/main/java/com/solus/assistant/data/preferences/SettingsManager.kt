package com.solus.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.solus.assistant.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * DataStore extension for preferences
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "solus_settings")

/**
 * Settings manager using DataStore for persistent storage
 */
class SettingsManager(val context: Context) {

    companion object {
        private val SERVER_HOST = stringPreferencesKey("server_host")
        private val SERVER_PORT = stringPreferencesKey("server_port")
        private val USER_ID = stringPreferencesKey("user_id")
        private val CONVERSATION_ID = stringPreferencesKey("conversation_id")
        private val AUTO_START = booleanPreferencesKey("auto_start")
        private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val WAKE_WORD = stringPreferencesKey("wake_word")
        private val VOSK_MODEL_ID = stringPreferencesKey("vosk_model_id")
        private val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        private val CHAT_HISTORY = stringPreferencesKey("chat_history")

        // Default values
        const val DEFAULT_SERVER_HOST = "http://10.0.2.2" // Emulator localhost
        const val DEFAULT_SERVER_PORT = "8000"
        const val DEFAULT_WAKE_WORD = "hey solus"
        const val DEFAULT_MODEL_ID = "vosk-model-small-en-us-0.15"
    }

    private val gson = Gson()

    /**
     * Get server base URL
     */
    val serverBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        val host = preferences[SERVER_HOST] ?: DEFAULT_SERVER_HOST
        val port = preferences[SERVER_PORT] ?: DEFAULT_SERVER_PORT
        "$host:$port/"
    }

    /**
     * Get server host
     */
    val serverHost: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_HOST] ?: DEFAULT_SERVER_HOST
    }

    /**
     * Get server port
     */
    val serverPort: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_PORT] ?: DEFAULT_SERVER_PORT
    }

    /**
     * Get user ID (generates one if not exists)
     */
    val userId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_ID] ?: run {
            val newUserId = "android_${UUID.randomUUID()}"
            // Save the new user ID
            context.dataStore.edit { it[USER_ID] = newUserId }
            newUserId
        }
    }

    /**
     * Get conversation ID
     */
    val conversationId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CONVERSATION_ID]
    }

    /**
     * Get auto-start preference
     */
    val autoStart: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_START] ?: false
    }

    /**
     * Get wake word enabled preference
     */
    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WAKE_WORD_ENABLED] ?: true
    }

    /**
     * Get wake word
     */
    val wakeWord: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WAKE_WORD] ?: DEFAULT_WAKE_WORD
    }

    /**
     * Get selected Vosk model ID
     */
    val voskModelId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOSK_MODEL_ID] ?: DEFAULT_MODEL_ID
    }

    /**
     * Check if first run is complete
     */
    val isFirstRunComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FIRST_RUN_COMPLETE] ?: false
    }

    /**
     * Update server host
     */
    suspend fun setServerHost(host: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_HOST] = host
        }
    }

    /**
     * Update server port
     */
    suspend fun setServerPort(port: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_PORT] = port
        }
    }

    /**
     * Update user ID
     */
    suspend fun setUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = userId
        }
    }

    /**
     * Update conversation ID
     */
    suspend fun setConversationId(conversationId: String?) {
        context.dataStore.edit { preferences ->
            if (conversationId != null) {
                preferences[CONVERSATION_ID] = conversationId
            } else {
                preferences.remove(CONVERSATION_ID)
            }
        }
    }

    /**
     * Update auto-start preference
     */
    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START] = enabled
        }
    }

    /**
     * Update wake word enabled preference
     */
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WAKE_WORD_ENABLED] = enabled
        }
    }

    /**
     * Update wake word
     */
    suspend fun setWakeWord(word: String) {
        context.dataStore.edit { preferences ->
            preferences[WAKE_WORD] = word
        }
    }

    /**
     * Update Vosk model ID
     */
    suspend fun setVoskModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[VOSK_MODEL_ID] = modelId
        }
    }

    /**
     * Mark first run as complete
     */
    suspend fun setFirstRunComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_RUN_COMPLETE] = complete
        }
    }

    /**
     * Reset conversation (starts a new conversation)
     */
    suspend fun resetConversation() {
        setConversationId(null)
        clearChatHistory()
    }

    /**
     * Get all settings as a map (for debugging)
     */
    suspend fun getAllSettings(): Map<String, Any?> {
        val preferences = context.dataStore.data.map { it.asMap() }
        return mapOf(
            "serverHost" to serverHost,
            "serverPort" to serverPort,
            "userId" to userId,
            "conversationId" to conversationId,
            "autoStart" to autoStart,
            "wakeWordEnabled" to wakeWordEnabled,
            "wakeWord" to wakeWord
        )
    }

    /**
     * Get chat history
     */
    val chatHistory: Flow<List<ChatMessage>> = context.dataStore.data.map { preferences ->
        val json = preferences[CHAT_HISTORY] ?: "[]"
        try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("SettingsManager", "Error parsing chat history", e)
            emptyList()
        }
    }

    /**
     * Save chat history
     */
    suspend fun saveChatHistory(messages: List<ChatMessage>) {
        context.dataStore.edit { preferences ->
            val json = gson.toJson(messages)
            preferences[CHAT_HISTORY] = json
        }
    }

    /**
     * Add message to chat history
     */
    suspend fun addMessageToHistory(message: ChatMessage) {
        context.dataStore.edit { preferences ->
            val json = preferences[CHAT_HISTORY] ?: "[]"
            val messages = try {
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val updatedMessages = messages + message
            preferences[CHAT_HISTORY] = gson.toJson(updatedMessages)
        }
    }

    /**
     * Clear chat history
     */
    suspend fun clearChatHistory() {
        context.dataStore.edit { preferences ->
            preferences[CHAT_HISTORY] = "[]"
        }
    }
}
