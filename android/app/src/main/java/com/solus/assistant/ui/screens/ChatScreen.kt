package com.solus.assistant.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solus.assistant.data.model.ChatMessage
import com.solus.assistant.data.model.ChatRequest
import com.solus.assistant.data.network.RetrofitClient
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.service.ActionExecutor
import com.solus.assistant.service.VoiceListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val actionExecutor = remember { ActionExecutor(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // State
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var textInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var voiceService: VoiceListenerService? by remember { mutableStateOf(null) }
    var isSending by remember { mutableStateOf(false) }

    // Load conversation ID
    var conversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        settingsManager.conversationId.collect { conversationId = it }
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    /**
     * Send message to server
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || isSending) return

        // Add user message
        messages = messages + ChatMessage(text, isUser = true)
        textInput = ""
        isSending = true

        scope.launch {
            try {
                val baseUrl = settingsManager.serverBaseUrl.first()
                val userId = settingsManager.userId.first()
                val api = RetrofitClient.getInstance(baseUrl)

                val request = ChatRequest(
                    text = text,
                    userId = userId,
                    conversationId = conversationId
                )

                val response = api.chat(request)

                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    if (chatResponse != null) {
                        // Update conversation ID
                        conversationId = chatResponse.conversationId
                        settingsManager.setConversationId(conversationId)

                        // Add assistant message
                        messages = messages + ChatMessage(
                            chatResponse.response,
                            isUser = false
                        )

                        // Execute action if present
                        chatResponse.action?.let { action ->
                            actionExecutor.executeAction(action)
                        }
                    }
                } else {
                    messages = messages + ChatMessage(
                        "Error: ${response.code()} ${response.message()}",
                        isUser = false
                    )
                }
            } catch (e: Exception) {
                messages = messages + ChatMessage(
                    "Error: ${e.message}",
                    isUser = false
                )
            } finally {
                isSending = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solus Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
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
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Start a conversation\nType a message or use voice",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                items(messages) { message ->
                    MessageBubble(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        maxLines = 4,
                        enabled = !isSending
                    )

                    IconButton(
                        onClick = { sendMessage(textInput) },
                        enabled = textInput.isNotBlank() && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                "Send",
                                tint = if (textInput.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
