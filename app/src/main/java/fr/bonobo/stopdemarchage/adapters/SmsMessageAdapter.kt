package fr.bonobo.stopdemarchage.adapters

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat // Importation ajoutée
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import fr.bonobo.stopdemarchage.data.SmsMessage
import fr.bonobo.stopdemarchage.SuspicionLevel

/**
 * Adaptateur pour afficher les messages d'une conversation (style bulle de chat)
 */
class SmsMessageAdapter(
    private val messages: List<SmsMessage>,
    private val onMessageClick: (SmsMessage) -> Unit
) : RecyclerView.Adapter<SmsMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs assumés corrects, basés sur votre code
        val container: LinearLayout = itemView.findViewById(R.id.message_container)
        val cardView: CardView = itemView.findViewById(R.id.card_message)
        val textBody: TextView = itemView.findViewById(R.id.text_message_body)
        val textTime: TextView = itemView.findViewById(R.id.text_message_time)
        val textRiskIndicator: TextView = itemView.findViewById(R.id.text_message_risk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context // Récupérer le contexte une seule fois

        // ⭐ CORRECTION : Utiliser message.isSent() au lieu de message.isSent (pour Kapt)
        if (message.isSent) {
            // Message envoyé (à droite, en bleu)
            holder.container.gravity = Gravity.END
            holder.cardView.setCardBackgroundColor(Color.parseColor("#1976D2")) // Bleu foncé
            holder.textBody.setTextColor(Color.WHITE)
            holder.textTime.setTextColor(Color.parseColor("#E3F2FD")) // Bleu très clair
            holder.textRiskIndicator.visibility = View.GONE
        } else {
            // Message reçu (à gauche, couleur selon risque)
            holder.container.gravity = Gravity.START

            val score = message.suspicionResult.score

            val (bgColor, textColor) = when {
                score < 30 -> Pair("#ECEFF1", "#000000") // Normal - gris clair
                score < 60 -> Pair("#FFF9C4", "#F57F17") // Suspect - jaune
                score < 80 -> Pair("#FFE0B2", "#E65100") // Dangereux - orange
                else -> Pair("#FFCDD2", "#C62828")       // Critique - rouge
            }

            holder.cardView.setCardBackgroundColor(Color.parseColor(bgColor))
            holder.textBody.setTextColor(Color.parseColor(textColor))
            holder.textTime.setTextColor(Color.parseColor(textColor))

            // Afficher l'indicateur de risque si suspect (score >= 30)
            if (score >= 30) {
                holder.textRiskIndicator.visibility = View.VISIBLE
                val riskEmoji = when {
                    score < 60 -> "🟡"
                    score < 80 -> "🟠"
                    else -> "🔴"
                }
                holder.textRiskIndicator.text = "$riskEmoji ${score}%"
                holder.textRiskIndicator.setTextColor(Color.parseColor(textColor))
            } else {
                holder.textRiskIndicator.visibility = View.GONE
            }
        }

        // Contenu du message
        holder.textBody.text = message.body

        // Heure
        // Note: L'utilisation de Locale.FRANCE est correcte
        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
        holder.textTime.text = timeFormat.format(Date(message.date))

        // Click listener
        holder.cardView.setOnClickListener {
            onMessageClick(message)
        }
    }

    override fun getItemCount(): Int = messages.size
}