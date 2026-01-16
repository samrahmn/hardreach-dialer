package com.hardreach.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver that receives alarm broadcasts and starts polling service
 */
class PollAlarmReceiver : BroadcastReceiver() {

    private val TAG = "PollAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Alarm fired - starting poll service")
        RemoteLogger.i(context, TAG, "Alarm fired - starting poll")

        // Check if polling is still enabled
        val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (!isEnabled) {
            Log.i(TAG, "Polling disabled - not starting service")
            return
        }

        val serviceIntent = Intent(context, WebhookService::class.java)
        serviceIntent.action = "POLL_ONCE"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Schedule next alarm
            AlarmScheduler.schedulePolling(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
            RemoteLogger.e(context, TAG, "Failed to start service: ${e.message}")
        }
    }
}
