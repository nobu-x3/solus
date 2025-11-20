package com.solus.assistant

import android.app.Application
import android.util.Log

/**
 * Application class for Solus Assistant
 */
class SolusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Solus Assistant app started")
    }

    companion object {
        private const val TAG = "SolusApplication"
    }
}
