package com.hardreach.dialer

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class RecentCall(
    val number: String,
    val name: String?,
    val date: Long,
    val type: Int,
    val duration: Long = 0
)

class RecentsAdapter(
    private val calls: List<RecentCall>,
    private val onCallClick: (String) -> Unit
) : RecyclerView.Adapter<RecentsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val callTypeIcon: ImageView = view.findViewById(R.id.call_type_icon)
        val contactName: TextView = view.findViewById(R.id.contact_name)
        val phoneNumber: TextView = view.findViewById(R.id.phone_number)
        val callTime: TextView = view.findViewById(R.id.call_time)
        val callDuration: TextView = view.findViewById(R.id.call_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_call, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = calls[position]
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        // Set call type icon tint and background based on type
        when (call.type) {
            CallLog.Calls.INCOMING_TYPE -> {
                holder.callTypeIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
                holder.callTypeIcon.setBackgroundResource(R.drawable.bg_call_incoming)
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                holder.callTypeIcon.setColorFilter(android.graphics.Color.parseColor("#1A73E8"))
                holder.callTypeIcon.setBackgroundResource(R.drawable.bg_call_outgoing)
            }
            CallLog.Calls.MISSED_TYPE -> {
                holder.callTypeIcon.setColorFilter(android.graphics.Color.parseColor("#EA4335"))
                holder.callTypeIcon.setBackgroundResource(R.drawable.bg_call_missed)
            }
            else -> {
                holder.callTypeIcon.setColorFilter(android.graphics.Color.parseColor("#5F6368"))
                holder.callTypeIcon.setBackgroundResource(R.drawable.bg_icon_circle)
            }
        }

        // Set contact name
        holder.contactName.text = call.name ?: call.number

        // Set phone number
        holder.phoneNumber.text = call.number

        // Set call time
        holder.callTime.text = dateFormat.format(Date(call.date))

        // Set call duration
        if (call.duration > 0) {
            val minutes = call.duration / 60
            val seconds = call.duration % 60
            holder.callDuration.text = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
            holder.callDuration.visibility = View.VISIBLE
        } else {
            // Missed calls have no duration
            if (call.type == CallLog.Calls.MISSED_TYPE) {
                holder.callDuration.text = "Missed"
                holder.callDuration.setTextColor(android.graphics.Color.parseColor("#EA4335"))
            } else {
                holder.callDuration.visibility = View.GONE
            }
        }

        // Set click listener on whole item
        holder.itemView.setOnClickListener {
            onCallClick(call.number)
        }
    }

    override fun getItemCount() = calls.size
}
