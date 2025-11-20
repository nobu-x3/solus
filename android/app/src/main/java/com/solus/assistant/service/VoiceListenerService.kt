package com.solus.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.solus.assistant.MainActivity
import com.solus.assistant.util.BeepGenerator
import com.solus.assistant.util.DebugLog
import com.solus.assistant.util.VoskModelDownloader
import com.solus.assistant.data.model.ChatRequest
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.*

/**
 * Foreground service with FREE local wake word detection using Vosk
 * - Completely free, no API keys
 * - Runs entirely on-device
 * - No beeping!
 */
class VoiceListenerService : Service() {

    private val binder = LocalBinder()
    private var voskService: SpeechService? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var actionExecutor: ActionExecutor
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isListening = false
    private var conversationId: String? = null
    private var isProcessingCommand = false
    private var wakeWord = "hey solus" // Loaded from settings
    private var modelId = SettingsManager.DEFAULT_MODEL_ID // Loaded from settings

    private var onCommandRecognizedCallback: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceListenerService = this@VoiceListenerService
    }

    fun setCommandRecognizedCallback(callback: (String) -> Unit) {
        onCommandRecognizedCallback = callback
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.d(TAG, "Service created")

        settingsManager = SettingsManager(this)
        actionExecutor = ActionExecutor(this)

        createNotificationChannel()
        // Don't call startForeground() here - Android 14 requires user interaction first

        // Load settings
        serviceScope.launch {
            launch {
                settingsManager.conversationId.collect { conversationId = it }
            }
            launch {
                settingsManager.wakeWord.collect {
                    wakeWord = it
                    DebugLog.d(TAG, "Wake word updated to: '$wakeWord'")
                }
            }
            launch {
                settingsManager.voskModelId.collect { modelId = it }
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
     * Start listening for wake word using Vosk
     */
    fun startListening() {
        if (isListening) {
            DebugLog.d(TAG, "Already listening")
            return
        }

        DebugLog.d(TAG, "Starting Vosk wake word detection")
        isListening = true

        // Start as foreground service (Android 14 requires this happens when user initiates)
        startForeground(NOTIFICATION_ID, createNotification("Initializing wake word..."))

        serviceScope.launch(Dispatchers.IO) {
            try {
                initializeVosk()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Failed to initialize Vosk: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateNotification("Error: ${e.message}")
                    stopListening()
                }
            }
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        if (!isListening) {
            DebugLog.d(TAG, "Not listening")
            return
        }

        DebugLog.d(TAG, "Stopping listening")
        isListening = false
        isProcessingCommand = false // Reset this flag!
        voskService?.stop()
        voskService?.shutdown()
        voskService = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        updateNotification("Stopped")
    }

    /**
     * Reset conversation
     */
    private fun resetConversation() {
        serviceScope.launch {
            settingsManager.resetConversation()
            conversationId = null
            updateNotification("Conversation reset")
        }
    }

    /**
     * Initialize Vosk wake word detection
     */
    private suspend fun initializeVosk() {
        withContext(Dispatchers.IO) {
            DebugLog.d(TAG, "Initializing Vosk with model: $modelId")

            // Check if model is installed
            val modelPath = VoskModelDownloader.getModelPath(this@VoiceListenerService, modelId)

            if (!VoskModelDownloader.isModelInstalled(this@VoiceListenerService, modelId)) {
                DebugLog.d(TAG, "Model not found, downloading...")

                // Find the model in available models
                val modelToDownload = VoskModelDownloader.AVAILABLE_MODELS.find { it.id == modelId }

                if (modelToDownload == null) {
                    withContext(Dispatchers.Main) {
                        DebugLog.e(TAG, "❌ Model $modelId not found in available models!")
                        updateNotification("❌ Invalid model ID")
                        stopListening()
                    }
                    return@withContext
                }

                // Download the model
                withContext(Dispatchers.Main) {
                    updateNotification("Downloading ${modelToDownload.name} (${modelToDownload.size})...")
                }

                val result = VoskModelDownloader.downloadModel(
                    context = this@VoiceListenerService,
                    model = modelToDownload
                ) { progress ->
                    serviceScope.launch(Dispatchers.Main) {
                        updateNotification("Downloading ${modelToDownload.name}: $progress%")
                    }
                }

                if (result.isFailure) {
                    withContext(Dispatchers.Main) {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        DebugLog.e(TAG, "❌ Failed to download model: $error")
                        updateNotification("❌ Download failed: $error")
                        stopListening()
                    }
                    return@withContext
                }

                DebugLog.d(TAG, "✓ Model downloaded successfully")
            }

            DebugLog.d(TAG, "Loading Vosk model from: ${modelPath.absolutePath}")
            val model = Model(modelPath.absolutePath)

            withContext(Dispatchers.Main) {
                DebugLog.d(TAG, "✓ Vosk model loaded")
                updateNotification("Say '$wakeWord'")

                val listener = object : VoskRecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        // Don't log or process partials - too spammy
                    }

                    override fun onResult(hypothesis: String?) {
                        hypothesis?.let {
                            val text = parseVoskJson(it)
                            if (text != null) {
                                DebugLog.d(TAG, "Result: '$text'")
                                checkForWakeWord(it)
                            }
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        hypothesis?.let {
                            val text = parseVoskJson(it)
                            if (text != null) {
                                DebugLog.d(TAG, "Final: '$text'")
                                checkForWakeWord(it)
                            }
                        }
                    }

                    override fun onError(exception: Exception?) {
                        DebugLog.e(TAG, "Vosk error: ${exception?.message}", exception)
                    }

                    override fun onTimeout() {
                        // Don't log timeout - too spammy
                    }
                }

                voskService = SpeechService(Recognizer(model, 16000.0f), 16000.0f)
                voskService?.startListening(listener)
                DebugLog.d(TAG, "✓ Vosk wake word detection started")
            }
        }
    }

    /**
     * Parse Vosk JSON result to extract text
     * Returns null if no text or empty text
     */
    private fun parseVoskJson(jsonString: String): String? {
        return try {
            val json = JSONObject(jsonString)
            // Try "text" field first (final/result), then "partial" field
            val text = json.optString("text", null) ?: json.optString("partial", null)
            if (text.isNullOrBlank()) null else text
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to parse Vosk JSON: ${e.message}")
            null
        }
    }

    /**
     * Check if wake word was detected in Vosk results
     */
    private fun checkForWakeWord(hypothesis: String) {
        if (isProcessingCommand) return

        // Parse JSON to extract actual text
        val text = parseVoskJson(hypothesis) ?: return

        val lowerText = text.lowercase()
        DebugLog.d(TAG, "Checking text: '$text' for wake word: '$wakeWord'")

        val wakeWordLower = wakeWord.lowercase()
        if (lowerText.contains(wakeWordLower)) {
            DebugLog.d(TAG, "✓ Wake word detected!")

            // Play beep immediately
            BeepGenerator.playWakeWordBeep()

            // Extract command text after wake word
            val wakeWordIndex = lowerText.indexOf(wakeWordLower)
            val commandText = text.substring(wakeWordIndex + wakeWord.length).trim()

            if (commandText.isNotEmpty()) {
                // User said "solus what's the weather" - process immediately
                DebugLog.d(TAG, "Command detected in same utterance: '$commandText'")
                isProcessingCommand = true
                voskService?.stop()

                // Play request sent beep
                BeepGenerator.playRequestSentBeep()

                // Send command to callback
                onCommandRecognizedCallback?.invoke(commandText)
            } else {
                // User said just "solus" - wait for command via Google Speech
                onWakeWordDetected()
            }
        }
    }

    /**
     * Called when wake word is detected - activate command recognition
     */
    private fun onWakeWordDetected() {
        if (isProcessingCommand) {
            DebugLog.d(TAG, "Already processing command")
            return
        }

        isProcessingCommand = true
        updateNotification("Wake word detected!")

        // Stop Vosk temporarily
        voskService?.stop()

        // Activate Google SpeechRecognizer for command
        initializeSpeechRecognizer()
    }

    /**
     * Initialize Google SpeechRecognizer for command (after wake word)
     */
    private fun initializeSpeechRecognizer() {
        DebugLog.d(TAG, "Starting command recognition...")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            DebugLog.e(TAG, "❌ Speech recognition not available")
            updateNotification("❌ Speech recognition unavailable")
            returnToWakeWordListening()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        if (speechRecognizer == null) {
            DebugLog.e(TAG, "❌ Failed to create SpeechRecognizer")
            returnToWakeWordListening()
            return
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                DebugLog.d(TAG, "Ready for command")
                updateNotification("Listening for command...")
            }

            override fun onBeginningOfSpeech() {
                DebugLog.d(TAG, "Command speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                DebugLog.d(TAG, "Command speech ended")
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        DebugLog.d(TAG, "No command detected")
                    }
                    else -> {
                        DebugLog.e(TAG, "Command recognition error: $error")
                    }
                }
                returnToWakeWordListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    DebugLog.d(TAG, "Command recognized: $command")

                    // Play request sent beep
                    BeepGenerator.playRequestSentBeep()

                    // Send to callback
                    onCommandRecognizedCallback?.invoke(command)

                    updateNotification("Processing: $command")
                    sendToServer(command)
                } else {
                    returnToWakeWordListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startCommandRecognition()
    }

    /**
     * Start command recognition
     */
    private fun startCommandRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            DebugLog.d(TAG, "✓ Command recognition started")
        } catch (e: Exception) {
            DebugLog.e(TAG, "❌ Exception starting command recognition: ${e.message}", e)
            returnToWakeWordListening()
        }
    }

    /**
     * Return to wake word listening
     */
    private fun returnToWakeWordListening() {
        DebugLog.d(TAG, "Returning to wake word listening")
        isProcessingCommand = false
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (isListening) {
            updateNotification("Say '$wakeWord'")

            // Restart Vosk listening
            voskService?.startListening(object : VoskRecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    // Don't log or process partials - too spammy
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseVoskJson(it)
                        if (text != null) {
                            DebugLog.d(TAG, "Result: '$text'")
                            checkForWakeWord(it)
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseVoskJson(it)
                        if (text != null) {
                            DebugLog.d(TAG, "Final: '$text'")
                            checkForWakeWord(it)
                        }
                    }
                }

                override fun onError(exception: Exception?) {
                    DebugLog.e(TAG, "Vosk error: ${exception?.message}", exception)
                }

                override fun onTimeout() {
                    // Don't log timeout - too spammy
                }
            })
        }
    }

    /**
     * Send command to server
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

                        // Return to wake word listening
                        serviceScope.launch {
                            delay(3000)
                            if (isListening) {
                                returnToWakeWordListening()
                            }
                        }
                    }
                } else {
                    DebugLog.e(TAG, "Server error: ${response.code()} ${response.message()}")
                    updateNotification("Server error: ${response.code()}")
                    returnToWakeWordListening()
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Error sending to server", e)
                updateNotification("Error: ${e.message}")
                returnToWakeWordListening()
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
