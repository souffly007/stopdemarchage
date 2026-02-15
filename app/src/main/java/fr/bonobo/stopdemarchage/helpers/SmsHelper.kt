package fr.bonobo.stopdemarchage.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import fr.bonobo.stopdemarchage.SmsSuspicionAnalyzer
import fr.bonobo.stopdemarchage.data.SmsConversation
import fr.bonobo.stopdemarchage.data.SmsMessage


object SmsHelper {

    /**
     * Récupère toutes les conversations SMS groupées par numéro
     */
    fun getAllConversations(context: Context, analyzer: SmsSuspicionAnalyzer): List<SmsConversation> {
        val allMessages = getAllSms(context, analyzer)

        // Grouper les messages par numéro de téléphone
        val groupedMessages = allMessages.groupBy { it.address }

        // Créer une conversation pour chaque groupe
        val conversations = groupedMessages.map { (address, messages) ->
            val sortedMessages = messages.sortedByDescending { it.date }
            val lastMessage = sortedMessages.first()
            val maxScore = messages.maxOfOrNull { it.suspicionResult.score } ?: 0
            val contactName = getContactName(context, address)

            SmsConversation(
                address = address,
                contactName = contactName,
                messages = sortedMessages,
                lastMessage = lastMessage,
                unreadCount = 0, // Peut être amélioré si nécessaire
                maxSuspicionScore = maxScore
            )
        }

        // Trier par date du dernier message (plus récent en premier)
        return conversations.sortedByDescending { it.lastMessage.date }
    }

    /**
     * Récupère le nom d'un contact depuis son numéro
     */
    fun getContactName(context: Context, phoneNumber: String): String? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsHelper", "Erreur lors de la recherche du contact", e)
        }

        return null
    }

    /**
     * Récupère tous les SMS du téléphone et les analyse
     */
    fun getAllSms(context: Context, analyzer: SmsSuspicionAnalyzer): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()

        try {
            val uri = Uri.parse("content://sms")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "type"),
                null,
                null,
                "date DESC" // Trier par date décroissante
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                val typeIndex = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val address = it.getString(addressIndex) ?: "Inconnu"
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val type = it.getInt(typeIndex)

                    // Analyser le SMS avec l'IA
                    val suspicionResult = analyzer.analyzeSms(address, body)

                    val smsMessage = SmsMessage(
                        id = id,
                        address = address,
                        body = body,
                        date = date,
                        type = type,
                        suspicionResult = suspicionResult
                    )

                    smsList.add(smsMessage)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsHelper", "Erreur lors de la lecture des SMS", e)
        }

        return smsList
    }

    /**
     * Récupère une conversation spécifique par numéro
     */
    fun getConversationByPhone(
        context: Context,
        phoneNumber: String,
        analyzer: SmsSuspicionAnalyzer
    ): SmsConversation? {
        val allConversations = getAllConversations(context, analyzer)
        return allConversations.find { it.address == phoneNumber }
    }

    /**
     * Récupère uniquement les SMS reçus
     */
    fun getReceivedSms(context: Context, analyzer: SmsSuspicionAnalyzer): List<SmsMessage> {
        return getAllSms(context, analyzer).filter { it.isReceived }
    }

    /**
     * Récupère uniquement les SMS suspects (score >= 60%)
     */
    fun getSuspiciousSms(context: Context, analyzer: SmsSuspicionAnalyzer): List<SmsMessage> {
        return getAllSms(context, analyzer).filter { it.suspicionResult.score >= 60 }
    }

    /**
     * Récupère les SMS par numéro de téléphone
     */
    fun getSmsByPhoneNumber(
        context: Context,
        phoneNumber: String,
        analyzer: SmsSuspicionAnalyzer
    ): List<SmsMessage> {
        return getAllSms(context, analyzer).filter { it.address == phoneNumber }
    }

    /**
     * Compte le nombre de SMS par niveau de risque
     */
    fun getSmsStats(context: Context, analyzer: SmsSuspicionAnalyzer): SmsStats {
        val allSms = getAllSms(context, analyzer)

        return SmsStats(
            total = allSms.size,
            normal = allSms.count { it.suspicionResult.score < 30 },
            medium = allSms.count { it.suspicionResult.score in 30..59 },
            high = allSms.count { it.suspicionResult.score in 60..79 },
            critical = allSms.count { it.suspicionResult.score >= 80 }
        )
    }
}

data class SmsStats(
    val total: Int,
    val normal: Int,
    val medium: Int,
    val high: Int,
    val critical: Int
)