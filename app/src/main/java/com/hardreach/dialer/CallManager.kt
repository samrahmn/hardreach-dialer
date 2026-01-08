package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log

class CallManager(private val context: Context) {

    private val TAG = "CallManager"
    private val handler = Handler(Looper.getMainLooper())
    private val stateManager = CallStateManager(context)

    // Increased from 8s to 15s for international calls
    private val FIRST_CALL_ANSWER_WAIT = 15000L

    // Check for prospect answer every 2 seconds
    private val PROSPECT_ANSWER_CHECK_INTERVAL = 2000L
    private val MAX_PROSPECT_WAIT = 20000L // Max 20 seconds to wait for prospect answer

    /**
     * Initiates automated conference call with robust state monitoring:
     * 1. Start monitoring call states
     * 2. Call team member first
     * 3. Wait up to 15 seconds for team member to answer
     * 4. If answered, call prospect
     * 5. Wait for prospect to answer (check every 2s, max 20s)
     * 6. When both connected, merge
     * 7. Mark as completed/failed automatically based on outcomes
     */
    fun initiateConferenceCall(callId: Int, teamMemberNumber: String, contactNumber: String) {
        Log.i(TAG, "=== Initiating Conference Call ===")
        Log.i(TAG, "Call ID: $callId")
        Log.i(TAG, "Team Member: $teamMemberNumber")
        Log.i(TAG, "Prospect: $contactNumber")

        // Start monitoring call states
        stateManager.startMonitoring(callId, teamMemberNumber, contactNumber)

        // Step 1: Call team member
        makeCall(teamMemberNumber)
        Log.i(TAG, "Step 1: Calling team member...")

        // Step 2: Wait 15 seconds to check if team member answered
        handler.postDelayed({
            if (stateManager.isFirstCallAnswered()) {
                Log.i(TAG, "✓ Team member answered - proceeding to call prospect")

                // Step 3: Call prospect (this creates second call)
                stateManager.setSecondCallInProgress()
                makeCall(contactNumber)
                Log.i(TAG, "Step 2: Calling prospect...")

                // Step 4: Poll for prospect answer, then merge when both connected
                waitForProspectAndMerge()

            } else {
                Log.w(TAG, "✗ Team member did not answer within 15 seconds")
                Log.w(TAG, "Cancelling call flow - state manager will mark as failed")
                // State manager will handle marking as failed when call ends
            }
        }, FIRST_CALL_ANSWER_WAIT)
    }

    /**
     * Polls every 2 seconds to check if prospect answered
     * Once both calls are connected, attempts merge
     * Max wait: 20 seconds
     */
    private fun waitForProspectAndMerge() {
        var waitTime = 0L

        val checkRunnable = object : Runnable {
            override fun run() {
                waitTime += PROSPECT_ANSWER_CHECK_INTERVAL

                if (stateManager.isSecondCallAnswered()) {
                    // Prospect answered!
                    Log.i(TAG, "✓✓ Both calls connected - attempting merge")

                    // Small delay to ensure both calls are stable
                    handler.postDelayed({
                        mergeCallsToConference()
                    }, 1000)

                } else if (waitTime >= MAX_PROSPECT_WAIT) {
                    // Timeout waiting for prospect
                    Log.w(TAG, "✗ Prospect did not answer within 20 seconds")
                    Log.w(TAG, "Merge cancelled - state manager will mark as failed")
                    // State manager will handle marking as failed

                } else {
                    // Keep checking
                    Log.d(TAG, "Waiting for prospect to answer... (${waitTime/1000}s elapsed)")
                    handler.postDelayed(this, PROSPECT_ANSWER_CHECK_INTERVAL)
                }
            }
        }

        // Start checking after 3 seconds (give time for call to ring)
        handler.postDelayed(checkRunnable, 3000)
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
     * Only called when both calls are confirmed connected
     */
    private fun mergeCallsToConference() {
        try {
            // Verify both calls are still connected before merge
            if (!stateManager.areBothCallsConnected()) {
                Log.w(TAG, "Cannot merge - both calls not connected")
                return
            }

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            // Android doesn't provide direct API to merge calls programmatically
            // This requires MODIFY_PHONE_STATE permission (system apps only)
            // Workaround: Simulate keypress to merge

            val intent = Intent("android.intent.action.PERFORM_CDMA_CALL_WAITING_ACTION")
            intent.putExtra("com.android.phone.MERGE_CALLS", true)
            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Conference merge attempted")
            Log.i(TAG, "Note: If merge doesn't work automatically, tap 'Merge' button on phone")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge calls: ${e.message}")
            Log.i(TAG, "User will need to manually tap 'Merge' button")
        }
    }

    fun cleanup() {
        stateManager.stopMonitoring()
    }
}
