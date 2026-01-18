package com.hardreach.dialer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class DialerActivity : AppCompatActivity() {

    private lateinit var navKeypad: Button
    private lateinit var navRecents: Button
    private lateinit var navContacts: Button
    private lateinit var btnSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
