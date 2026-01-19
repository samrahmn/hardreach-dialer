package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class DialerActivity : AppCompatActivity() {

    private lateinit var navKeypad: Button
    private lateinit var navRecents: Button
    private lateinit var navContacts: Button
    private lateinit var btnSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming CALL intents - place the call via TelecomManager
        if (handleCallIntent(intent)) {
            return
        }

        setContentView(R.layout.activity_dialer)

        navKeypad = findViewById(R.id.nav_keypad)
        navRecents = findViewById(R.id.nav_recents)
        navContacts = findViewById(R.id.nav_contacts)
        btnSettings = findViewById(R.id.btn_settings)

        navKeypad.setOnClickListener {
            showFragment(KeypadFragment())
        }

        navRecents.setOnClickListener {
            showFragment(RecentsFragment())
        }

        navContacts.setOnClickListener {
            showFragment(ContactsFragment())
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        if (savedInstanceState == null) {
            showFragment(KeypadFragment())
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleCallIntent(it) }
    }

    /**
     * Handle incoming ACTION_CALL intents by placing the call via TelecomManager.
     * This prevents the circular intent problem where our app intercepts its own call intents.
     * Returns true if a call intent was handled, false otherwise.
     */
    private fun handleCallIntent(intent: Intent): Boolean {
        val action = intent.action
        val data = intent.data

        // Only handle CALL intents with tel: URIs
        if ((action == Intent.ACTION_CALL || action == "android.intent.action.CALL_PRIVILEGED")
            && data != null && data.scheme == "tel") {

            val phoneNumber = data.schemeSpecificPart
            if (phoneNumber.isNullOrBlank()) {
                return false
            }

            android.util.Log.i("DialerActivity", "Handling ACTION_CALL for: $phoneNumber")

            // Check permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("DialerActivity", "CALL_PHONE permission not granted")
                finish()
                return true
            }

            // Place call via TelecomManager (bypasses our app's intent filter)
            placeCallViaTelecom(phoneNumber)
            finish()
            return true
        }

        return false
    }

    /**
     * Place call using TelecomManager which routes to the system telephony
     */
    private fun placeCallViaTelecom(phoneNumber: String) {
        try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val uri = Uri.fromParts("tel", phoneNumber, null)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telecomManager?.placeCall(uri, null)
                android.util.Log.i("DialerActivity", "Call placed via TelecomManager: $phoneNumber")
            } else {
                // Fallback for older Android versions
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
                callIntent.setPackage("com.android.phone")
                startActivity(callIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("DialerActivity", "Failed to place call: ${e.message}", e)
        }
    }
}
