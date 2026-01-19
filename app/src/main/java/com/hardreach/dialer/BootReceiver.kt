package com.hardreach.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device booted, checking if polling enabled")

            val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
            val serviceEnabled = prefs.getBoolean("service_enabled", false)

            if (serviceEnabled) {
                Log.i("BootReceiver", "Polling was enabled - starting foreground service")
                RemoteLogger.i(context, "BootReceiver", "Boot completed - starting service")

                // Start foreground service for reliable background polling
                val serviceIntent = Intent(context, WebhookService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Also schedule AlarmManager as backup
                AlarmScheduler.schedulePolling(context)
            }
        }
    }
}
