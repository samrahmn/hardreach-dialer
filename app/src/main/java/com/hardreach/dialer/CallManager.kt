package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Timer-based call manager for Android 14+ compatibility
 * Uses time-based assumptions instead of call state detection
 */
class CallManager(private val context: Context) {

    private val TAG = "CallManager"
    private val handler = Handler(Looper.getMainLooper())
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val client = OkHttpClient()

    // Timings
    private val FIRST_CALL_WAIT = 15000L // 15s for Crissa to answer
    private val SECOND_CALL_WAIT = 12000L // 12s for prospect to answer
    private val MERGE_DELAY = 2000L // 2s before merge attempt

    private var currentCallId: Int? = null
    private var callStartTime = 0L

    /**
     * Timer-based conference call flow (Android 14+ compatible):
     * 1. Call team member, wait 15s
     * 2. Check if call still active (>5s duration = answered)
     * 3. If yes: call prospect, wait 12s
     * 4. Attempt merge
     * 5. Mark as completed
     */
    fun initiateConferenceCall(callId: Int, teamMemberNumber: String, contactNumber: String) {
        Log.i(TAG, "=== Timer-Based Conference Call ===")
        Log.i(TAG, "Call ID: $callId")
        Log.i(TAG, "Team Member: $teamMemberNumber")
        Log.i(TAG, "Prospect: $contactNumber")
        Log.i(TAG, "Mode: Timer-based (Android 14 compatible)")

        currentCallId = callId
        callStartTime = System.currentTimeMillis()

        // Step 1: Call team member
        makeCall(teamMemberNumber)
        Log.i(TAG, "Step 1: Calling team member...")
        Log.i(TAG, "Will check status at 15 seconds...")

        // Step 2: Check at 15 seconds
        handler.postDelayed({
            val callDuration = System.currentTimeMillis() - callStartTime
            val callState = telephonyManager.callState

            Log.i(TAG, "15s checkpoint: Call duration=${callDuration}ms, State=$callState")

            // Check if call is still active
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                // Call ended already (failed/rejected/no balance)
                Log.w(TAG, "✗ Call ended before 15s (duration: ${callDuration/1000}s)")
                Log.w(TAG, "Marking as FAILED - likely rejected or no balance")
                updateCallStatus(callId, "failed")
                cleanup()
                return@postDelayed
            }

            if (callDuration < 5000) {
                // Call too short, likely failed
                Log.w(TAG, "✗ Call duration too short: ${callDuration/1000}s")
                Log.w(TAG, "Marking as FAILED")
                updateCallStatus(callId, "failed")
                cleanup()
                return@postDelayed
            }

            // Call is still active after 15s - assume Crissa answered
            Log.i(TAG, "✓ Call active for ${callDuration/1000}s - assuming answered")
            Log.i(TAG, "Step 2: Calling prospect...")

            // Step 3: Call prospect
            makeCall(contactNumber)

            // Step 4: Wait 12 seconds for prospect to answer
            handler.postDelayed({
                Log.i(TAG, "12s elapsed - assuming prospect answered")
                Log.i(TAG, "Step 3: Attempting merge...")

                // Step 5: Attempt merge
                handler.postDelayed({
                    mergeCallsToConference()

                    // Step 6: Mark as completed
                    Log.i(TAG, "✓✓ Conference flow complete - marking as COMPLETED")
                    updateCallStatus(callId, "completed")
                    cleanup()

                }, MERGE_DELAY)

            }, SECOND_CALL_WAIT)

        }, FIRST_CALL_WAIT)

        // Overall timeout: 2 minutes max
        handler.postDelayed({
            Log.w(TAG, "Overall timeout (2 min) - cleaning up")
            if (currentCallId != null) {
                updateCallStatus(callId, "failed")
            }
            cleanup()
        }, 120000)
    }

    /**
     * Make outgoing call using SIM
     */
    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "→ Call initiated to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to make call: ${e.message}")
        }
    }

    /**
     * Merge active calls into conference
     */
    private fun mergeCallsToConference() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            val intent = Intent("android.intent.action.PERFORM_CDMA_CALL_WAITING_ACTION")
            intent.putExtra("com.android.phone.MERGE_CALLS", true)
            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Merge attempted")
            Log.i(TAG, "Note: If merge doesn't work automatically, tap 'Merge' button on phone")

        } catch (e: Exception) {
            Log.e(TAG, "Merge failed: ${e.message}")
            Log.i(TAG, "User may need to manually tap 'Merge' button")
        }
    }

    /**
     * Update call status in CRM database
     */
    private fun updateCallStatus(callId: Int, status: String) {
        if (currentCallId == null) {
            Log.d(TAG, "Status already updated, skipping duplicate")
            return
        }

        currentCallId = null // Prevent duplicate updates

        Thread {
            try {
                val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
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
                    Log.i(TAG, "✓ Call $callId marked as $status in database")
                } else {
                    Log.e(TAG, "Failed to update status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call status: ${e.message}", e)
            }
        }.start()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        currentCallId = null
        Log.d(TAG, "Cleanup complete")
    }

    fun shutdown() {
        cleanup()
    }
}
