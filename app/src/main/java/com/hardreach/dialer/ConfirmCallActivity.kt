package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Confirmation dialog before initiating conference calls
 * Prevents accidental auto-dialing
 */
class ConfirmCallActivity : AppCompatActivity() {

    private val TAG = "ConfirmCallActivity"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    // Auto-decline after 30 seconds if no response
    private val AUTO_DECLINE_TIMEOUT = 30000L

    private var callId: Int = -1
    private var teamMemberNumber: String = ""
    private var contactNumber: String = ""

    private val autoDeclineRunnable = Runnable {
        Log.i(TAG, "Auto-declining call after timeout")
        declineCall()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_call)

        // Get call details from intent
        callId = intent.getIntExtra("call_id", -1)
        teamMemberNumber = intent.getStringExtra("team_member_number") ?: ""
        contactNumber = intent.getStringExtra("contact_number") ?: ""

        if (callId == -1 || contactNumber.isEmpty()) {
            Log.e(TAG, "Invalid call details")
            finish()
            return
        }

        // Setup UI
        val contactText = findViewById<TextView>(R.id.contactNumberText)
        val acceptButton = findViewById<Button>(R.id.acceptCallButton)
        val declineButton = findViewById<Button>(R.id.declineCallButton)

        contactText.text = "Call $contactNumber?"

        acceptButton.setOnClickListener {
            handler.removeCallbacks(autoDeclineRunnable)
            acceptCall()
        }

        declineButton.setOnClickListener {
            handler.removeCallbacks(autoDeclineRunnable)
            declineCall()
        }

        // Start auto-decline timer
        handler.postDelayed(autoDeclineRunnable, AUTO_DECLINE_TIMEOUT)

        Log.i(TAG, "Showing confirmation for call #$callId to $contactNumber")
        RemoteLogger.i(this, TAG, "Confirmation dialog shown for call #$callId")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoDeclineRunnable)
    }

    private fun acceptCall() {
        Log.i(TAG, "User accepted call #$callId")
        RemoteLogger.i(this, TAG, "User ACCEPTED call #$callId - initiating conference")

        // Start the actual call via CallManager
        val callManager = CallManager(this)
        callManager.initiateConferenceCall(callId, teamMemberNumber, contactNumber)

        finish()
    }

    private fun declineCall() {
        Log.i(TAG, "User declined call #$callId")
        RemoteLogger.i(this, TAG, "User DECLINED call #$callId")

        // Mark call as failed in database
        updateCallStatus(callId, "failed")

        finish()
    }

    private fun updateCallStatus(callId: Int, status: String) {
        Thread {
            try {
                val prefs = getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "")?.trim() ?: ""
                val apiKey = prefs.getString("api_key", "")?.replace("\\s".toRegex(), "") ?: ""

                if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                    Log.w(TAG, "Cannot update status - no server config")
                    return@Thread
                }

                val json = JSONObject()
                json.put("status", status)

                val body = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$serverUrl/api/dialer/pending-calls/$callId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .patch(body)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.i(TAG, "Call $callId marked as $status")
                } else {
                    Log.e(TAG, "Failed to update status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call status: ${e.message}", e)
            }
        }.start()
    }

    override fun onBackPressed() {
        // Prevent accidental back press - must explicitly decline
        // Do nothing
    }
}
