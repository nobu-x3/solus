# Solus Android App

Voice-controlled AI assistant Android app that connects to the Solus server.

## Features

- **Always-on Voice Listening**: Continuously listens for voice commands using a foreground service
- **Settings Page**: Configure server connection details (host, port, user ID)
- **Action Execution**: Automatically executes server-returned actions (open apps, make calls, send messages, etc.)
- **Conversation History**: Maintains conversation context with the server
- **Modern UI**: Built with Jetpack Compose and Material Design 3

## Requirements

- Android 8.0 (API 26) or higher
- Microphone permission
- Internet permission
- Phone and SMS permissions (for call/message actions)
- Overlay permission (for always-on listening)

## Setup Instructions

### 1. Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK with API 34

### 2. Project Structure

```
android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/solus/assistant/
│   │       │   ├── MainActivity.kt
│   │       │   ├── SolusApplication.kt
│   │       │   ├── data/
│   │       │   │   ├── model/
│   │       │   │   │   ├── ChatRequest.kt
│   │       │   │   │   ├── ChatResponse.kt
│   │       │   │   │   └── ServerAction.kt
│   │       │   │   ├── network/
│   │       │   │   │   ├── SolusApi.kt
│   │       │   │   │   └── RetrofitClient.kt
│   │       │   │   └── preferences/
│   │       │   │       └── SettingsManager.kt
│   │       │   ├── service/
│   │       │   │   ├── VoiceListenerService.kt
│   │       │   │   └── ActionExecutor.kt
│   │       │   └── ui/
│   │       │       ├── screens/
│   │       │       │   ├── MainScreen.kt
│   │       │       │   └── SettingsScreen.kt
│   │       │       ├── theme/
│   │       │       │   ├── Color.kt
│   │       │       │   ├── Theme.kt
│   │       │       │   └── Type.kt
│   │       │       └── navigation/
│   │       │           └── Navigation.kt
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 3. Build and Run

1. Open the `android` folder in Android Studio
2. Sync Gradle files
3. Configure the Solus server connection in Settings (default: http://10.0.2.2:8000 for emulator)
4. Grant all required permissions
5. Start the voice listening service
6. Speak your commands!

## Server API Integration

The app communicates with the Solus server using HTTP REST API:

### POST /chat
```json
Request:
{
  "text": "User's voice command",
  "user_id": "android_user_123",
  "conversation_id": "optional_existing_id"
}

Response:
{
  "response": "AI assistant response",
  "action": {
    "type": "todo_add",
    "params": {
      "title": "Buy groceries",
      "description": "Milk, bread, eggs",
      "priority": "high",
      "due_date": "2025-11-21T10:00:00Z"
    }
  },
  "conversation_id": "conv_123"
}
```

## Supported Actions

The app can execute the following server actions:

- **todo_add**: Add a task to the default todo app
- **reminder_set**: Set a reminder/alarm
- **note_create**: Create a note
- **app_open**: Open an app by package name
- **call_make**: Make a phone call
- **message_send**: Send an SMS message

## Permissions

The app requires the following permissions:

- `RECORD_AUDIO`: For voice recognition
- `INTERNET`: For server communication
- `FOREGROUND_SERVICE`: For always-on listening
- `FOREGROUND_SERVICE_MICROPHONE`: For microphone access in foreground service
- `POST_NOTIFICATIONS`: For service notifications
- `CALL_PHONE`: For making calls (optional)
- `SEND_SMS`: For sending messages (optional)
- `SYSTEM_ALERT_WINDOW`: For overlay UI (optional)

## Configuration

Settings are stored using SharedPreferences:

- **Server Host**: Default `http://10.0.2.2` (emulator), or your server IP
- **Server Port**: Default `8000`
- **User ID**: Unique identifier for this device
- **Auto-start**: Start listening on app launch

## Development

### Key Components

1. **VoiceListenerService**: Foreground service that continuously listens for voice commands
2. **ActionExecutor**: Executes actions returned by the server
3. **SettingsManager**: Manages app preferences using DataStore
4. **SolusApi**: Retrofit interface for server communication
5. **Navigation**: Jetpack Compose navigation between screens

### Testing

- For emulator testing, the default server URL is `http://10.0.2.2:8000` (localhost on host machine)
- For physical device, use your computer's local IP address (e.g., `http://192.168.1.100:8000`)
- Ensure the Solus server is running before testing

## Troubleshooting

### Voice Recognition Not Working
- Check microphone permission is granted
- Ensure device is not muted
- Verify Google Speech Recognition is installed

### Cannot Connect to Server
- Verify server is running: `curl http://localhost:8000/health`
- Check server URL in Settings
- For emulator: use `10.0.2.2` instead of `localhost`
- For physical device: ensure both device and server are on same network
- Check firewall settings

### Actions Not Executing
- Grant required permissions for specific actions
- Check Logcat for error messages
- Verify action format matches server response schema

## License

Part of the Solus AI Assistant project.
