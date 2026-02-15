package fr.bonobo.stopdemarchage

import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.*

data class CallLogEntry(
    val id: Long,
    val number: String, // Le numéro de téléphone
    val contactName: String?,
    val type: Int,      // Type d'appel (entrant, sortant, etc. de CallLog.Calls)
    val date: Long,     // Timestamp de l'appel
    val duration: Long, // Durée en secondes
    var isBlocked: Boolean = false // <-- Ajout pertinent pour l'UI
) {
    fun getDisplayName(): String {
        return contactName ?: number
    }

    fun getTimeFormatted(): String {
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return dateFormat.format(Date(date))
    }

    fun getDurationFormatted(): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun getTypeString(): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Entrant"
            CallLog.Calls.OUTGOING_TYPE -> "Sortant"
            CallLog.Calls.MISSED_TYPE -> "Manqué"
            else -> "Inconnu" // Vous pourriez ajouter CallLog.Calls.BLOCKED_TYPE ici si vous lisez les appels bloqués par le système
        }
    }
}