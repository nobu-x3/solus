package com.solus.assistant.util

import android.util.Log
import com.solus.assistant.BuildConfig

/**
 * Debug logging utility that only logs in debug builds
 */
object DebugLog {
    /**
     * Debug log - only logs in debug builds
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Error log - always logs (even in release)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Warning log - always logs
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    /**
     * Info log - only in debug builds
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }
}
