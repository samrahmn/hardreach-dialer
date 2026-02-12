package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Call manager that waits for actual call connection before placing second call
 * Uses InCallService callbacks for reliable state detection
 * Supports step-by-step confirmation when auto-accept is disabled
 */
class CallManager(private val context: Context) {

    private val TAG = "CallManager"
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    // Timeouts
    private val FIRST_CALL_TIMEOUT = 60000L  // 60s max wait for first call to connect
    private val SECOND_CALL_TIMEOUT = 60000L // 60s max wait for second call to connect

    // Track call IDs currently being processed to prevent duplicates
    companion object {
        private val processingCallIds = mutableSetOf<Int>()

        @Synchronized
        fun isProcessing(callId: Int): Boolean = processingCallIds.contains(callId)

        @Synchronized
        fun startProcessing(callId: Int): Boolean {
            return if (processingCallIds.contains(callId)) {
                false // Already processing
            } else {
                processingCallIds.add(callId)
                true // Successfully started
            }
        }

        @Synchronized
        fun stopProcessing(callId: Int) {
            processingCallIds.remove(callId)
        }
    }

    private var currentCallId: Int? = null
    private var pendingTeamNumber: String? = null
    private var pendingContactNumber: String? = null
    private var callStartTime = 0L
    private var timeoutRunnable: Runnable? = null
    private var autoAcceptMode = true

    /**
     * Conference call flow with actual connection detection:
     * 1. Call team member (with confirmation if auto-accept OFF)
     * 2. Wait for InCallService callback (STATE_ACTIVE) = call connected
     * 3. Once connected: call prospect (with confirmation if auto-accept OFF)
     * 4. Wait for second call to connect
     * 5. Attempt merge (with confirmation if auto-accept OFF)
     * 6. Mark as completed
     */
    fun initiateConferenceCall(callId: Int, teamMemberNumber: String, contactNumber: String) {
        // Prevent duplicate processing of the same call
        if (!startProcessing(callId)) {
            Log.w(TAG, "Call $callId already being processed - skipping duplicate")
            RemoteLogger.w(context, TAG, "Call $callId already processing - skipping duplicate")
            return
        }

        // Check auto-accept preference
        val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        autoAcceptMode = prefs.getBoolean("auto_accept", false)

        RemoteLogger.i(context, TAG, "=== Conference Call Started ===")
        RemoteLogger.i(context, TAG, "Call ID: $callId | Team: $teamMemberNumber | Prospect: $contactNumber")
        RemoteLogger.i(context, TAG, "Auto-accept mode: $autoAcceptMode")
        Log.i(TAG, "=== Connection-Based Conference Call ===")
        Log.i(TAG, "Call ID: $callId")
        Log.i(TAG, "Team Member: $teamMemberNumber")
        Log.i(TAG, "Prospect: $contactNumber")
        Log.i(TAG, "Auto-accept: $autoAcceptMode")

        currentCallId = callId
        pendingTeamNumber = teamMemberNumber
        pendingContactNumber = contactNumber
        callStartTime = System.currentTimeMillis()

        // Reset states
        HardreachInCallService.reset()
        HardreachInCallService.isCrmCall = true  // Mark as CRM call for auto-mute
        HardreachInCallService.teamMemberNumber = teamMemberNumber
        HardreachInCallService.prospectNumber = contactNumber
        ConfirmCallActivity.reset()

        if (autoAcceptMode) {
            // Auto-accept: proceed directly
            startFirstCall(callId, teamMemberNumber, contactNumber)
        } else {
            // Manual mode: show confirmation for first call
            showFirstCallConfirmation(callId, teamMemberNumber, contactNumber)
        }
    }

