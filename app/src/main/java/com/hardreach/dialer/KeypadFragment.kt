package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class KeypadFragment : Fragment() {

    private lateinit var phoneNumberDisplay: EditText
    private lateinit var btnBackspace: ImageButton
    private lateinit var btnCall: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_keypad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        phoneNumberDisplay = view.findViewById(R.id.phone_number_display)
        btnBackspace = view.findViewById(R.id.btn_backspace)
        btnCall = view.findViewById(R.id.btn_call)

        setupDialpad(view)
        setupActions()

        // Check for DIAL intent and pre-fill number
        activity?.intent?.data?.schemeSpecificPart?.let { number ->
            phoneNumberDisplay.setText(number)
        }
    }

    private fun setupDialpad(view: View) {
        val digits = mapOf(
            R.id.btn_0 to "0",
            R.id.btn_1 to "1",
            R.id.btn_2 to "2",
            R.id.btn_3 to "3",
            R.id.btn_4 to "4",
            R.id.btn_5 to "5",
            R.id.btn_6 to "6",
            R.id.btn_7 to "7",
            R.id.btn_8 to "8",
            R.id.btn_9 to "9",
            R.id.btn_star to "*",
            R.id.btn_hash to "#"
        )

        digits.forEach { (buttonId, digit) ->
            view.findViewById<Button>(buttonId).setOnClickListener {
                phoneNumberDisplay.append(digit)
            }
        }
    }

    private fun setupActions() {
        btnBackspace.setOnClickListener {
            val text = phoneNumberDisplay.text.toString()
            if (text.isNotEmpty()) {
                phoneNumberDisplay.setText(text.substring(0, text.length - 1))
                phoneNumberDisplay.setSelection(phoneNumberDisplay.text.length)
            }
        }

        btnCall.setOnClickListener {
            makeCall(phoneNumberDisplay.text.toString())
        }
    }

    private fun makeCall(number: String) {
        if (number.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Phone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        startActivity(intent)
    }

    fun setPhoneNumber(number: String) {
        phoneNumberDisplay.setText(number)
        phoneNumberDisplay.setSelection(phoneNumberDisplay.text.length)
    }
}
