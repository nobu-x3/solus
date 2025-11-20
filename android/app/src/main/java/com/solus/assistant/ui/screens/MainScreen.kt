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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.service.VoiceListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    // State
    var serverHost by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    var isListening by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var voiceService: VoiceListenerService? by remember { mutableStateOf(null) }
    var isPendingResponse by remember { mutableStateOf(false) }

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

    // Load settings
    LaunchedEffect(Unit) {
        launch {
            settingsManager.serverHost.collect { serverHost = it }
        }
        launch {
            settingsManager.serverPort.collect { serverPort = it }
        }
        launch {
            settingsManager.isPendingResponse.collect { isPending ->
                android.util.Log.d("MainScreen", "isPendingResponse changed to: $isPending")
                isPendingResponse = isPending
            }
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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Welcome
            Text(
                "Welcome to Solus",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Text(
                "Your AI voice assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connection status
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
                        "Server Connection",
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

                    when (connectionStatus) {
                        is ConnectionStatus.Idle -> {}
                        is ConnectionStatus.Testing -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        "Success",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is ConnectionStatus.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    "Connection failed: ${(connectionStatus as ConnectionStatus.Error).message}",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Permissions
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
                            "Microphone and notification permissions are needed for voice features.",
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

            // Voice Listener Card
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
                            "Voice Assistant",
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
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Listening in background. App can be closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Show pending request status
                    if (isPendingResponse) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Processing request...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                onClick = {
                                    android.util.Log.d("MainScreen", "Cancel button clicked")
                                    voiceService?.cancelCurrentRequest()
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (isListening) {
                                voiceService?.stopListening()
                                isListening = false
                            } else {
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = permissionsState.allPermissionsGranted && serviceConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isListening) "Stop Listening" else "Start Listening")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Chat Button
            Button(
                onClick = onNavigateToChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = permissionsState.allPermissionsGranted
            ) {
                Icon(Icons.Default.Chat, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Chat", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Configure Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Tips
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
                        "Quick Start Guide",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Divider()
                    Text(
                        "1. Configure server settings (host and port)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Grant required permissions",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Test connection to verify setup",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "4. Start chatting using voice or text",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
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
                ConnectionStatus.Success("Connected successfully")
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
