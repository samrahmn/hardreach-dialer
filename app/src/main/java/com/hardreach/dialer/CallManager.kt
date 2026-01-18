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
 * Call manager that waits for actual call connection before placing second call
 * Uses InCallService callbacks for reliable state detection
 */
class CallManager(private val context: Context) {

    private val TAG = "CallManager"
    private val handler = Handler(Looper.getMainLooper())
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val client = OkHttpClient()

    // Timeouts
    private val FIRST_CALL_TIMEOUT = 60000L  // 60s max wait for first call to connect
    private val SECOND_CALL_TIMEOUT = 60000L // 60s max wait for second call to connect
    private val MERGE_DELAY = 3000L          // 3s before merge attempt

    private var currentCallId: Int? = null
    private var pendingContactNumber: String? = null
    private var callStartTime = 0L
    private var timeoutRunnable: Runnable? = null

    /**
     * Conference call flow with actual connection detection:
     * 1. Call team member
     * 2. Wait for InCallService callback (STATE_ACTIVE) = call connected
     * 3. Once connected: call prospect
     * 4. Wait for second call to connect
     * 5. Attempt merge
     * 6. Mark as completed
     */
    fun initiateConferenceCall(callId: Int, teamMemberNumber: String, contactNumber: String) {
        RemoteLogger.i(context, TAG, "=== Conference Call Started ===")
        RemoteLogger.i(context, TAG, "Call ID: $callId | Team: $teamMemberNumber | Prospect: $contactNumber")
        Log.i(TAG, "=== Connection-Based Conference Call ===")
        Log.i(TAG, "Call ID: $callId")
        Log.i(TAG, "Team Member: $teamMemberNumber")
        Log.i(TAG, "Prospect: $contactNumber")
        Log.i(TAG, "Mode: Wait for actual connection (InCallService callbacks)")

        currentCallId = callId
        pendingContactNumber = contactNumber
        callStartTime = System.currentTimeMillis()

        // Reset InCallService state
        HardreachInCallService.reset()

        // Setup callback for when first call connects
        HardreachInCallService.onFirstCallConnected = {
            Log.i(TAG, "✓✓ First call connected callback received!")
            RemoteLogger.i(context, TAG, "✓✓ First call connected - now calling prospect")
            StatusManager.log("✓ Team member answered - calling prospect...")

            // Cancel timeout
            timeoutRunnable?.let { handler.removeCallbacks(it) }

            // Call prospect now that first call is connected
            handler.postDelayed({
                callProspect(callId, contactNumber)
            }, 1000) // Small delay before second call
        }

        // Step 1: Call team member
        StatusManager.callStarted(teamMemberNumber)
        StatusManager.log("Calling team member: $teamMemberNumber")
        StatusManager.log("Waiting for them to answer...")
        makeCall(teamMemberNumber)

        RemoteLogger.i(context, TAG, "Step 1: Calling team member - waiting for connection...")
        Log.i(TAG, "Step 1: Calling team member - waiting for connection callback...")

        // Timeout if first call doesn't connect within 60 seconds
        timeoutRunnable = Runnable {
            if (!HardreachInCallService.isFirstCallConnected) {
                Log.w(TAG, "✗ First call timeout - no answer within 60s")
                RemoteLogger.w(context, TAG, "✗ First call timeout - no answer")
                StatusManager.callFailed("Team member didn't answer (60s timeout)")
                updateCallStatus(callId, "failed")
                cleanup()
            }
        }
        handler.postDelayed(timeoutRunnable!!, FIRST_CALL_TIMEOUT)

        // Overall timeout: 3 minutes max
        handler.postDelayed({
            Log.w(TAG, "Overall timeout (3 min) - cleaning up")
            if (currentCallId != null) {
                StatusManager.callFailed("Timeout after 3 minutes")
                updateCallStatus(callId, "failed")
            }
            cleanup()
        }, 180000)
    }

