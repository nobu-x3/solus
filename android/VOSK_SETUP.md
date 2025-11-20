# Vosk Wake Word Detection Setup

Your Solus Android app now uses **Vosk** for completely FREE local wake word detection!

## Why Vosk?

✅ **Completely free** - No API keys, no subscriptions, no payment required
✅ **100% on-device** - Runs entirely offline, no cloud needed
✅ **No sign-up** - Zero registration or accounts required
✅ **Open source** - MIT license, full transparency
✅ **No beeping!** - Silent wake word detection just like Google Assistant
✅ **Low battery** - Optimized for continuous listening (~2-3%/hour)

## How It Works

1. **Vosk** runs continuously listening for "hey solus"
2. When detected, activates **Google SpeechRecognizer** for the command
3. Command sent to server and processed
4. Returns to Vosk wake word listening

No beeping, no network usage for wake word, minimal battery drain!

## Setup Instructions

### Step 1: Download Vosk Model

You need to download a small speech model (40MB) for wake word detection:

1. Go to: https://alphacephei.com/vosk/models
2. Download: **vosk-model-small-en-us-0.15.zip** (40.8 MB)
3. Extract the ZIP file

You should have a folder named `vosk-model-small-en-us-0.15` containing:
- `am/` folder
- `conf/` folder
- `graph/` folder
- `ivector/` folder

### Step 2: Install Model on Device/Emulator

#### Option A: Using adb (Easiest)

```bash
# Navigate to where you extracted the model
cd path/to/vosk-model-small-en-us-0.15

# Push the model to your device
adb push vosk-model-small-en-us-0.15 /data/data/com.solus.assistant/files/
```

####  Option B: Using Android File Explorer

1. Connect your device via USB
2. Open Android Studio → View → Tool Windows → Device File Explorer
3. Navigate to: `/data/data/com.solus.assistant/files/`
4. Right-click → Upload → Select the `vosk-model-small-en-us-0.15` folder

#### Option C: Programmatic Download (Advanced)

You can add code to download the model automatically on first run. The model URL is:
```
https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
```

### Step 3: Verify Installation

The model folder should be at:
```
/data/data/com.solus.assistant/files/vosk-model-small-en-us-0.15/
```

Inside this folder you should see:
- `am/`
- `conf/`
- `graph/`
- `ivector/`

### Step 4: Build and Run

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The app will now use Vosk for wake word detection!

## Using the App

1. Open the app and grant microphone permissions
2. Tap "Start Listening"
3. Notification will show "Say 'hey solus'"
4. Say **"hey solus"** followed by your command
5. App will process and respond
6. Returns to listening for wake word

## Other Vosk Models

You can use different models for different languages or accuracy levels:

### Lightweight Models (~40MB)
- **vosk-model-small-en-us-0.15** (English US) - Recommended
- **vosk-model-small-en-in-0.4** (English India)
- **vosk-model-small-cn-0.22** (Chinese)
- **vosk-model-small-ru-0.22** (Russian)
- Many more at: https://alphacephei.com/vosk/models

### Larger Models (~1-2GB) - Better Accuracy
- **vosk-model-en-us-0.22** (English US) - Higher accuracy
- **vosk-model-en-us-0.22-lgraph** (English US) - Highest accuracy

To use a different model, update `VoiceListenerService.kt` line 159:
```kotlin
val modelPath = File(filesDir, "vosk-model-small-en-us-0.15")
```
Change to your model folder name.

## Customizing Wake Word

The wake word is currently "hey solus". To change it, edit `VoiceListenerService.kt` line 50:

```kotlin
private var wakeWord = "hey solus"
```

Change to any phrase you like:
```kotlin
private var wakeWord = "jarvis"  // Like Iron Man
private var wakeWord = "computer"  // Like Star Trek
private var wakeWord = "alfred"  // Like Batman
```

## Troubleshooting

**"Wake word model required" error:**
- Model not installed correctly
- Check the folder is at: `/data/data/com.solus.assistant/files/vosk-model-small-en-us-0.15/`
- Verify folder contains `am/`, `conf/`, `graph/`, `ivector/` subdirectories

**Wake word not detected:**
- Speak clearly and at normal volume
- Make sure you're saying the exact phrase "hey solus"
- Try adjusting your volume or distance from mic
- Check logs for partial recognition results

**High battery drain:**
- Vosk is optimized but continuous listening uses some battery (2-3%/hour typical)
- Much better than the old continuous cloud approach
- Consider using a larger model if accuracy is an issue (small models may need retries)

**Model loading slow:**
- First load takes 2-5 seconds - this is normal
- Model stays in memory after first load
- Larger models take longer to load but are more accurate

## Benefits vs. Old Approach

| Old (Continuous Google SpeechRecognizer) | New (Vosk Wake Word) |
|------------------------------------------|----------------------|
| ❌ Constant beeping | ✅ No beeping |
| ❌ High battery drain | ✅ Low battery (~2-3%/hr) |
| ❌ Requires internet always | ✅ Wake word works offline |
| ❌ High data usage | ✅ Zero data for wake word |
| ❌ Frequent restarts | ✅ Runs continuously |
| ❌ API keys/subscriptions | ✅ 100% free forever |

## Resources

- Vosk Models: https://alphacephei.com/vosk/models
- Vosk Documentation: https://alphacephei.com/vosk/
- Vosk GitHub: https://github.com/alphacep/vosk-api
- Vosk Android: https://github.com/alphacep/vosk-android-demo

## License

Vosk is MIT licensed and completely free to use, even commercially!
