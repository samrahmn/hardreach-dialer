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
 * Confirmation dialog for conference call steps
 * Supports: FIRST_CALL, SECOND_CALL, MERGE
 */
class ConfirmCallActivity : AppCompatActivity() {

    companion object {
        const val STEP_FIRST_CALL = "first_call"
        const val STEP_SECOND_CALL = "second_call"
        const val STEP_MERGE = "merge"

        // Callbacks for step completion
        var onFirstCallAccepted: (() -> Unit)? = null
        var onSecondCallAccepted: (() -> Unit)? = null
        var onMergeAccepted: (() -> Unit)? = null
        var onDeclined: (() -> Unit)? = null

        fun reset() {
            onFirstCallAccepted = null
            onSecondCallAccepted = null
            onMergeAccepted = null
            onDeclined = null
        }
    }

    private val TAG = "ConfirmCallActivity"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    // Auto-decline after 30 seconds if no response
    private val AUTO_DECLINE_TIMEOUT = 30000L

    private var callId: Int = -1
    private var teamMemberNumber: String = ""
    private var contactNumber: String = ""
    private var step: String = STEP_FIRST_CALL

    private val autoDeclineRunnable = Runnable {
        Log.i(TAG, "Auto-declining after timeout")
        declineCall()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_call)

        // Get call details from intent
        callId = intent.getIntExtra("call_id", -1)
        teamMemberNumber = intent.getStringExtra("team_member_number") ?: ""
        contactNumber = intent.getStringExtra("contact_number") ?: ""
        step = intent.getStringExtra("step") ?: STEP_FIRST_CALL

        // Setup UI based on step
        val titleText = findViewById<TextView>(R.id.confirmTitleText)
        val contactText = findViewById<TextView>(R.id.contactNumberText)
        val acceptButton = findViewById<Button>(R.id.acceptCallButton)
        val declineButton = findViewById<Button>(R.id.declineCallButton)

        when (step) {
            STEP_FIRST_CALL -> {
                titleText.text = "Call Team Member?"
                contactText.text = teamMemberNumber
                acceptButton.text = "Call"
            }
            STEP_SECOND_CALL -> {
                titleText.text = "Call Prospect?"
                contactText.text = contactNumber
                acceptButton.text = "Call"
            }
            STEP_MERGE -> {
                titleText.text = "Merge Calls?"
                contactText.text = "Connect both parties"
                acceptButton.text = "Merge"
            }
        }

        acceptButton.setOnClickListener {
            handler.removeCallbacks(autoDeclineRunnable)
            acceptStep()
        }

        declineButton.setOnClickListener {
            handler.removeCallbacks(autoDeclineRunnable)
            declineCall()
        }

        // Start auto-decline timer
        handler.postDelayed(autoDeclineRunnable, AUTO_DECLINE_TIMEOUT)

        Log.i(TAG, "Showing confirmation for step: $step")
        RemoteLogger.i(this, TAG, "Confirmation dialog shown for call #$callId - step: $step")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoDeclineRunnable)
    }

    private fun acceptStep() {
        Log.i(TAG, "User accepted step: $step")
        RemoteLogger.i(this, TAG, "User ACCEPTED step: $step")

        when (step) {
            STEP_FIRST_CALL -> {
                onFirstCallAccepted?.invoke()
            }
            STEP_SECOND_CALL -> {
                onSecondCallAccepted?.invoke()
            }
            STEP_MERGE -> {
                onMergeAccepted?.invoke()
            }
        }

        finish()
    }

    private fun declineCall() {
        Log.i(TAG, "User declined at step: $step")
        RemoteLogger.i(this, TAG, "User DECLINED at step: $step")

        // Mark call as failed in database
        if (callId != -1) {
            updateCallStatus(callId, "failed")
        }

        onDeclined?.invoke()
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
