package com.solus.assistant.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.service.VoiceListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    // State
    var isListening by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var voiceService: VoiceListenerService? by remember { mutableStateOf(null) }
    var serverHost by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(false) }

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
    }

    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as VoiceListenerService.LocalBinder
                voiceService = binder.getService()
                serviceConnected = true
                isListening = voiceService?.isCurrentlyListening() ?: false
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceConnected = false
                voiceService = null
            }
        }
    }

    // Bind to service
    DisposableEffect(Unit) {
        val intent = Intent(context, VoiceListenerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    // Auto-start if enabled
    LaunchedEffect(serviceConnected, autoStart) {
        if (serviceConnected && autoStart && !isListening) {
            voiceService?.startListening()
            isListening = true
        }
    }

    // Permissions
    val permissionsList = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solus Assistant") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (permissionsState.allPermissionsGranted) {
                FloatingActionButton(
                    onClick = {
                        if (isListening) {
                            voiceService?.stopListening()
                            isListening = false
                        } else {
                            // Start foreground service
                            val intent = Intent(context, VoiceListenerService::class.java).apply {
                                action = VoiceListenerService.ACTION_START_LISTENING
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            voiceService?.startListening()
                            isListening = true
                        }
                    },
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        if (isListening) "Stop Listening" else "Start Listening"
                    )
                }
            }
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
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (isListening) "Listening" else "Stopped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (isListening) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Permissions Card
            if (!permissionsState.allPermissionsGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "This app needs microphone and notification permissions to function.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Server Info Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Server Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Host:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            serverHost.ifEmpty { "Not configured" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Port:", style = MaterialTheme.typography.bodySmall)
                        Text(serverPort, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("User ID:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            userId.take(20) + if (userId.length > 20) "..." else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Actions Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Actions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Divider()

                    Button(
                        onClick = {
                            scope.launch {
                                settingsManager.resetConversation()
                                val intent = Intent(context, VoiceListenerService::class.java).apply {
                                    action = VoiceListenerService.ACTION_RESET_CONVERSATION
                                }
                                context.startService(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Conversation")
                    }

                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Configure Settings")
                    }
                }
            }

            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        "How to Use",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Divider()
                    Text(
                        "1. Configure server settings",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Grant required permissions",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Tap the microphone button to start listening",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "4. Say the wake word (default: 'hey solus')",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "5. Speak your command",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "6. The assistant will respond and execute actions",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