    private fun showFirstCallConfirmation(callId: Int, teamMemberNumber: String, contactNumber: String) {
        ConfirmCallActivity.onFirstCallAccepted = {
            startFirstCall(callId, teamMemberNumber, contactNumber)
        }
        ConfirmCallActivity.onDeclined = {
            cleanup()
        }

        val intent = Intent(context, ConfirmCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("team_member_number", teamMemberNumber)
            putExtra("contact_number", contactNumber)
            putExtra("step", ConfirmCallActivity.STEP_FIRST_CALL)
        }
        context.startActivity(intent)
    }

    private fun startFirstCall(callId: Int, teamMemberNumber: String, contactNumber: String) {
        // Setup callback for when first call connects
        HardreachInCallService.onFirstCallConnected = {
            Log.i(TAG, "✓✓ First call connected callback received!")
            RemoteLogger.i(context, TAG, "✓✓ First call connected")
            StatusManager.log("✓ Team member answered")

            // Cancel timeout
            timeoutRunnable?.let { handler.removeCallbacks(it) }

            // Proceed to second call (with or without confirmation)
            handler.postDelayed({
                if (autoAcceptMode) {
                    callProspect(callId, contactNumber)
                } else {
                    showSecondCallConfirmation(callId, contactNumber)
                }
            }, 1000)
        }

        // Step 1: Call team member
        StatusManager.callStarted(teamMemberNumber)
        StatusManager.log("Calling team member: $teamMemberNumber")
        makeCall(teamMemberNumber)

        RemoteLogger.i(context, TAG, "Step 1: Calling team member...")
        Log.i(TAG, "Step 1: Calling team member...")

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

    private fun showSecondCallConfirmation(callId: Int, contactNumber: String) {
        ConfirmCallActivity.onSecondCallAccepted = {
            callProspect(callId, contactNumber)
        }
        ConfirmCallActivity.onDeclined = {
            // End first call and cleanup
            updateCallStatus(callId, "failed")
            cleanup()
        }

        val intent = Intent(context, ConfirmCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("team_member_number", pendingTeamNumber)
            putExtra("contact_number", contactNumber)
            putExtra("step", ConfirmCallActivity.STEP_SECOND_CALL)
        }
        context.startActivity(intent)
    }

    /**
     * Called after first call is connected.
     * Sets up callback so merge fires instantly when prospect answers.
     */
    private fun callProspect(callId: Int, contactNumber: String) {
        RemoteLogger.i(context, TAG, "Step 2: Calling prospect: $contactNumber")
        StatusManager.callStarted(contactNumber)
        StatusManager.log("Calling prospect: $contactNumber")

        // Wire up instant merge — fires the moment call #2 goes STATE_ACTIVE
        // Nulled immediately on first invocation to prevent duplicate merges
        // when the carrier rebuilds conference legs as new Call objects
        HardreachInCallService.onSecondCallConnected = {
            // Fire once only — carrier creates new Call objects after conference()
            HardreachInCallService.onSecondCallConnected = null

            Log.i(TAG, "✓ Second call connected - merging immediately")
            RemoteLogger.i(context, TAG, "✓ Second call connected - merging NOW")
            StatusManager.callConnected(pendingContactNumber ?: "prospect")

            // Cancel the second call timeout
            timeoutRunnable?.let { handler.removeCallbacks(it) }

            attemptMergeAndComplete(callId)
        }

        makeCall(contactNumber)

        // Timeout if prospect doesn't answer within 60s
        timeoutRunnable = Runnable {
            if (HardreachInCallService.onSecondCallConnected != null) {
                Log.w(TAG, "✗ Second call timeout - prospect didn't answer")
                RemoteLogger.w(context, TAG, "✗ Second call timeout - no answer from prospect")
                StatusManager.callFailed("Prospect didn't answer (60s timeout)")
                HardreachInCallService.onSecondCallConnected = null
                updateCallStatus(callId, "failed")
                cleanup()
            }
        }
        handler.postDelayed(timeoutRunnable!!, SECOND_CALL_TIMEOUT)
    }

    /**
     * Attempt merge and mark as completed
     */
    private fun attemptMergeAndComplete(callId: Int) {
        StatusManager.log("Both calls active - ready to merge")

        if (autoAcceptMode) {
            // Auto-merge
            performMerge(callId)
        } else {
            // Show merge confirmation
            showMergeConfirmation(callId)
        }
    }

    private fun showMergeConfirmation(callId: Int) {
        ConfirmCallActivity.onMergeAccepted = {
            performMerge(callId)
        }
        ConfirmCallActivity.onDeclined = {
            // Don't merge, but still mark as completed (calls are active)
            updateCallStatus(callId, "completed")
            StatusManager.log("Merge declined - calls remain separate")
            cleanup()
        }

        val intent = Intent(context, ConfirmCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("team_member_number", pendingTeamNumber)
            putExtra("contact_number", pendingContactNumber)
            putExtra("step", ConfirmCallActivity.STEP_MERGE)
        }
        context.startActivity(intent)
    }

    private fun performMerge(callId: Int) {
        StatusManager.mergingCalls()
        mergeCallsToConference()

        // Mark as completed
        RemoteLogger.i(context, TAG, "✓ Conference flow complete - marking as COMPLETED")
        Log.i(TAG, "✓✓ Conference flow complete - marking as COMPLETED")
        updateCallStatus(callId, "completed")
        StatusManager.log("✓ Conference call completed!")
        cleanup()
    }

    /**
     * Make outgoing call using TelecomManager.placeCall()
     * This properly routes to system telephony without going through our DialerActivity
     * InCallService will receive events because we're the default dialer
     */
    private fun makeCall(phoneNumber: String) {
        try {
            Log.i(TAG, "=== makeCall() called with: '$phoneNumber' ===")
            RemoteLogger.i(context, TAG, "=== makeCall() input: '$phoneNumber' ===")

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", phoneNumber, null)

            Log.i(TAG, "URI created: $uri")
            RemoteLogger.i(context, TAG, "URI: $uri")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                telecomManager.placeCall(uri, null)
                Log.i(TAG, "→ Call placed via TelecomManager to $phoneNumber")
                RemoteLogger.i(context, TAG, "→ Call placed via TelecomManager to $phoneNumber")
            } else {
                // Fallback for older Android - use explicit system dialer intent
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage("com.android.phone")
                }
                context.startActivity(intent)
                Log.i(TAG, "→ Call initiated via intent to $phoneNumber")
                RemoteLogger.i(context, TAG, "→ Call initiated via intent to $phoneNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to make call: ${e.message}")
            RemoteLogger.e(context, TAG, "✗ Failed to make call: ${e.message}")
        }
    }

