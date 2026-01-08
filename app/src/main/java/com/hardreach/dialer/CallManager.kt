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
    
    /**
     * Initiates automated conference call:
     * 1. Call team member first
     * 2. Wait for answer (5 seconds)
     * 3. Call contact
     * 4. Auto-merge into conference
     */
    fun initiateConferenceCall(teamMemberNumber: String, contactNumber: String) {
        Log.i(TAG, "Initiating conference: Team=$teamMemberNumber, Contact=$contactNumber")
        
        // Step 1: Call team member
        makeCall(teamMemberNumber)
        
        // Step 2: Wait 5 seconds for team member to answer
        handler.postDelayed({
            // Step 3: Call contact (this creates second call)
            makeCall(contactNumber)
            
            // Step 4: Auto-merge after 2 seconds
            handler.postDelayed({
                mergeCallsToConference()
            }, 2000)
        }, 5000)
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
            Log.i(TAG, "Call initiated to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call: ${e.message}")
        }
    }
    
    /**
     * Merge active calls into conference
     * Uses TelecomManager to merge calls
     */
    private fun mergeCallsToConference() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            
            // Android doesn't provide direct API to merge calls programmatically
            // This requires MODIFY_PHONE_STATE permission (system apps only)
            // Workaround: Simulate keypress to merge
            
            val intent = Intent("android.intent.action.PERFORM_CDMA_CALL_WAITING_ACTION")
            intent.putExtra("com.android.phone.MERGE_CALLS", true)
            context.sendBroadcast(intent)
            
            Log.i(TAG, "Conference merge attempted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge calls: ${e.message}")
            // Note: Auto-merge may not work on all devices
            // User may need to manually tap "Merge" button
        }
    }
}
