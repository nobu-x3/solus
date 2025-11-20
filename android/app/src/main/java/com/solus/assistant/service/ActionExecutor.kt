package com.solus.assistant.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.solus.assistant.data.model.ActionType
import com.solus.assistant.data.model.ServerAction
import java.text.SimpleDateFormat
import java.util.*

/**
 * Executes actions returned by the server
 */
class ActionExecutor(private val context: Context) {

    /**
     * Execute an action based on its type
     */
    fun executeAction(action: ServerAction) {
        Log.d(TAG, "Executing action: ${action.type}")

        try {
            when (action.type) {
                ActionType.TODO_ADD -> executeTodoAdd(action.params)
                ActionType.REMINDER_SET -> executeReminderSet(action.params)
                ActionType.NOTE_CREATE -> executeNoteCreate(action.params)
                ActionType.APP_OPEN -> executeAppOpen(action.params)
                ActionType.CALL_MAKE -> executeCallMake(action.params)
                ActionType.MESSAGE_SEND -> executeMessageSend(action.params)
                else -> {
                    Log.w(TAG, "Unknown action type: ${action.type}")
                    showToast("Unknown action: ${action.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action ${action.type}", e)
            showToast("Error: ${e.message}")
        }
    }

    /**
     * Add a todo/task
     */
    private fun executeTodoAdd(params: Map<String, Any>) {
        val title = params["title"] as? String ?: return
        val description = params["description"] as? String
        val priority = params["priority"] as? String
        val dueDate = params["due_date"] as? String

        Log.d(TAG, "Adding todo: $title")

        // Try to add to Google Tasks or default calendar
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }

            // Parse due date if provided
            dueDate?.let {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    val date = sdf.parse(it)
                    date?.let { d ->
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.time)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.time + 3600000) // +1 hour
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing date: $it", e)
                }
            }

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            showToast("Opening calendar to add task")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening calendar", e)
            showToast("Could not open calendar app")
        }
    }

    /**
     * Set a reminder/alarm
     */
    private fun executeReminderSet(params: Map<String, Any>) {
        val title = params["title"] as? String ?: return
        val time = params["time"] as? String ?: return
        val repeat = params["repeat"] as? String

        Log.d(TAG, "Setting reminder: $title at $time")

        try {
            // Parse time
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val date = sdf.parse(time)

            if (date != null) {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = date.time
                }

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, title)
                    putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
                    putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))

                    // Handle repeat
                    when (repeat?.lowercase()) {
                        "daily" -> {
                            putExtra(AlarmClock.EXTRA_DAYS, ArrayList<Int>().apply {
                                addAll(listOf(
                                    Calendar.MONDAY,
                                    Calendar.TUESDAY,
                                    Calendar.WEDNESDAY,
                                    Calendar.THURSDAY,
                                    Calendar.FRIDAY,
                                    Calendar.SATURDAY,
                                    Calendar.SUNDAY
                                ))
                            })
                        }
                        "weekly" -> {
                            putExtra(AlarmClock.EXTRA_DAYS, ArrayList<Int>().apply {
                                add(calendar.get(Calendar.DAY_OF_WEEK))
                            })
                        }
                    }

                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
                showToast("Setting alarm")
            } else {
                showToast("Invalid time format")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting reminder", e)
            showToast("Could not set reminder")
        }
    }

    /**
     * Create a note
     */
    private fun executeNoteCreate(params: Map<String, Any>) {
        val title = params["title"] as? String ?: ""
        val content = params["content"] as? String ?: return

        Log.d(TAG, "Creating note: $title")

        val fullContent = if (title.isNotEmpty()) {
            "$title\n\n$content"
        } else {
            content
        }

        // Try to open default notes app or share to notes
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, fullContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Save note to").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            showToast("Creating note")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating note", e)
            showToast("Could not create note")
        }
    }

    /**
     * Open an app by package name
     */
    private fun executeAppOpen(params: Map<String, Any>) {
        val packageName = params["package_name"] as? String ?: return

        Log.d(TAG, "Opening app: $packageName")

        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                showToast("Opening $packageName")
            } else {
                showToast("App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app", e)
            showToast("Could not open app")
        }
    }

    /**
     * Make a phone call
     */
    private fun executeCallMake(params: Map<String, Any>) {
        val phoneNumber = params["phone_number"] as? String ?: return

        Log.d(TAG, "Making call to: $phoneNumber")

        // Check permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Phone permission not granted")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            showToast("Calling $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            showToast("Could not make call")
        }
    }

    /**
     * Send an SMS message
     */
    private fun executeMessageSend(params: Map<String, Any>) {
        val phoneNumber = params["phone_number"] as? String ?: return
        val message = params["message"] as? String ?: return

        Log.d(TAG, "Sending message to: $phoneNumber")

        // Check permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("SMS permission not granted")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use SmsManager for Android 12+
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                // Use deprecated method for older versions
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            showToast("Message sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            showToast("Could not send message")
        }
    }

    /**
     * Show a toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
