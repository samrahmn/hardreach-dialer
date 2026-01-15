package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Full dialer activity with keypad - shown when Hardreach is set as default phone app
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var phoneNumberDisplay: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle DIAL intent - extract phone number if provided
        val phoneNumber = when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_VIEW -> {
                intent.data?.schemeSpecificPart ?: ""
            }
            else -> ""
        }

        // Create full dialer UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val title = TextView(this).apply {
            text = "Hardreach Dialer"
            textSize = 24f
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
        }

        // Phone number display
        phoneNumberDisplay = EditText(this).apply {
            setText(phoneNumber)
            textSize = 32f
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = true
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(16, 32, 16, 32)
        }

        // Keypad grid (3x4)
        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            setPadding(0, 32, 0, 32)
        }

        val buttons = arrayOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "*", "0", "#"
        )

        buttons.forEach { digit ->
            val button = Button(this).apply {
                text = digit
                textSize = 24f
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    phoneNumberDisplay.append(digit)
                }
            }
            keypad.addView(button)
        }

        // Call button
        val callButton = Button(this).apply {
            text = "📞 CALL"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 8)
            }
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                makeCall()
            }
        }

        // Backspace button
        val backspaceButton = Button(this).apply {
            text = "⌫ Delete"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setOnClickListener {
                val text = phoneNumberDisplay.text.toString()
                if (text.isNotEmpty()) {
                    phoneNumberDisplay.setText(text.substring(0, text.length - 1))
                }
            }
        }

        // Settings button
        val settingsButton = Button(this).apply {
            text = "Settings"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@DialerActivity, MainActivity::class.java))
            }
        }

        layout.addView(title)
        layout.addView(phoneNumberDisplay)
        layout.addView(keypad)
        layout.addView(callButton)
        layout.addView(backspaceButton)
        layout.addView(settingsButton)

        setContentView(layout)
    }

    private fun makeCall() {
        val number = phoneNumberDisplay.text.toString()
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Phone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        startActivity(intent)
    }
}
