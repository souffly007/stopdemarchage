package fr.bonobo.stopdemarchage.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.R
import fr.bonobo.stopdemarchage.SuspicionLevel
import fr.bonobo.stopdemarchage.data.SmsConversation

class SmsConversationAdapter(
    private val conversations: List<SmsConversation>,
    private val onConversationClick: (SmsConversation) -> Unit
) : RecyclerView.Adapter<SmsConversationAdapter.ConversationViewHolder>() {

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_conversation_item)
        val textContactName: TextView = itemView.findViewById(R.id.text_contact_name)
        val textLastMessage: TextView = itemView.findViewById(R.id.text_last_message)
        val textDate: TextView = itemView.findViewById(R.id.text_conversation_date)
        val textMessageCount: TextView = itemView.findViewById(R.id.text_message_count)
        val textRiskIndicator: TextView = itemView.findViewById(R.id.text_risk_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]

        // Nom du contact ou numéro
        holder.textContactName.text = conversation.displayName

        // Aperçu du dernier message
        val messagePrefix = if (conversation.lastMessage.isSent) "Vous: " else ""
        holder.textLastMessage.text = "$messagePrefix${conversation.lastMessagePreview}"

        // Date/Heure
        holder.textDate.text = conversation.formattedDate

        // Nombre de messages
        holder.textMessageCount.text = "${conversation.messageCount} msg"

        // Indicateur de risque
        val (riskText, riskEmoji, cardColor, textColor) = when (conversation.riskLevel) {
            SuspicionLevel.LOW -> {
                Quadruple("", "", "#FFFFFF", "#000000")
            }
            SuspicionLevel.MEDIUM -> {
                Quadruple("Suspect", "🟡", "#FFF9C4", "#F57F17")
            }
            SuspicionLevel.HIGH -> {
                Quadruple("Dangereux", "🟠", "#FFE0B2", "#E65100")
            }
            SuspicionLevel.CRITICAL -> {
                Quadruple("⚠️ ARNAQUE", "🔴", "#FFCDD2", "#C62828")
            }
        }

        // Afficher l'indicateur de risque seulement si suspect
        if (conversation.maxSuspicionScore >= 30) {
            holder.textRiskIndicator.visibility = View.VISIBLE
            holder.textRiskIndicator.text = "$riskEmoji $riskText"
            holder.cardView.setCardBackgroundColor(Color.parseColor(cardColor))
            holder.textContactName.setTextColor(Color.parseColor(textColor))
        } else {
            holder.textRiskIndicator.visibility = View.GONE
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            holder.textContactName.setTextColor(Color.parseColor("#000000"))
        }

        // Style pour le nom du contact (gras si inconnu)
        if (conversation.contactName == null) {
            holder.textContactName.text = "📱 ${conversation.address}"
        }

        // Click listener
        holder.cardView.setOnClickListener {
            onConversationClick(conversation)
        }
    }

    override fun getItemCount(): Int = conversations.size

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}