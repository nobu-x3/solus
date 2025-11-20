package com.solus.assistant.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * Manages Android's built-in Text-to-Speech functionality
 */
class AndroidTTSManager(private val context: Context) {

    data class Voice(
        val id: String,
        val name: String,
        val locale: Locale
    )

    companion object {
        private const val TAG = "AndroidTTSManager"

        val AVAILABLE_VOICES = listOf(
            Voice("en-US", "English (US)", Locale.US),
            Voice("en-GB", "English (UK)", Locale.UK),
            Voice("en-AU", "English (Australia)", Locale("en", "AU")),
            Voice("en-IN", "English (India)", Locale("en", "IN"))
        )
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentVoiceId: String? = null

    /**
     * Initialize TTS engine (suspend function that waits for initialization)
     */
    suspend fun initialize(voiceId: String = "en-US"): Result<Unit> {
        return try {
            DebugLog.d(TAG, "Initializing TTS with voice: $voiceId")

            // If already initialized with this voice, skip
            if (isInitialized && currentVoiceId == voiceId && tts != null) {
                DebugLog.d(TAG, "TTS already initialized with voice: $voiceId")
                return Result.success(Unit)
            }

            // Release old instance if exists
            release()

            // Wait for TTS initialization using coroutine
            suspendCancellableCoroutine { continuation ->
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val voice = AVAILABLE_VOICES.find { it.id == voiceId }
                        if (voice != null) {
                            val result = tts?.setLanguage(voice.locale)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                DebugLog.e(TAG, "Language not supported: ${voice.locale}")
                                isInitialized = false
                                continuation.resume(Result.failure(Exception("Language not supported")))
                            } else {
                                DebugLog.d(TAG, "✓ TTS initialized successfully")
                                isInitialized = true
                                currentVoiceId = voiceId

                                // Set speech rate slightly faster
                                tts?.setSpeechRate(1.1f)

                                // Set utterance progress listener
                                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        DebugLog.d(TAG, "TTS started speaking")
                                    }

                                    override fun onDone(utteranceId: String?) {
                                        DebugLog.d(TAG, "TTS finished speaking")
                                    }

                                    override fun onError(utteranceId: String?) {
                                        DebugLog.e(TAG, "TTS error occurred")
                                    }
                                })

                                continuation.resume(Result.success(Unit))
                            }
                        } else {
                            DebugLog.e(TAG, "Voice not found: $voiceId")
                            isInitialized = false
                            continuation.resume(Result.failure(Exception("Voice not found")))
                        }
                    } else {
                        DebugLog.e(TAG, "TTS initialization failed with status: $status")
                        isInitialized = false
                        continuation.resume(Result.failure(Exception("TTS init failed")))
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to initialize TTS: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Speak text
     */
    fun speak(text: String): Result<Unit> {
        return try {
            if (!isInitialized || tts == null) {
                DebugLog.w(TAG, "TTS not initialized, cannot speak")
                return Result.failure(Exception("TTS not initialized"))
            }

            DebugLog.d(TAG, "Speaking: $text")

            val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utteranceId")

            if (result == TextToSpeech.SUCCESS) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("TTS speak failed with code: $result"))
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to speak: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if TTS is speaking
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    /**
     * Release resources
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        currentVoiceId = null
        DebugLog.d(TAG, "TTS resources released")
    }

    /**
     * Check if a voice is available (always true for Android TTS)
     */
    fun isVoiceInstalled(voiceId: String): Boolean {
        // Android TTS voices are built-in, always available
        return AVAILABLE_VOICES.any { it.id == voiceId }
    }
}
