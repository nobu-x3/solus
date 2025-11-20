package com.solus.assistant.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and extracts Vosk speech recognition models
 */
object VoskModelDownloader {

    data class VoskModel(
        val id: String,
        val name: String,
        val language: String,
        val size: String,
        val url: String
    )

    val AVAILABLE_MODELS = listOf(
        VoskModel(
            id = "vosk-model-small-en-us-0.15",
            name = "English (US)",
            language = "en-US",
            size = "40 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        ),
        VoskModel(
            id = "vosk-model-small-en-in-0.4",
            name = "English (India)",
            language = "en-IN",
            size = "36 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
        ),
        VoskModel(
            id = "vosk-model-small-cn-0.22",
            name = "Chinese",
            language = "zh-CN",
            size = "42 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        ),
        VoskModel(
            id = "vosk-model-small-ru-0.22",
            name = "Russian",
            language = "ru-RU",
            size = "45 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        ),
        VoskModel(
            id = "vosk-model-small-fr-0.22",
            name = "French",
            language = "fr-FR",
            size = "41 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
        ),
        VoskModel(
            id = "vosk-model-small-de-0.15",
            name = "German",
            language = "de-DE",
            size = "45 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
        ),
        VoskModel(
            id = "vosk-model-small-es-0.42",
            name = "Spanish",
            language = "es-ES",
            size = "39 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        ),
        VoskModel(
            id = "vosk-model-small-pt-0.3",
            name = "Portuguese",
            language = "pt-PT",
            size = "31 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
        ),
        VoskModel(
            id = "vosk-model-small-tr-0.3",
            name = "Turkish",
            language = "tr-TR",
            size = "35 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
        ),
        VoskModel(
            id = "vosk-model-small-ja-0.22",
            name = "Japanese",
            language = "ja-JP",
            size = "48 MB",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
        )
    )

    /**
     * Check if a model is already installed
     */
    fun isModelInstalled(context: Context, modelId: String): Boolean {
        val modelPath = File(context.filesDir, modelId)
        return modelPath.exists() && modelPath.isDirectory && modelPath.listFiles()?.isNotEmpty() == true
    }

    /**
     * Get the path to an installed model
     */
    fun getModelPath(context: Context, modelId: String): File {
        return File(context.filesDir, modelId)
    }

    /**
     * Download and extract a Vosk model
     * @param onProgress Called with progress percentage (0-100)
     */
    suspend fun downloadModel(
        context: Context,
        model: VoskModel,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, model.id)

            // Delete existing model if present
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }

            DebugLog.d("VoskModelDownloader", "Downloading ${model.name} from ${model.url}")
            onProgress(0)

            // Download the ZIP file
            val connection = URL(model.url).openConnection()
            connection.connect()
            val totalSize = connection.contentLength

            connection.getInputStream().use { input ->
                val tempZip = File(context.cacheDir, "${model.id}.zip")
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = ((totalBytesRead * 50) / totalSize).toInt() // 0-50% for download
                        onProgress(progress)
                    }
                }

                DebugLog.d("VoskModelDownloader", "Download complete, extracting...")
                onProgress(50)

                // Extract the ZIP file
                ZipInputStream(tempZip.inputStream()).use { zipInput ->
                    var entry = zipInput.nextEntry
                    var fileCount = 0

                    while (entry != null) {
                        val file = File(context.filesDir, entry.name)

                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { output ->
                                zipInput.copyTo(output)
                            }
                        }

                        fileCount++
                        // 50-100% for extraction
                        val progress = 50 + (fileCount * 50 / 100).coerceIn(0, 50)
                        onProgress(progress)

                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }

                // Delete temp ZIP file
                tempZip.delete()

                onProgress(100)
                DebugLog.d("VoskModelDownloader", "Model installed to: ${modelDir.absolutePath}")

                Result.success(modelDir)
            }
        } catch (e: Exception) {
            DebugLog.e("VoskModelDownloader", "Failed to download model: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a model
     */
    fun deleteModel(context: Context, modelId: String): Boolean {
        val modelDir = File(context.filesDir, modelId)
        return if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            false
        }
    }

    /**
     * Get installed models
     */
    fun getInstalledModels(context: Context): List<VoskModel> {
        return AVAILABLE_MODELS.filter { isModelInstalled(context, it.id) }
    }
}
