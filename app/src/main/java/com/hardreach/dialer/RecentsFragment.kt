package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RecentsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: RecentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recents, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recents_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadRecents()
    }

    override fun onResume() {
        super.onResume()
        // Reload recents when fragment becomes visible
        loadRecents()
    }

    private fun loadRecents() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val calls = mutableListOf<RecentCall>()

        val cursor: Cursor? = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            ),
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < 50) {
                val number = it.getString(0) ?: "Unknown"
                val name = it.getString(1)
                val date = it.getLong(2)
                val type = it.getInt(3)

                calls.add(RecentCall(number, name, date, type))
                count++
            }
        }

        if (calls.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            adapter = RecentsAdapter(calls) { number ->
                makeCall(number)
            }
            recyclerView.adapter = adapter
        }
    }

    private fun makeCall(number: String) {
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
}
