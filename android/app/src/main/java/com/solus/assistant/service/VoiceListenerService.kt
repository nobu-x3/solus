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
import com.solus.assistant.util.DebugLog
import com.solus.assistant.data.model.ChatRequest
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    private var wakeWord = "hey solus"

    inner class LocalBinder : Binder() {
        fun getService(): VoiceListenerService = this@VoiceListenerService
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.d(TAG, "Service created")

        settingsManager = SettingsManager(this)
        actionExecutor = ActionExecutor(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Ready to listen"))

        // Load settings
        serviceScope.launch {
            settingsManager.conversationId.collect { conversationId = it }
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
        updateNotification("Initializing wake word...")

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
            DebugLog.d(TAG, "Initializing Vosk...")

            // Check if model exists, if not download it
            val modelPath = File(filesDir, "vosk-model-small-en-us-0.15")

            if (!modelPath.exists()) {
                withContext(Dispatchers.Main) {
                    updateNotification("Downloading wake word model...")
                }
                DebugLog.d(TAG, "Model not found, need to download")

                // Model needs to be downloaded - see instructions below
                withContext(Dispatchers.Main) {
                    DebugLog.e(TAG, "⚠️ Vosk model not found!")
                    DebugLog.e(TAG, "Download model from: https://alphacephei.com/vosk/models")
                    DebugLog.e(TAG, "Get 'vosk-model-small-en-us-0.15.zip' (40MB)")
                    DebugLog.e(TAG, "Extract and place in: ${filesDir.absolutePath}/")
                    updateNotification("❌ Wake word model required - see logs")
                    stopListening()
                }
                return@withContext
            }

            DebugLog.d(TAG, "Loading Vosk model from: ${modelPath.absolutePath}")
            val model = Model(modelPath.absolutePath)

            withContext(Dispatchers.Main) {
                DebugLog.d(TAG, "✓ Vosk model loaded")
                updateNotification("Say '$wakeWord'")

                val listener = object : VoskRecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        hypothesis?.let {
                            DebugLog.d(TAG, "Partial: $it")
                            checkForWakeWord(it)
                        }
                    }

                    override fun onResult(hypothesis: String?) {
                        hypothesis?.let {
                            DebugLog.d(TAG, "Result: $it")
                            checkForWakeWord(it)
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        hypothesis?.let {
                            DebugLog.d(TAG, "Final: $it")
                            checkForWakeWord(it)
                        }
                    }

                    override fun onError(exception: Exception?) {
                        DebugLog.e(TAG, "Vosk error: ${exception?.message}", exception)
                    }

                    override fun onTimeout() {
                        DebugLog.d(TAG, "Vosk timeout - continuing")
                    }
                }

                voskService = SpeechService(Recognizer(model, 16000.0f), 16000.0f)
                voskService?.startListening(listener)
                DebugLog.d(TAG, "✓ Vosk wake word detection started")
            }
        }
    }

    /**
     * Check if wake word was detected in Vosk results
     */
    private fun checkForWakeWord(hypothesis: String) {
        if (isProcessingCommand) return

        val lowerHypothesis = hypothesis.lowercase()
        if (lowerHypothesis.contains(wakeWord)) {
            DebugLog.d(TAG, "✓ Wake word detected!")
            onWakeWordDetected()
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
                    hypothesis?.let { checkForWakeWord(it) }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { checkForWakeWord(it) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let { checkForWakeWord(it) }
                }

                override fun onError(exception: Exception?) {
                    DebugLog.e(TAG, "Vosk error: ${exception?.message}", exception)
                }

                override fun onTimeout() {
                    DebugLog.d(TAG, "Vosk timeout - continuing")
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
