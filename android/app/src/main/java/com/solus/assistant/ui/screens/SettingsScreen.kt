package com.solus.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.util.AndroidTTSManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    // State for settings
    var serverHost by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(false) }
    var wakeWordEnabled by remember { mutableStateOf(true) }
    var wakeWord by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(false) }
    var ttsVoiceId by remember { mutableStateOf("") }
    var expandedVoiceDropdown by remember { mutableStateOf(false) }

    // Load settings
    LaunchedEffect(Unit) {
        launch {
            settingsManager.serverHost.collect { serverHost = it }
        }
        launch {
            settingsManager.serverPort.collect { serverPort = it }
        }
        launch {
            settingsManager.userId.collect { userId = it }
        }
        launch {
            settingsManager.autoStart.collect { autoStart = it }
        }
        launch {
            settingsManager.wakeWordEnabled.collect { wakeWordEnabled = it }
        }
        launch {
            settingsManager.wakeWord.collect { wakeWord = it }
        }
        launch {
            settingsManager.ttsEnabled.collect { ttsEnabled = it }
        }
        launch {
            settingsManager.ttsVoiceId.collect { ttsVoiceId = it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                connectionStatus = ConnectionStatus.Testing
                                connectionStatus = testConnection(serverHost, serverPort)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Test Connection")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Configuration Section
            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("Server Host") },
                placeholder = { Text("http://10.0.2.2") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("Server Port") },
                placeholder = { Text("8000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Connection status
            when (connectionStatus) {
                is ConnectionStatus.Idle -> {}
                is ConnectionStatus.Testing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Testing connection...", style = MaterialTheme.typography.bodySmall)
                }
                is ConnectionStatus.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                "Success",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Connected Successfully",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    (connectionStatus as ConnectionStatus.Success).message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                is ConnectionStatus.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Connection Failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                (connectionStatus as ConnectionStatus.Error).message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Divider()

            // User Configuration Section
            Text(
                text = "User Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("User ID") },
                placeholder = { Text("android_user_123") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Unique identifier for this device")
                }
            )

            Divider()

            // Voice Settings Section
            Text(
                text = "Voice Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wake Word", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Require wake word before listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { wakeWordEnabled = it }
                )
            }

            if (wakeWordEnabled) {
                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    label = { Text("Wake Word") },
                    placeholder = { Text("hey solus") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-start", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Start listening when app opens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = { autoStart = it }
                )
            }

            Divider()

            // TTS (Text-to-Speech) Section
            Text(
                text = "Text-to-Speech",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable TTS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Read responses aloud for voice commands",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ttsEnabled,
                    onCheckedChange = { ttsEnabled = it }
                )
            }

            if (ttsEnabled) {
                // Voice selection dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedVoiceDropdown,
                    onExpandedChange = { expandedVoiceDropdown = !expandedVoiceDropdown }
                ) {
                    OutlinedTextField(
                        value = AndroidTTSManager.AVAILABLE_VOICES.find { it.id == ttsVoiceId }?.name ?: "Select voice",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Voice") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVoiceDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedVoiceDropdown,
                        onDismissRequest = { expandedVoiceDropdown = false }
                    ) {
                        AndroidTTSManager.AVAILABLE_VOICES.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(voice.name)
                                        Text(
                                            "${voice.language} • ${voice.quality} • ${voice.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    ttsVoiceId = voice.id
                                    expandedVoiceDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Divider()

            // Chat History Section
            Text(
                text = "Chat History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = { showClearHistoryDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Chat History")
            }

            Divider()

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        settingsManager.setServerHost(serverHost)
                        settingsManager.setServerPort(serverPort)
                        settingsManager.setUserId(userId)
                        settingsManager.setAutoStart(autoStart)
                        settingsManager.setWakeWordEnabled(wakeWordEnabled)
                        settingsManager.setWakeWord(wakeWord)
                        settingsManager.setTtsEnabled(ttsEnabled)
                        settingsManager.setTtsVoiceId(ttsVoiceId)
                        // Reset Retrofit client to use new settings
                        RetrofitClient.reset()
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            // Info Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Quick Setup Guide",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "• For Android Emulator: use http://10.0.2.2",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• For Physical Device: use your computer's local IP (e.g., http://192.168.1.100)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Default port is 8000",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Ensure the Solus server is running before connecting",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This will permanently delete all chat messages. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsManager.clearChatHistory()
                            showClearHistoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Connection status sealed class
 */
sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Testing : ConnectionStatus()
    data class Success(val message: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

/**
 * Test connection to server
 */
private suspend fun testConnection(host: String, port: String): ConnectionStatus {
    return try {
        val baseUrl = "$host:$port/"
        val api = RetrofitClient.getInstance(baseUrl)
        val response = api.getHealth()

        if (response.isSuccessful) {
            val health = response.body()
            if (health != null && health.status == "healthy") {
                ConnectionStatus.Success(
                    "Model loaded: ${health.modelLoaded}, " +
                    "Memories: ${health.memoryCount}"
                )
            } else {
                ConnectionStatus.Error("Server returned unhealthy status")
            }
        } else {
            ConnectionStatus.Error("HTTP ${response.code()}: ${response.message()}")
        }
    } catch (e: Exception) {
        ConnectionStatus.Error(e.message ?: "Unknown error")
    }
}
