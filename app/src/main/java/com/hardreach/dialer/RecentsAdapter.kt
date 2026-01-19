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
    val type: Int
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_call, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = calls[position]
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        // Set call type icon tint based on type
        val iconTint = when (call.type) {
            CallLog.Calls.INCOMING_TYPE -> android.graphics.Color.parseColor("#4CAF50")
            CallLog.Calls.OUTGOING_TYPE -> android.graphics.Color.parseColor("#2196F3")
            CallLog.Calls.MISSED_TYPE -> android.graphics.Color.parseColor("#F44336")
            else -> android.graphics.Color.parseColor("#757575")
        }
        holder.callTypeIcon.setColorFilter(iconTint)

        // Set contact name
        holder.contactName.text = call.name ?: call.number

        // Set phone number
        holder.phoneNumber.text = call.number

        // Set call time
        holder.callTime.text = dateFormat.format(Date(call.date))

        // Set click listener on whole item
        holder.itemView.setOnClickListener {
            onCallClick(call.number)
        }
    }

    override fun getItemCount() = calls.size
}
