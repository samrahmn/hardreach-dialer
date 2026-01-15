package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full functional dialer with keypad, call history, and contacts
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var phoneNumberDisplay: EditText
    private lateinit var searchBox: EditText
    private lateinit var tabKeypad: Button
    private lateinit var tabRecents: Button
    private lateinit var tabContacts: Button
    private lateinit var keypadLayout: LinearLayout
    private lateinit var recentsLayout: ScrollView
    private lateinit var contactsLayout: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle DIAL intent
        val phoneNumber = when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_VIEW -> {
                intent.data?.schemeSpecificPart ?: ""
            }
            else -> ""
        }

        // Main container
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF2196F3.toInt())
        }

        val title = TextView(this).apply {
            text = "Hardreach Dialer"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val settingsBtn = Button(this).apply {
            text = "⚙"
            textSize = 18f
            setOnClickListener {
                startActivity(Intent(this@DialerActivity, MainActivity::class.java))
            }
        }

        header.addView(title)
        header.addView(settingsBtn)

        // Search box (for contacts search)
        searchBox = EditText(this).apply {
            hint = "Search contacts..."
            setPadding(16, 16, 16, 16)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (contactsLayout.visibility == View.VISIBLE) {
                        loadContacts(s.toString())
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        // Tabs
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tabKeypad = Button(this).apply {
            text = "Keypad"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showKeypad() }
        }

        tabRecents = Button(this).apply {
            text = "Recents"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showRecents() }
        }

        tabContacts = Button(this).apply {
            text = "Contacts"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showContacts() }
        }

        tabsLayout.addView(tabKeypad)
        tabsLayout.addView(tabRecents)
        tabsLayout.addView(tabContacts)

        // === KEYPAD LAYOUT ===
        keypadLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        phoneNumberDisplay = EditText(this).apply {
            setText(phoneNumber)
            textSize = 28f
            gravity = Gravity.CENTER
            isFocusable = false
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(16, 24, 16, 24)
        }

        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            setPadding(0, 16, 0, 16)
        }

        val buttons = arrayOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "*", "0", "#"
        )

        buttons.forEach { digit ->
            val btn = Button(this).apply {
                text = digit
                textSize = 24f
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 120
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener { phoneNumberDisplay.append(digit) }
            }
            keypad.addView(btn)
        }

        val callButton = Button(this).apply {
            text = "📞 CALL"
            textSize = 20f
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 8) }
            setOnClickListener { makeCall(phoneNumberDisplay.text.toString()) }
        }

        val backspaceButton = Button(this).apply {
            text = "⌫ Delete"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val text = phoneNumberDisplay.text.toString()
                if (text.isNotEmpty()) {
                    phoneNumberDisplay.setText(text.substring(0, text.length - 1))
                }
            }
        }

        keypadLayout.addView(phoneNumberDisplay)
        keypadLayout.addView(keypad)
        keypadLayout.addView(callButton)
        keypadLayout.addView(backspaceButton)

        // === RECENTS LAYOUT ===
        recentsLayout = ScrollView(this).apply {
            visibility = View.GONE
        }

        // === CONTACTS LAYOUT ===
        contactsLayout = ScrollView(this).apply {
            visibility = View.GONE
        }

        // Assemble
        mainLayout.addView(header)
        mainLayout.addView(searchBox)
        mainLayout.addView(tabsLayout)
        mainLayout.addView(keypadLayout)
        mainLayout.addView(recentsLayout)
        mainLayout.addView(contactsLayout)

        setContentView(mainLayout)
    }

    private fun showKeypad() {
        keypadLayout.visibility = View.VISIBLE
        recentsLayout.visibility = View.GONE
        contactsLayout.visibility = View.GONE
        searchBox.visibility = View.GONE
        tabKeypad.setBackgroundColor(0xFF2196F3.toInt())
        tabRecents.setBackgroundColor(0xFFCCCCCC.toInt())
        tabContacts.setBackgroundColor(0xFFCCCCCC.toInt())
    }

    private fun showRecents() {
        keypadLayout.visibility = View.GONE
        recentsLayout.visibility = View.VISIBLE
        contactsLayout.visibility = View.GONE
        searchBox.visibility = View.GONE
        tabKeypad.setBackgroundColor(0xFFCCCCCC.toInt())
        tabRecents.setBackgroundColor(0xFF2196F3.toInt())
        tabContacts.setBackgroundColor(0xFFCCCCCC.toInt())
        loadRecents()
    }

    private fun showContacts() {
        keypadLayout.visibility = View.GONE
        recentsLayout.visibility = View.GONE
        contactsLayout.visibility = View.VISIBLE
        searchBox.visibility = View.VISIBLE
        tabKeypad.setBackgroundColor(0xFFCCCCCC.toInt())
        tabRecents.setBackgroundColor(0xFFCCCCCC.toInt())
        tabContacts.setBackgroundColor(0xFF2196F3.toInt())
        loadContacts("")
    }

    private fun loadRecents() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            layout.addView(TextView(this).apply {
                text = "Call log permission required"
                textSize = 16f
            })
            recentsLayout.removeAllViews()
            recentsLayout.addView(layout)
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE),
            null, null, CallLog.Calls.DATE + " DESC"
        )

        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        var count = 0

        cursor?.use {
            while (it.moveToNext() && count < 20) {
                val number = it.getString(0) ?: "Unknown"
                val name = it.getString(1) ?: number
                val date = it.getLong(2)
                val type = it.getInt(3)

                val typeIcon = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "📞"
                    CallLog.Calls.OUTGOING_TYPE -> "📲"
                    CallLog.Calls.MISSED_TYPE -> "📵"
                    else -> "📞"
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 12, 8, 12)
                    setBackgroundColor(0xFFF9F9F9.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 8) }
                    setOnClickListener { makeCall(number) }
                }

                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                info.addView(TextView(this).apply {
                    text = "$typeIcon $name"
                    textSize = 16f
                })
                info.addView(TextView(this).apply {
                    text = "$number • ${dateFormat.format(Date(date))}"
                    textSize = 12f
                    setTextColor(0xFF666666.toInt())
                })

                val callBtn = Button(this).apply {
                    text = "📞"
                    textSize = 18f
                    setOnClickListener { makeCall(number) }
                }

                row.addView(info)
                row.addView(callBtn)
                layout.addView(row)
                count++
            }
        }

        if (count == 0) {
            layout.addView(TextView(this).apply {
                text = "No recent calls"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            })
        }

        recentsLayout.removeAllViews()
        recentsLayout.addView(layout)
    }

    private fun loadContacts(filter: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            layout.addView(TextView(this).apply {
                text = "Contacts permission required"
                textSize = 16f
            })
            contactsLayout.removeAllViews()
            contactsLayout.addView(layout)
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val selection = if (filter.isNotEmpty()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        } else null

        val selectionArgs = if (filter.isNotEmpty()) {
            arrayOf("%$filter%")
        } else null

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            selectionArgs,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        var count = 0
        cursor?.use {
            while (it.moveToNext() && count < 50) {
                val name = it.getString(0) ?: "Unknown"
                val number = it.getString(1) ?: ""

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 12, 8, 12)
                    setBackgroundColor(0xFFF9F9F9.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 8) }
                    setOnClickListener { makeCall(number) }
                }

                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                info.addView(TextView(this).apply {
                    text = name
                    textSize = 16f
                })
                info.addView(TextView(this).apply {
                    text = number
                    textSize = 12f
                    setTextColor(0xFF666666.toInt())
                })

                val callBtn = Button(this).apply {
                    text = "📞"
                    textSize = 18f
                    setOnClickListener { makeCall(number) }
                }

                row.addView(info)
                row.addView(callBtn)
                layout.addView(row)
                count++
            }
        }

        if (count == 0) {
            layout.addView(TextView(this).apply {
                text = if (filter.isNotEmpty()) "No contacts found" else "No contacts"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            })
        }

        contactsLayout.removeAllViews()
        contactsLayout.addView(layout)
    }

    private fun makeCall(number: String) {
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
