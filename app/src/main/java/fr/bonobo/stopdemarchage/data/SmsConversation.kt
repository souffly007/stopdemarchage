package fr.bonobo.stopdemarchage.data

import fr.bonobo.stopdemarchage.SuspicionLevel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Représente une conversation SMS avec un contact
 */
data class SmsConversation(
    val address: String,                    // Numéro de téléphone
    val contactName: String?,               // Nom du contact (null si inconnu)
    val messages: List<SmsMessage>,         // Tous les messages de cette conversation
    val lastMessage: SmsMessage,            // Dernier message reçu/envoyé
    val unreadCount: Int = 0,               // Nombre de messages non lus
    val maxSuspicionScore: Int              // Score de suspicion le plus élevé
) {

    val displayName: String
        get() = contactName ?: address

    val lastMessagePreview: String
        get() = if (lastMessage.body.length > 50) {
            lastMessage.body.substring(0, 50) + "..."
        } else {
            lastMessage.body
        }

    val formattedDate: String
        get() {
            val now = Calendar.getInstance()
            val messageTime = Calendar.getInstance().apply { timeInMillis = lastMessage.date }

            return when {
                isSameDay(now, messageTime) -> {
                    SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(lastMessage.date))
                }
                isYesterday(now, messageTime) -> {
                    "Hier"
                }
                isSameWeek(now, messageTime) -> {
                    SimpleDateFormat("EEEE", Locale.FRANCE).format(Date(lastMessage.date))
                }
                else -> {
                    SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(lastMessage.date))
                }
            }
        }

    val riskLevel: SuspicionLevel
        get() = when {
            maxSuspicionScore < 30 -> SuspicionLevel.LOW
            maxSuspicionScore < 60 -> SuspicionLevel.MEDIUM
            maxSuspicionScore < 80 -> SuspicionLevel.HIGH
            else -> SuspicionLevel.CRITICAL
        }

    val messageCount: Int
        get() = messages.size

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, messageTime: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, messageTime)
    }

    private fun isSameWeek(now: Calendar, messageTime: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR)
    }
}