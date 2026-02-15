package fr.bonobo.stopdemarchage

import android.content.Context
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast // Garder pour les messages, mais la logique de block/unblock sera déplacée
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class CallLogAdapter(
    private val context: Context,
    private val onCallClick: (CallLogEntry) -> Unit,
    // Le callback sera maintenant responsable de demander à l'activité de gérer le toggle
    private val onBlockToggle: (CallLogEntry) -> Unit, // NE PAS MODIFIER LA SIGNATURE ICI
    private val onAddToContacts: (CallLogEntry) -> Unit,
    private val onSendMessage: (CallLogEntry) -> Unit,
    private val onShowDetails: (CallLogEntry) -> Unit = {}
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    private var callList = mutableListOf<CallLogEntry>()

    fun updateCalls(newCalls: List<CallLogEntry>) {
        val diffCallback = CallLogDiffCallback(callList, newCalls)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        callList.clear()
        callList.addAll(newCalls)
        diffResult.dispatchUpdatesTo(this)
        // Pas besoin de Toast ici, la logique est dans l'activité maintenant
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        holder.bind(callList[position])
    }

    override fun getItemCount(): Int = callList.size

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val callTypeIcon: ImageView = itemView.findViewById(R.id.call_type_icon)
        private val contactName: TextView = itemView.findViewById(R.id.contact_name)
        private val phoneNumber: TextView = itemView.findViewById(R.id.phone_number)
        private val callTime: TextView = itemView.findViewById(R.id.call_time)
        private val callDuration: TextView = itemView.findViewById(R.id.call_duration)
        private val blockedIcon: ImageView = itemView.findViewById(R.id.blocked_icon) // C'est l'icône de la capture, pas un bouton
        private val menuButton: ImageButton = itemView.findViewById(R.id.menu_button) // Le bouton 3 points qui ouvre le menu

        fun bind(call: CallLogEntry) {
            bindContactInfo(call)
            bindCallTime(call)
            bindCallDuration(call)
            bindCallTypeIcon(call)
            bindBlockedState(call) // Met à jour la visibilité de l'icône de blocage
            setupClickListeners(call)
        }

        private fun bindContactInfo(call: CallLogEntry) {
            contactName.text = call.getDisplayName()
            // Afficher le numéro seulement si un nom de contact est présent et différent du numéro
            // Ou toujours si vous voulez les deux
            if (call.contactName != null && call.contactName != call.number) {
                phoneNumber.text = call.number
                phoneNumber.visibility = View.VISIBLE
            } else {
                // Si pas de nom de contact, le numéro est le DisplayName, donc cacher le champ numéro double
                phoneNumber.visibility = View.GONE
            }
        }

        private fun bindCallTime(call: CallLogEntry) {
            callTime.text = call.getTimeFormatted()
        }

        private fun bindCallDuration(call: CallLogEntry) {
            if (call.duration > 0) {
                callDuration.text = call.getDurationFormatted()
                callDuration.visibility = View.VISIBLE
            } else {
                callDuration.visibility = View.GONE
            }
        }

        private fun bindCallTypeIcon(call: CallLogEntry) {
            // Assurez-vous d'avoir ces drawables et couleurs dans vos ressources
            val (iconRes, colorRes) = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming to R.color.call_incoming
                CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.color.call_outgoing
                CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed to R.color.call_missed
                // Gérer le type BLOCKED si CallLog renvoie ce type
                CallLog.Calls.BLOCKED_TYPE -> R.drawable.ic_blocked_circle to R.color.call_blocked // Créez cette couleur
                else -> R.drawable.ic_blocked_circle to R.color.call_missed // Fallback
            }
            callTypeIcon.setImageResource(iconRes)
            callTypeIcon.setColorFilter(ContextCompat.getColor(context, colorRes))
        }

        private fun bindBlockedState(call: CallLogEntry) {
            // Si le numéro est bloqué, rendre l'icône visible
            blockedIcon.visibility = if (call.isBlocked) View.VISIBLE else View.GONE
            // Optionnel: Diminuer l'opacité de toute la ligne si bloqué
            itemView.alpha = if (call.isBlocked) 0.6f else 1.0f
        }

        private fun setupClickListeners(call: CallLogEntry) {
            itemView.setOnClickListener {
                onCallClick(call) // Gère le clic sur l'élément entier (ex: rappeler)
            }
            // Le bouton de menu (les trois points)
            menuButton.setOnClickListener { view ->
                showPopupMenu(view, call)
            }
            // Si l'icône de blocage (le cercle rouge) est cliquable directement (ce que la capture suggère)
            // Alors on peut ajouter un listener ici pour l'action rapide.
            // Si ce n'est qu'un indicateur, alors l'action est via le menu contextuel.
            // Vu votre capture, le rond rouge est un bouton. Je vais l'utiliser comme tel.
            // Assurez-vous que blocked_icon est votre rond rouge barré.
            blockedIcon.setOnClickListener {
                onBlockToggle(call) // Appelle le callback pour que l'activité gère le blocage
            }
        }

        private fun showPopupMenu(view: View, call: CallLogEntry) {
            val popup = PopupMenu(context, view)
            try {
                popup.menuInflater.inflate(R.menu.call_log_menu, popup.menu)
                setupMenuItems(popup, call)
                popup.setOnMenuItemClickListener { menuItem ->
                    handleMenuItemClick(menuItem.itemId, call)
                }
                popup.show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Erreur lors de l'affichage du menu", Toast.LENGTH_SHORT).show()
            }
        }

        private fun setupMenuItems(popup: PopupMenu, call: CallLogEntry) {
            val menu = popup.menu
            menu.findItem(R.id.action_block)?.let { blockItem ->
                // Mettre à jour le texte du menu "Bloquer" / "Débloquer"
                blockItem.title = if (call.isBlocked) {
                    context.getString(R.string.unblock_number) // Créez cette ressource string
                } else {
                    context.getString(R.string.block_number) // Créez cette ressource string
                }
            }
            menu.findItem(R.id.action_add_contact)?.let { addContactItem ->
                // Afficher l'option "Ajouter aux contacts" seulement si pas déjà un contact
                addContactItem.isVisible = call.contactName == null || call.contactName == call.number
            }
        }

        private fun handleMenuItemClick(itemId: Int, call: CallLogEntry): Boolean {
            return when (itemId) {
                R.id.action_call_back -> {
                    onCallClick(call); true
                }
                R.id.action_send_message -> {
                    onSendMessage(call); true
                }
                R.id.action_add_contact -> {
                    onAddToContacts(call); true
                }
                R.id.action_block -> {
                    // NE PAS APPELER toggleBlockNumber(call) DIRECTEMENT ICI
                    // Appeler le callback pour que l'activité gère la logique
                    onBlockToggle(call); true
                }
                else -> false
            }
        }
        // >>> SUPPRIMER LA FONCTION toggleBlockNumber D'ICI <<<
        // La logique est déplacée dans CallLogActivity pour utiliser CallLogManager
        /*
        private fun toggleBlockNumber(call: CallLogEntry) {
            // ... cette logique sera déplacée ...
        }
        */
    }

    private class CallLogDiffCallback(
        private val oldList: List<CallLogEntry>,
        private val newList: List<CallLogEntry>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.number == newItem.number &&
                    oldItem.contactName == newItem.contactName &&
                    oldItem.type == newItem.type &&
                    oldItem.date == newItem.date &&
                    oldItem.duration == newItem.duration &&
                    oldItem.isBlocked == newItem.isBlocked // TRÈS IMPORTANT : Inclure isBlocked
        }
    }
}