package com.hardreach.dialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Contact(
    val name: String,
    val number: String
)

class ContactsAdapter(
    private var contacts: List<Contact>,
    private val onCallClick: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contactAvatar: TextView = view.findViewById(R.id.contact_avatar)
        val contactName: TextView = view.findViewById(R.id.contact_name)
        val phoneNumber: TextView = view.findViewById(R.id.phone_number)
        val btnCall: ImageButton = view.findViewById(R.id.btn_call)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]

        // Set avatar with first letter of name
        holder.contactAvatar.text = contact.name.firstOrNull()?.uppercase() ?: "?"

        // Set contact info
        holder.contactName.text = contact.name
        holder.phoneNumber.text = contact.number

        // Set click listeners
        holder.itemView.setOnClickListener {
            onCallClick(contact.number)
        }

        holder.btnCall.setOnClickListener {
            onCallClick(contact.number)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
}
