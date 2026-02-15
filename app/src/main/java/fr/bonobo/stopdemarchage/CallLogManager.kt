package fr.bonobo.stopdemarchage

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri // Assurez-vous que cette ligne est présente


class CallLogManager(private val context: Context) {

    // Utiliser le même nom de fichier SharedPreferences que dans MainActivity et CallScreeningService
    private val PREFS_FILE_NAME = "StopDemarchagePrefs" // Doit correspondre à MainActivity
    // Utiliser la même clé que celle que nous allons définir pour la liste noire
    private val KEY_BLOCKED_NUMBERS = "blocked_numbers" // Doit correspondre à CallScreeningService et aux futurs changements

    private val sharedPrefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    suspend fun getCallLogs(limit: Int = 100): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogEntry>()
        val blockedNumbers = getBlockedNumbersSet() // Lire la liste noire sous forme de Set<String>

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndex(CallLog.Calls._ID)
                val numberColumn = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameColumn = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeColumn = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateColumn = c.getColumnIndex(CallLog.Calls.DATE)
                val durationColumn = c.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (c.moveToNext() && count < limit) {
                    val id = if (idColumn >= 0) c.getLong(idColumn) else 0L
                    val rawNumber = if (numberColumn >= 0) c.getString(numberColumn) ?: "Inconnu" else "Inconnu"
                    val contactName = if (nameColumn >= 0) c.getString(nameColumn) else null
                    val type = if (typeColumn >= 0) c.getInt(typeColumn) else CallLog.Calls.MISSED_TYPE
                    val date = if (dateColumn >= 0) c.getLong(dateColumn) else 0L
                    val duration = if (durationColumn >= 0) c.getLong(durationColumn) else 0L

                    val finalContactName = contactName ?: getContactName(rawNumber)

                    // >>> CORRECTION MAJEURE: Normaliser le numéro avant de le vérifier
                    val normalizedNumber = normalizePhoneNumber(rawNumber)

                    val isBlocked = blockedNumbers.contains(normalizedNumber) // Vérifier le numéro normalisé

                    callLogs.add(
                        CallLogEntry(
                            id = id,
                            number = rawNumber, // Garder le numéro original pour l'affichage
                            contactName = finalContactName,
                            type = type,
                            date = date,
                            duration = duration,
                            isBlocked = isBlocked
                        )
                    )
                    count++
                }
            }
        } catch (e: SecurityException) {
            // Gérer l'exception de permission, log ou Toast
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        callLogs
    }

    private fun getContactName(phoneNumber: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameColumn = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameColumn >= 0) c.getString(nameColumn) else null
                } else null
            }
        } catch (e: Exception) {
            // Gérer les exceptions de permission ou autres
            e.printStackTrace()
            null
        }
    }

    // --- Fonctions de gestion de la liste noire ---

    // Utilisera la normalisation avant d'ajouter
    fun blockNumber(phoneNumber: String) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val blockedNumbers = getBlockedNumbersSet().toMutableSet()
        if (blockedNumbers.add(normalizedNumber)) { // N'ajoute que si nouveau
            saveBlockedNumbersSet(blockedNumbers)
        }
    }

    // Utilisera la normalisation avant de supprimer
    fun unblockNumber(phoneNumber: String) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val blockedNumbers = getBlockedNumbersSet().toMutableSet()
        if (blockedNumbers.remove(normalizedNumber)) { // Ne supprime que si existant
            saveBlockedNumbersSet(blockedNumbers)
        }
    }

    // Utilisera la normalisation avant de vérifier
    fun isNumberBlocked(phoneNumber: String): Boolean {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        return getBlockedNumbersSet().contains(normalizedNumber)
    }

    // Récupère la liste des numéros bloqués, déjà normalisés
    fun getBlockedNumbersList(): List<String> {
        return getBlockedNumbersSet().toList()
    }

    // >>> CORRECTION: Lire le Set<String> directement
    private fun getBlockedNumbersSet(): Set<String> {
        // La clé doit être la même que celle utilisée dans CallScreeningService
        return sharedPrefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()
    }

    // >>> CORRECTION: Enregistrer le Set<String> directement
    private fun saveBlockedNumbersSet(blockedNumbers: Set<String>) {
        sharedPrefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, blockedNumbers).apply()
    }

    // Fonction de normalisation des numéros - CRUCIALE
    private fun normalizePhoneNumber(number: String): String {
        var normalized = number.trim()
            .replace(" ", "") // Supprimer les espaces
            .replace("-", "") // Supprimer les tirets
            .replace("(", "") // Supprimer les parenthèses
            .replace(")", "") // Supprimer les parenthèses

        // Si le numéro commence par 0 et a 10 chiffres (format français typique)
        if (normalized.startsWith("0") && normalized.length == 10 && normalized.matches("^0[1-9][0-9]{8}$".toRegex())) {
            normalized = "+33" + normalized.substring(1)
        }
        // Pour les numéros courts ou spéciaux qui ne commencent pas par un +, on peut décider de ne pas les normaliser
        // ou d'ajouter d'autres règles si besoin (ex: numéros d'urgence, numéros à 4 chiffres, etc.)

        // IMPORTANT: Pour une robustesse maximale, vous pouvez envisager d'utiliser une bibliothèque
        // comme libphonenumber de Google pour une normalisation internationale complexe.
        // Mais pour un usage simple en France, ceci est un bon début.

        return normalized
    }
}