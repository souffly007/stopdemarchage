package fr.bonobo.stopdemarchage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.R
import fr.bonobo.stopdemarchage.data.models.BlockedCall

class CallHistoryAdapter(private val calls: List<BlockedCall>) :
    RecyclerView.Adapter<CallHistoryAdapter.CallViewHolder>() {

    class CallViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val numberText: TextView = view.findViewById(R.id.tvNumber)
        val timeText: TextView = view.findViewById(R.id.tvTime)
        val categoryText: TextView = view.findViewById(R.id.tvCategory)
        val reportsText: TextView = view.findViewById(R.id.tvReports)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_history, parent, false)
        return CallViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        val call = calls[position]
        holder.numberText.text = call.number
        holder.timeText.text = "${call.time} • ${call.duration}"
        holder.categoryText.text = call.category
        holder.reportsText.text = "${call.reports} signalements"
    }

    override fun getItemCount() = calls.size
}