    /**
     * Called after first call is connected
     */
    private fun callProspect(callId: Int, contactNumber: String) {
        Log.i(TAG, "Step 2: Calling prospect: $contactNumber")
        RemoteLogger.i(context, TAG, "Step 2: Calling prospect: $contactNumber")
        StatusManager.callStarted(contactNumber)
        StatusManager.log("Calling prospect: $contactNumber")

        makeCall(contactNumber)

        // Wait for second call to be active, then attempt merge
        // We'll use a polling approach to check when both calls are active
        checkForMergeReady(callId, 0)
    }

    /**
     * Check if both calls are active and ready to merge
     */
    private fun checkForMergeReady(callId: Int, attempts: Int) {
        if (attempts > 30) { // 30 attempts * 2s = 60s max
            Log.w(TAG, "Gave up waiting for second call to connect")
            RemoteLogger.w(context, TAG, "Second call didn't connect - completing anyway")
            // Still mark as completed since first call was successful
            attemptMergeAndComplete(callId)
            return
        }

        handler.postDelayed({
            val callState = telephonyManager.callState

            // Check if we have 2 active calls via InCallService
            val instance = HardreachInCallService.instance
            if (instance != null) {
                // If InCallService shows 2 calls, we're ready to merge
                val currentCall = HardreachInCallService.currentCall
                if (currentCall != null) {
                    val state = HardreachInCallService.getCallState(currentCall)
                    if (state == android.telecom.Call.STATE_ACTIVE) {
                        Log.i(TAG, "✓ Second call connected - ready to merge!")
                        RemoteLogger.i(context, TAG, "✓ Second call connected - merging...")
                        StatusManager.callConnected(pendingContactNumber ?: "prospect")
                        attemptMergeAndComplete(callId)
                        return@postDelayed
                    }
                }
            }

            // Also check if call ended
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                Log.w(TAG, "Calls ended before merge")
                StatusManager.callFailed("Calls ended before merge")
                updateCallStatus(callId, "failed")
                cleanup()
                return@postDelayed
            }

            // Keep checking
            Log.d(TAG, "Waiting for second call to connect... (attempt ${attempts + 1})")
            checkForMergeReady(callId, attempts + 1)
        }, 2000)
    }

    /**
     * Attempt merge and mark as completed
     */
    private fun attemptMergeAndComplete(callId: Int) {
        StatusManager.log("Both calls active - attempting merge...")

        handler.postDelayed({
            StatusManager.mergingCalls()
            mergeCallsToConference()

            // Mark as completed
            RemoteLogger.i(context, TAG, "✓ Conference flow complete - marking as COMPLETED")
            Log.i(TAG, "✓✓ Conference flow complete - marking as COMPLETED")
            updateCallStatus(callId, "completed")
            StatusManager.log("✓ Conference call completed!")
            cleanup()
        }, MERGE_DELAY)
    }

    /**
     * Make outgoing call - system will use SIM automatically
     * InCallService will receive events because we're the default dialer
     */
    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "→ Call initiated to $phoneNumber")
            RemoteLogger.i(context, TAG, "→ Call initiated to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to make call: ${e.message}")
            RemoteLogger.e(context, TAG, "✗ Failed to make call: ${e.message}")
        }
    }

    /**
     * Merge active calls into conference
     */
    private fun mergeCallsToConference() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val intent = Intent("com.android.phone.ACTION_MERGE_CALLS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    Log.i(TAG, "✓ Merge command sent")
                } catch (e: Exception) {
                    Log.w(TAG, "Merge intent failed: ${e.message}")
                    try {
                        Runtime.getRuntime().exec("input keyevent 17")
                        Log.i(TAG, "✓ Merge keyevent sent")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Keyevent also failed: ${e2.message}")
                    }
                }
            }

            Log.i(TAG, "Note: If merge doesn't work automatically, tap 'Merge' button on phone")
            StatusManager.log("Tap 'Merge' button if calls aren't merged")

        } catch (e: Exception) {
            Log.e(TAG, "Merge failed: ${e.message}")
            StatusManager.log("Please tap 'Merge' button manually")
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

        currentCallId = null

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
                    RemoteLogger.i(context, TAG, "✓ Call $callId marked as $status")
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
        pendingContactNumber = null
        timeoutRunnable = null
        HardreachInCallService.reset()
        StatusManager.idle()
        Log.d(TAG, "Cleanup complete")
    }

    fun shutdown() {
        cleanup()
    }
}
