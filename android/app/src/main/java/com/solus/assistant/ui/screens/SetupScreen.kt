package com.solus.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.util.PiperTTSManager
import com.solus.assistant.util.VoskModelDownloader
import kotlinx.coroutines.launch

/**
 * First-run setup screen for language selection and model download
 */
enum class SetupStep {
    VOSK_MODEL,
    TTS_VOICE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settingsManager: SettingsManager,
    onSetupComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ttsManager = remember { PiperTTSManager(settingsManager.context) }

    var setupStep by remember { mutableStateOf(SetupStep.VOSK_MODEL) }
    var selectedModel by remember { mutableStateOf<VoskModelDownloader.VoskModel?>(null) }
    var selectedVoice by remember { mutableStateOf<PiperTTSManager.PiperVoice?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to Solus Assistant") }
            )
        }
    ) { padding ->
        if (isDownloading) {
            // Show download progress
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (setupStep) {
                        SetupStep.VOSK_MODEL -> "Downloading ${selectedModel?.name}..."
                        SetupStep.TTS_VOICE -> "Downloading ${selectedVoice?.name}..."
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = downloadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$downloadProgress%",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This will only happen once. The voice model runs entirely on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isDownloading = false
                            errorMessage = null
                            selectedModel = null
                        }
                    ) {
                        Text("Try Again")
                    }
                }
            }
        } else {
            when (setupStep) {
                SetupStep.VOSK_MODEL -> VoskModelSelection(
                    selectedModel = selectedModel,
                    onModelSelect = { selectedModel = it },
                    onContinue = {
                        selectedModel?.let { model ->
                            isDownloading = true
                            errorMessage = null
                            downloadProgress = 0

                            scope.launch {
                                try {
                                    val result = VoskModelDownloader.downloadModel(
                                        context = settingsManager.context,
                                        model = model
                                    ) { progress ->
                                        downloadProgress = progress
                                    }

                                    if (result.isSuccess) {
                                        settingsManager.setVoskModelId(model.id)
                                        isDownloading = false
                                        setupStep = SetupStep.TTS_VOICE
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message ?: "Download failed"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Unknown error"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                )
                SetupStep.TTS_VOICE -> TtsVoiceSelection(
                    selectedVoice = selectedVoice,
                    onVoiceSelect = { selectedVoice = it },
                    onSkip = {
                        scope.launch {
                            settingsManager.setFirstRunComplete(true)
                            onSetupComplete()
                        }
                    },
                    onDownload = {
                        selectedVoice?.let { voice ->
                            isDownloading = true
                            errorMessage = null
                            downloadProgress = 0

                            scope.launch {
                                try {
                                    val result = ttsManager.downloadVoice(voice) { progress ->
                                        downloadProgress = progress
                                    }

                                    if (result.isSuccess) {
                                        settingsManager.setTtsVoiceId(voice.id)
                                        settingsManager.setTtsEnabled(true)
                                        settingsManager.setFirstRunComplete(true)
                                        onSetupComplete()
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message ?: "Download failed"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Unknown error"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionCard(
    model: VoskModelDownloader.VoskModel,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = model.size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}

@Composable
private fun VoskModelSelection(
    selectedModel: VoskModelDownloader.VoskModel?,
    onModelSelect: (VoskModelDownloader.VoskModel) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Choose Your Language",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Select the language for voice recognition. A small model (~40MB) will be downloaded for on-device wake word detection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✓ 100% Free & Open Source", style = MaterialTheme.typography.bodyMedium)
                Text("✓ No API Keys Required", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Works Completely Offline", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Privacy-Focused (On-Device)", style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VoskModelDownloader.AVAILABLE_MODELS) { model ->
                ModelSelectionCard(
                    model = model,
                    isSelected = selectedModel == model,
                    onSelect = { onModelSelect(model) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = selectedModel != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download and Continue")
        }
    }
}

@Composable
private fun TtsVoiceSelection(
    selectedVoice: PiperTTSManager.PiperVoice?,
    onVoiceSelect: (PiperTTSManager.PiperVoice) -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Choose Voice Assistant (Optional)",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Select a voice for text-to-speech. This will read responses aloud for voice commands.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PiperTTSManager.AVAILABLE_VOICES) { voice ->
                VoiceSelectionCard(
                    voice = voice,
                    isSelected = selectedVoice == voice,
                    onSelect = { onVoiceSelect(voice) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip for Now")
            }
            Button(
                onClick = onDownload,
                enabled = selectedVoice != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Download Voice")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelectionCard(
    voice: PiperTTSManager.PiperVoice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${voice.language} • ${voice.quality} • ${voice.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}
