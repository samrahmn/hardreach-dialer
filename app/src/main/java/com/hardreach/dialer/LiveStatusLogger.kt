package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Broadcasts live status updates to MainActivity for real-time visibility
 */
object LiveStatusLogger {

    const val ACTION_STATUS_UPDATE = "com.hardreach.dialer.STATUS_UPDATE"
    const val ACTION_LOG_UPDATE = "com.hardreach.dialer.LOG_UPDATE"
    const val EXTRA_STATUS = "status"
    const val EXTRA_LOG = "log"

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Update the live status banner (current call state)
     */
    fun updateStatus(context: Context, status: String) {
        val intent = Intent(ACTION_STATUS_UPDATE)
        intent.setPackage(context.packageName) // Make explicit for Android 8+
        intent.putExtra(EXTRA_STATUS, status)
        context.sendBroadcast(intent)
    }

    /**
     * Add a log entry to the activity log
     */
    fun log(context: Context, message: String) {
        val timestamp = timeFormat.format(Date())
        val logEntry = "[$timestamp] $message"

        val intent = Intent(ACTION_LOG_UPDATE)
        intent.setPackage(context.packageName) // Make explicit for Android 8+
        intent.putExtra(EXTRA_LOG, logEntry)
        context.sendBroadcast(intent)
    }

    /**
     * Convenience methods for common statuses
     */
    fun callStarted(context: Context, number: String) {
        updateStatus(context, "📞 Calling $number...")
        log(context, "Initiating call to $number")
    }

    fun callConnected(context: Context, number: String) {
        updateStatus(context, "✓ Connected to $number")
        log(context, "Call connected to $number")
    }

    fun waitingForAnswer(context: Context, seconds: Int) {
        updateStatus(context, "⏳ Waiting ${seconds}s for answer...")
        log(context, "Waiting ${seconds}s for call to be answered")
    }

    fun mergingCalls(context: Context) {
        updateStatus(context, "🔀 Merging calls into conference...")
        log(context, "Attempting to merge calls")
    }

    fun conferenceCreated(context: Context) {
        updateStatus(context, "✅ Conference active - You are MUTED")
        log(context, "✅ Conference created successfully - Microphone muted")
    }

    fun callFailed(context: Context, reason: String) {
        updateStatus(context, "❌ Call failed: $reason")
        log(context, "Call failed: $reason")
    }

    fun idle(context: Context) {
        updateStatus(context, "Waiting for calls...")
        log(context, "Ready for next call")
    }
}
