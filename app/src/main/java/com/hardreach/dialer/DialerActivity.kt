package com.hardreach.dialer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Main dialer activity with bottom navigation
 * Uses fragments for Keypad, Recents, and Contacts
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var navKeypad: LinearLayout
    private lateinit var navRecents: LinearLayout
    private lateinit var navContacts: LinearLayout
    private lateinit var btnSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        navKeypad = findViewById(R.id.nav_keypad)
        navRecents = findViewById(R.id.nav_recents)
        navContacts = findViewById(R.id.nav_contacts)
        btnSettings = findViewById(R.id.btn_settings)

        // Setup bottom navigation click listeners
        navKeypad.setOnClickListener {
            selectTab(0)
            showFragment(KeypadFragment())
        }

        navRecents.setOnClickListener {
            selectTab(1)
            showFragment(RecentsFragment())
        }

        navContacts.setOnClickListener {
            selectTab(2)
            showFragment(ContactsFragment())
        }

        // Setup settings button
        btnSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // Show keypad fragment by default
        if (savedInstanceState == null) {
            selectTab(0)
            showFragment(KeypadFragment())
        }
    }

    private fun selectTab(index: Int) {
        // Reset all tabs
        resetTab(navKeypad)
        resetTab(navRecents)
        resetTab(navContacts)

        // Highlight selected tab
        when (index) {
            0 -> highlightTab(navKeypad)
            1 -> highlightTab(navRecents)
            2 -> highlightTab(navContacts)
        }
    }

    private fun resetTab(tab: LinearLayout) {
        val icon = tab.getChildAt(0) as ImageView
        val label = tab.getChildAt(1) as TextView

        icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun highlightTab(tab: LinearLayout) {
        val icon = tab.getChildAt(0) as ImageView
        val label = tab.getChildAt(1) as TextView

        icon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
        label.setTextColor(ContextCompat.getColor(this, R.color.primary))
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