    /**
     * Merge active calls into conference using InCallService
     */
    private fun mergeCallsToConference() {
        try {
            Log.i(TAG, "Attempting merge via InCallService...")
            RemoteLogger.i(context, TAG, "Attempting merge via InCallService...")

            val merged = HardreachInCallService.mergeCalls()

            if (merged) {
                Log.i(TAG, "✓ Merge command sent successfully")
                RemoteLogger.i(context, TAG, "✓ Merge command sent")
            } else {
                Log.w(TAG, "Merge returned false - may need manual merge")
                RemoteLogger.w(context, TAG, "Merge failed - tap Merge button manually")
                StatusManager.log("Tap 'Merge' button if calls aren't merged")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed: ${e.message}")
            RemoteLogger.e(context, TAG, "Merge exception: ${e.message}")
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

        // Release processing lock before nulling currentCallId
        stopProcessing(callId)
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
        // Release processing lock for this call ID
        currentCallId?.let { stopProcessing(it) }

        handler.removeCallbacksAndMessages(null)
        currentCallId = null
        pendingContactNumber = null
        pendingTeamNumber = null
        timeoutRunnable = null
        HardreachInCallService.reset()
        StatusManager.idle()
        Log.d(TAG, "Cleanup complete")
    }

    fun shutdown() {
        cleanup()
    }
}
