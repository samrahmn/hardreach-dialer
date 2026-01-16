package com.hardreach.dialer

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class RecentCall(
    val number: String,
    val name: String?,
    val date: Long,
    val type: Int
)

class RecentsAdapter(
    private val calls: List<RecentCall>,
    private val onCallClick: (String) -> Unit
) : RecyclerView.Adapter<RecentsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val callTypeIcon: TextView = view.findViewById(R.id.call_type_icon)
        val contactName: TextView = view.findViewById(R.id.contact_name)
        val callDetails: TextView = view.findViewById(R.id.call_details)
        val btnCall: ImageButton = view.findViewById(R.id.btn_call)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_call, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = calls[position]
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        // Set call type icon
        holder.callTypeIcon.text = when (call.type) {
            CallLog.Calls.INCOMING_TYPE -> "📞"
            CallLog.Calls.OUTGOING_TYPE -> "📲"
            CallLog.Calls.MISSED_TYPE -> "📵"
            else -> "📞"
        }

        // Set contact name
        holder.contactName.text = call.name ?: call.number

        // Set call details
        holder.callDetails.text = "${call.number} • ${dateFormat.format(Date(call.date))}"

        // Set click listeners
        holder.itemView.setOnClickListener {
            onCallClick(call.number)
        }

        holder.btnCall.setOnClickListener {
            onCallClick(call.number)
        }
    }

    override fun getItemCount() = calls.size
}
