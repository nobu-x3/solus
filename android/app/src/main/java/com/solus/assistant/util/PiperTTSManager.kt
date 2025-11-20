package com.solus.assistant.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manages Piper TTS models and speech synthesis using sherpa-onnx
 */
class PiperTTSManager(private val context: Context) {

    data class PiperVoice(
        val id: String,
        val name: String,
        val language: String,
        val quality: String,
        val size: String,
        val modelUrl: String,
        val configUrl: String
    )

    companion object {
        private const val TAG = "PiperTTSManager"
        private const val TTS_MODELS_DIR = "piper_models"
        private const val SAMPLE_RATE = 22050

        val AVAILABLE_VOICES = listOf(
            PiperVoice(
                id = "en_US-lessac-medium",
                name = "Lessac (US English, Medium)",
                language = "en-US",
                quality = "medium",
                size = "63 MB",
                modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
                configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"
            ),
            PiperVoice(
                id = "en_US-amy-medium",
                name = "Amy (US English, Medium)",
                language = "en-US",
                quality = "medium",
                size = "63 MB",
                modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx",
                configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json"
            ),
            PiperVoice(
                id = "en_GB-alan-medium",
                name = "Alan (British English, Medium)",
                language = "en-GB",
                quality = "medium",
                size = "63 MB",
                modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium/en_GB-alan-medium.onnx",
                configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium/en_GB-alan-medium.onnx.json"
            ),
            PiperVoice(
                id = "en_US-lessac-low",
                name = "Lessac (US English, Low - Faster)",
                language = "en-US",
                quality = "low",
                size = "20 MB",
                modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/low/en_US-lessac-low.onnx",
                configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/low/en_US-lessac-low.onnx.json"
            )
        )
    }

    private var tts: OfflineTts? = null
    private var currentVoiceId: String? = null

    /**
     * Check if a voice model is installed
     */
    fun isVoiceInstalled(voiceId: String): Boolean {
        val modelFile = File(context.filesDir, "$TTS_MODELS_DIR/$voiceId.onnx")
        val configFile = File(context.filesDir, "$TTS_MODELS_DIR/$voiceId.onnx.json")
        return modelFile.exists() && configFile.exists()
    }

    /**
     * Download a voice model
     */
    suspend fun downloadVoice(
        voice: PiperVoice,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            DebugLog.d(TAG, "Downloading voice: ${voice.name}")

            val modelsDir = File(context.filesDir, TTS_MODELS_DIR)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val modelFile = File(modelsDir, "${voice.id}.onnx")
            val configFile = File(modelsDir, "${voice.id}.onnx.json")

            // Download model file (this is the large one)
            DebugLog.d(TAG, "Downloading model file from ${voice.modelUrl}")
            downloadFile(voice.modelUrl, modelFile) { progress ->
                onProgress((progress * 0.9).toInt()) // Model is 90% of work
            }

            // Download config file
            DebugLog.d(TAG, "Downloading config file from ${voice.configUrl}")
            downloadFile(voice.configUrl, configFile) { progress ->
                onProgress(90 + (progress * 0.1).toInt()) // Config is 10% of work
            }

            DebugLog.d(TAG, "✓ Voice downloaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to download voice: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Initialize TTS with a specific voice
     */
    fun initialize(voiceId: String): Result<Unit> {
        try {
            if (currentVoiceId == voiceId && tts != null) {
                DebugLog.d(TAG, "TTS already initialized with voice: $voiceId")
                return Result.success(Unit)
            }

            DebugLog.d(TAG, "Initializing TTS with voice: $voiceId")

            val modelPath = File(context.filesDir, "$TTS_MODELS_DIR/$voiceId.onnx").absolutePath
            val configPath = File(context.filesDir, "$TTS_MODELS_DIR/$voiceId.onnx.json").absolutePath

            if (!File(modelPath).exists() || !File(configPath).exists()) {
                return Result.failure(Exception("Voice model not found. Please download it first."))
            }

            // Release old TTS if exists
            tts?.release()

            // Create TTS config
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPath,
                        lexicon = "",
                        tokens = "",
                        dataDir = ""
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                ruleFsts = "",
                maxNumSentences = 1
            )

            tts = OfflineTts(config)
            currentVoiceId = voiceId

            DebugLog.d(TAG, "✓ TTS initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to initialize TTS: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synthesize speech from text and play it
     */
    fun speak(text: String): Result<Unit> {
        try {
            val ttsInstance = tts ?: return Result.failure(Exception("TTS not initialized"))

            DebugLog.d(TAG, "Synthesizing speech: $text")

            // Generate audio
            val audio = ttsInstance.generate(
                text = text,
                sid = 0,
                speed = 1.0f
            )

            if (audio == null || audio.samples.isEmpty()) {
                return Result.failure(Exception("Failed to generate audio"))
            }

            DebugLog.d(TAG, "Generated ${audio.samples.size} audio samples at ${audio.sampleRate} Hz")

            // Play audio
            playAudio(audio.samples, audio.sampleRate)

            Result.success(Unit)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to speak: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Play audio samples
     */
    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        Thread {
            try {
                // Convert float samples to short
                val shortSamples = ShortArray(samples.size) { i ->
                    (samples[i] * Short.MAX_VALUE).toInt().coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    ).toShort()
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()
                audioTrack.write(shortSamples, 0, shortSamples.size)
                audioTrack.stop()
                audioTrack.release()

                DebugLog.d(TAG, "✓ Audio playback completed")
            } catch (e: Exception) {
                DebugLog.e(TAG, "Error playing audio: ${e.message}", e)
            }
        }.start()
    }

    /**
     * Download a file from URL
     */
    private fun downloadFile(
        urlString: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()

        val fileLength = connection.contentLength
        var totalDownloaded = 0L

        connection.getInputStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead

                    if (fileLength > 0) {
                        val progress = ((totalDownloaded * 100) / fileLength).toInt()
                        onProgress(progress)
                    }
                }
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        tts?.release()
        tts = null
        currentVoiceId = null
        DebugLog.d(TAG, "TTS resources released")
    }

    /**
     * Get the directory where models are stored
     */
    fun getModelsDirectory(): File {
        return File(context.filesDir, TTS_MODELS_DIR)
    }
}
