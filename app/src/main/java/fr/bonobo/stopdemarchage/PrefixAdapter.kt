package fr.bonobo.stopdemarchage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BlockedNumberAdapter(
    private val blockedList: MutableList<String>,
    private val onDeleteClick: (String) -> Unit // Callback pour la suppression
) : RecyclerView.Adapter<BlockedNumberAdapter.BlockedNumberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedNumberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_number, parent, false) // Nouveau layout
        return BlockedNumberViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockedNumberViewHolder, position: Int) {
        holder.bind(blockedList[position])
    }

    override fun getItemCount(): Int = blockedList.size

    inner class BlockedNumberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewNumber: TextView = itemView.findViewById(R.id.textViewBlockedNumber)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDeleteBlockedNumber)

        fun bind(number: String) {
            textViewNumber.text = number
            buttonDelete.setOnClickListener {
                onDeleteClick(number)
            }
        }
    }
}