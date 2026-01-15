package com.hardreach.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal dialer activity shown when Hardreach is set as default phone app
 * Just redirects to the system dialer for manual calls
 */
class DialerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle DIAL intent - extract phone number if provided
        val phoneNumber = when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_VIEW -> {
                intent.data?.schemeSpecificPart ?: ""
            }
            else -> ""
        }

        // Simple layout - just show that this is for auto-dialing
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "Hardreach Auto-Dialer"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val message = TextView(this).apply {
            text = if (phoneNumber.isNotEmpty()) {
                "Number: $phoneNumber\n\n" +
                "This app is for automatic conference calls from your CRM.\n\n" +
                "Use your phone's contacts app for regular calls."
            } else {
                "This app is for automatic conference calls from your CRM.\n\n" +
                "To make regular calls, use your phone's contacts app.\n\n" +
                "Auto-merge is now enabled with built-in APIs!"
            }
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }

        val openMainButton = Button(this).apply {
            text = "Open Settings"
            setOnClickListener {
                startActivity(Intent(this@DialerActivity, MainActivity::class.java))
                finish()
            }
        }

        layout.addView(title)
        layout.addView(message)
        layout.addView(openMainButton)

        setContentView(layout)
    }
}
