package com.hardreach.dialer

import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * ConnectionService implementation for Hardreach Dialer
 * This is required for Android to recognize the app as a complete dialer
 */
@RequiresApi(Build.VERSION_CODES.M)
class HardreachConnectionService : ConnectionService() {

    private val TAG = "HardreachConnectionService"

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateOutgoingConnection called")

        // We don't actually handle calls - delegate to system dialer
        // But we need this service for Android to recognize us as a dialer app
        return Connection.createCanceledConnection()
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateIncomingConnection called")
        return Connection.createCanceledConnection()
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateIncomingConnectionFailed")
    }
}
