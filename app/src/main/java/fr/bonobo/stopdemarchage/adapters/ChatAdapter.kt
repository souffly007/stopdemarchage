package fr.bonobo.stopdemarchage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.R
import fr.bonobo.stopdemarchage.data.SmsMessageEntry
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<SmsMessageEntry>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.textMessage)
        val messageDate: TextView = view.findViewById(R.id.textMessageDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutId = if (viewType == 1) R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.body
        holder.messageDate.text = dateFormat.format(Date(message.date))
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) 1 else 0
    }
}