package fr.bonobo.stopdemarchage.data

import fr.bonobo.stopdemarchage.SuspicionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 = reçu, 2 = envoyé
    val suspicionResult: SuspicionResult
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
            return sdf.format(Date(date))
        }

    // ✅ CORRECTION : En tant que PROPRIÉTÉS (pas fonctions)
    val isReceived: Boolean
        get() = type == 1

    val isSent: Boolean
        get() = type == 2

    val riskLevel: String
        get() = when {
            suspicionResult.score < 30 -> "low"
            suspicionResult.score < 60 -> "medium"
            suspicionResult.score < 80 -> "high"
            else -> "critical"
        }
}