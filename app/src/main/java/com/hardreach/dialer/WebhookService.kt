package com.hardreach.dialer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WebhookService : Service() {
    
    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "webhook_service"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL = 15000L // 15 seconds
    }
    
    private val TAG = "WebhookService"
    private lateinit var callManager: CallManager
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    
    private val pollRunnable = object : Runnable {
        override fun run() {
            pollForCalls()
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        callManager = CallManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        
        // Start polling
        handler.post(pollRunnable)
        
        Log.i(TAG, "Service started - polling every ${POLL_INTERVAL/1000}s")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "Service stopped")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hardreach Dialer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hardreach Dialer Active")
            .setContentText("Listening for calls from CRM")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    
    private fun pollForCalls() {
        val prefs = getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val apiKey = prefs.getString("api_key", "") ?: ""
        
        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            Log.w(TAG, "Server URL or API key not configured")
            return
        }
        
        val request = Request.Builder()
            .url("$serverUrl/api/dialer/pending-calls")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Polling failed: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Polling failed: ${it.code}")
                        return
                    }
                    
                    val body = it.body?.string() ?: return
                    Log.d(TAG, "Response: $body")
                    
                    try {
                        val json = JSONObject(body)
                        val calls = json.getJSONArray("calls")
                        
                        if (calls.length() > 0) {
                            val call = calls.getJSONObject(0)
                            val teamMemberNumber = call.getString("team_member_number")
                            val contactNumber = call.getString("contact_number")
                            
                            Log.i(TAG, "Initiating call: $teamMemberNumber -> $contactNumber")
                            callManager.initiateConferenceCall(teamMemberNumber, contactNumber)
                            
                            // TODO: Mark call as completed in CRM
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response: ${e.message}")
                    }
                }
            }
        })
    }
}
