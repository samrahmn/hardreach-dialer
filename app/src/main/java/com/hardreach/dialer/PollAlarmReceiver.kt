package com.hardreach.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * BroadcastReceiver that receives alarm broadcasts and polls for calls
 * Does polling directly in receiver for more reliable background operation
 */
class PollAlarmReceiver : BroadcastReceiver() {

    private val TAG = "PollAlarmReceiver"
    private val CHANNEL_ID = "poll_channel"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Alarm fired - polling for calls")
        RemoteLogger.i(context, TAG, "Alarm fired - starting poll")

        // Check if polling is still enabled
        val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (!isEnabled) {
            Log.i(TAG, "Polling disabled - skipping")
            return
        }

        // Schedule next alarm FIRST to ensure continuity
        AlarmScheduler.schedulePolling(context)

        // Acquire wake lock for polling
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HardreachDialer::PollWakeLock"
        )
        wakeLock.acquire(30000) // 30 second timeout

        // Poll in background thread
        Thread {
            try {
                pollForCalls(context)
            } catch (e: Exception) {
                Log.e(TAG, "Poll error: ${e.message}")
                RemoteLogger.e(context, TAG, "Poll error: ${e.message}")
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }.start()
    }

    private fun pollForCalls(context: Context) {
        val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "")?.trim() ?: ""
        val apiKey = prefs.getString("api_key", "")?.replace("\\s".toRegex(), "") ?: ""

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            Log.w(TAG, "Server URL or API key not configured")
            return
        }

        Log.d(TAG, "Polling $serverUrl/api/dialer/pending-calls")

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$serverUrl/api/dialer/pending-calls")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Polling failed: ${response.code} - ${response.message}")
                return
            }

            val body = response.body?.string()
            if (body == null) {
                Log.e(TAG, "Empty response body")
                return
            }

            Log.d(TAG, "Response: $body")

            val json = JSONObject(body)
            val calls = json.getJSONArray("calls")

            if (calls.length() > 0) {
                val call = calls.getJSONObject(0)
                val callId = call.getInt("id")
                val teamMemberNumber = call.getString("team_member_number")
                val contactNumber = call.getString("contact_number")

                Log.i(TAG, "Found pending call ID $callId - initiating conference")
                RemoteLogger.i(context, TAG, "Found call #$callId - starting conference flow")

                // CallManager handles auto-accept check and shows confirmations as needed
                val callManager = CallManager(context)
                callManager.initiateConferenceCall(callId, teamMemberNumber, contactNumber)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Polling exception: ${e.message}", e)
        }
    }
}
