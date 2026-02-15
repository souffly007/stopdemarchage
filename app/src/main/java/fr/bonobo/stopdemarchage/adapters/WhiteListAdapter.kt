package fr.bonobo.stopdemarchage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.R
import fr.bonobo.stopdemarchage.data.WhiteListContact

class WhiteListAdapter(
    private val contacts: MutableList<WhiteListContact>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<WhiteListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvContactName)
        val numberText: TextView = view.findViewById(R.id.tvContactNumber)
        val deleteButton: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_white_list_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.nameText.text = contact.name
        holder.numberText.text = contact.number
        holder.deleteButton.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount() = contacts.size
}