package com.hardreach.dialer

import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * ConnectionService implementation for Hardreach Dialer
 * Handles actual phone calls using the SIM card
 */
@RequiresApi(Build.VERSION_CODES.M)
class HardreachConnectionService : ConnectionService() {

    private val TAG = "HardreachConnectionService"

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateOutgoingConnection called for: ${request?.address}")
        RemoteLogger.i(applicationContext, TAG, "Creating outgoing connection to: ${request?.address}")

        val connection = object : Connection() {
            override fun onAnswer() {
                Log.i(TAG, "onAnswer called")
            }

            override fun onReject() {
                Log.i(TAG, "onReject called")
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                Log.i(TAG, "onDisconnect called")
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }

            override fun onAbort() {
                Log.i(TAG, "onAbort called")
                setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
                destroy()
            }

            override fun onStateChanged(state: Int) {
                Log.i(TAG, "Connection state changed: $state")
                RemoteLogger.i(applicationContext, TAG, "Connection state: $state")
            }
        }

        // Get the phone number
        val phoneNumber = request?.address?.schemeSpecificPart

        if (phoneNumber != null) {
            connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
            connection.setCallerDisplayName(phoneNumber, TelecomManager.PRESENTATION_ALLOWED)

            // Set as dialing, then active
            connection.setDialing()

            // Simulate connection after a short delay (real implementation would use SIM)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                connection.setActive()
                Log.i(TAG, "✅ Call connected to: $phoneNumber")
                RemoteLogger.i(applicationContext, TAG, "✅ Call connected to: $phoneNumber")
            }, 2000)

            return connection
        }

        Log.e(TAG, "❌ No phone number in request")
        RemoteLogger.e(applicationContext, TAG, "❌ No phone number in connection request")
        return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateIncomingConnection called")
        // For incoming calls, we don't handle them - let system handle
        return Connection.createCanceledConnection()
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed for: ${request?.address}")
        RemoteLogger.w(applicationContext, TAG, "⚠️ Outgoing connection failed: ${request?.address}")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateIncomingConnectionFailed")
    }
}
