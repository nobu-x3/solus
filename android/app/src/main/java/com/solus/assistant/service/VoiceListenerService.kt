package com.solus.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.solus.assistant.MainActivity
import com.solus.assistant.util.DebugLog
import com.solus.assistant.data.model.ChatRequest
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Foreground service that continuously listens for voice commands
 */
class VoiceListenerService : Service() {

    private val binder = LocalBinder()
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var audioManager: AudioManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isListening = false
    private var conversationId: String? = null
    private var wakeWordEnabled = true
    private var wakeWord = "hey solus"
    private var waitingForCommand = false

    inner class LocalBinder : Binder() {
        fun getService(): VoiceListenerService = this@VoiceListenerService
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.d(TAG, "Service created")

        settingsManager = SettingsManager(this)
        actionExecutor = ActionExecutor(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Ready to listen"))

        // Load settings
        serviceScope.launch {
            launch {
                settingsManager.conversationId.collect { conversationId = it }
            }
            launch {
                settingsManager.wakeWordEnabled.collect { wakeWordEnabled = it }
            }
            launch {
                settingsManager.wakeWord.collect { wakeWord = it.lowercase() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.d(TAG, "Service started")

        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_RESET_CONVERSATION -> resetConversation()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.d(TAG, "Service destroyed")
        stopListening()
        serviceScope.cancel()
    }

    /**
     * Start listening for voice commands
     */
    fun startListening() {
        if (isListening) {
            DebugLog.d(TAG, "Already listening")
            return
        }

        DebugLog.d(TAG, "Starting to listen")
        isListening = true
        waitingForCommand = false
        updateNotification("Listening...")
        initializeSpeechRecognizer()
    }

    /**
     * Stop listening for voice commands
     */
    fun stopListening() {
        if (!isListening) {
            DebugLog.d(TAG, "Not listening")
            return
        }

        DebugLog.d(TAG, "Stopping listening")
        isListening = false
        waitingForCommand = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        updateNotification("Stopped")
    }

    /**
     * Reset conversation (start new conversation)
     */
    private fun resetConversation() {
        serviceScope.launch {
            settingsManager.resetConversation()
            conversationId = null
            updateNotification("Conversation reset")
        }
    }

    /**
     * Initialize speech recognizer
     */
    private fun initializeSpeechRecognizer() {
        DebugLog.d(TAG, "Initializing speech recognizer")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            DebugLog.e(TAG, "❌ Speech recognition NOT AVAILABLE on this device!")
            DebugLog.e(TAG, "This usually means:")
            DebugLog.e(TAG, "  1. You're on an emulator without Google Play Services")
            DebugLog.e(TAG, "  2. Google app / speech services not installed")
            DebugLog.e(TAG, "  3. No internet connection (required for speech recognition)")
            updateNotification("❌ Speech recognition unavailable")
            stopListening()
            return
        }

        DebugLog.d(TAG, "✓ Speech recognition is available")

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        if (speechRecognizer == null) {
            DebugLog.e(TAG, "❌ Failed to create SpeechRecognizer instance!")
            updateNotification("Failed to create recognizer")
            stopListening()
            return
        }

        DebugLog.d(TAG, "✓ SpeechRecognizer created successfully")

        speechRecognizer?.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    DebugLog.d(TAG, "Ready for speech")
                    val status = if (wakeWordEnabled && !waitingForCommand) {
                        "Say \"$wakeWord\""
                    } else {
                        "Listening..."
                    }
                    updateNotification(status)
                }

                override fun onBeginningOfSpeech() {
                    DebugLog.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Volume level changed
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Partial results
                }

                override fun onEndOfSpeech() {
                    DebugLog.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error - Check microphone"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "MICROPHONE PERMISSION DENIED"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error - Internet required for speech recognition"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout - Check internet connection"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected" // Normal, not an error
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Google speech server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)"
                        else -> "Unknown error code: $error"
                    }

                    // Only log actual errors (not "no match" which is normal)
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        DebugLog.e(TAG, "❌ Speech recognition error [$error]: $errorMessage")
                    } else {
                        DebugLog.d(TAG, "No speech detected, continuing to listen...")
                    }

                    // Show important errors in notification
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        updateNotification("❌ Microphone permission required")
                    } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        updateNotification("⚠️ No internet connection")
                    }

                    // Restart listening after a short delay (except for permission errors)
                    if (isListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        serviceScope.launch {
                            delay(1000)
                            if (isListening) {
                                startRecognition()
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        DebugLog.d(TAG, "Recognized: $text")
                        handleRecognizedText(text)
                    }

                    // Continue listening
                    if (isListening) {
                        serviceScope.launch {
                            delay(500)
                            if (isListening) {
                                startRecognition()
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Handle partial results if needed
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Handle events if needed
                }
            })

            startRecognition()
        }
    }

    /**
     * Start speech recognition
     */
    private fun startRecognition() {
        DebugLog.d(TAG, "Starting speech recognition...")

        if (speechRecognizer == null) {
            DebugLog.e(TAG, "❌ Cannot start recognition - SpeechRecognizer is null!")
            return
        }

        // Mute the beep sound by adjusting stream volume
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.ADJUST_MUTE,
            0
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Extended silence tolerance to reduce beeping/restarts
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            DebugLog.d(TAG, "✓ Speech recognition started successfully")
        } catch (e: Exception) {
            DebugLog.e(TAG, "❌ Exception starting recognition: ${e.message}", e)
            updateNotification("Error: ${e.message}")
        }

        // Unmute after a short delay
        serviceScope.launch {
            delay(300)
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.ADJUST_UNMUTE,
                0
            )
        }
    }

    /**
     * Handle recognized text
     */
    private fun handleRecognizedText(text: String) {
        val lowerText = text.lowercase()

        // Check for wake word if enabled
        if (wakeWordEnabled && !waitingForCommand) {
            if (lowerText.contains(wakeWord)) {
                DebugLog.d(TAG, "Wake word detected")

                // Check if command is in the same phrase (after wake word)
                val wakeWordIndex = lowerText.indexOf(wakeWord)
                val commandText = text.substring(wakeWordIndex + wakeWord.length).trim()

                if (commandText.isNotEmpty()) {
                    // Wake word and command in same phrase, process immediately
                    DebugLog.d(TAG, "Command detected with wake word: $commandText")
                    updateNotification("Processing: $commandText")
                    sendToServer(commandText)
                } else {
                    // Only wake word detected, wait for next phrase
                    waitingForCommand = true
                    updateNotification("Listening for command...")
                }
            }
            return
        }

        // Reset waiting state
        if (wakeWordEnabled) {
            waitingForCommand = false
        }

        // Send to server
        updateNotification("Processing: $text")
        sendToServer(text)
    }

    /**
     * Send text to server
     */
    private fun sendToServer(text: String) {
        serviceScope.launch {
            try {
                val baseUrl = settingsManager.serverBaseUrl.first()
                val userId = settingsManager.userId.first()
                val api = RetrofitClient.getInstance(baseUrl)

                DebugLog.d(TAG, "Sending to server: $text")
                updateNotification("Sending to server...")

                val request = ChatRequest(
                    text = text,
                    userId = userId,
                    conversationId = conversationId
                )

                val response = api.chat(request)

                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    if (chatResponse != null) {
                        DebugLog.d(TAG, "Response: ${chatResponse.response}")
                        conversationId = chatResponse.conversationId
                        settingsManager.setConversationId(conversationId)

                        // Execute action if present
                        chatResponse.action?.let { action ->
                            DebugLog.d(TAG, "Executing action: ${action.type}")
                            actionExecutor.executeAction(action)
                        }

                        updateNotification("✓ ${chatResponse.response.take(50)}")

                        // Reset to ready state after showing response
                        serviceScope.launch {
                            delay(3000)
                            if (isListening) {
                                val status = if (wakeWordEnabled) {
                                    "Say \"$wakeWord\""
                                } else {
                                    "Listening..."
                                }
                                updateNotification(status)
                            }
                        }
                    }
                } else {
                    DebugLog.e(TAG, "Server error: ${response.code()} ${response.message()}")
                    updateNotification("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Error sending to server", e)
                updateNotification("Error: ${e.message}")
            }
        }
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Solus voice listener service"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification
     */
    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Solus Assistant")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update notification
     */
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    fun isCurrentlyListening(): Boolean = isListening

    /**
     * Send a text command to the server (for manual text input)
     */
    fun sendTextCommand(text: String) {
        if (text.isBlank()) return
        DebugLog.d(TAG, "Sending text command: $text")
        updateNotification("Processing: $text")
        sendToServer(text)
    }

    companion object {
        private const val TAG = "VoiceListenerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_listener_channel"

        const val ACTION_START_LISTENING = "com.solus.assistant.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.solus.assistant.STOP_LISTENING"
        const val ACTION_RESET_CONVERSATION = "com.solus.assistant.RESET_CONVERSATION"
    }
}
