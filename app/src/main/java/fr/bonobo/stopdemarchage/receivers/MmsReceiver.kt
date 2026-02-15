package fr.bonobo.stopdemarchage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

class MmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.WAP_PUSH_DELIVER") {
            Log.d(TAG, "📧 MMS reçu")

            try {
                // Extraire le numéro de l'expéditeur si possible
                val phoneNumber = extractPhoneNumber(intent)

                if (phoneNumber != null) {
                    Log.d(TAG, "📧 MMS de: $phoneNumber")

                    // Vérifier si le numéro est dans les contacts
                    if (isNumberInContacts(context, phoneNumber)) {
                        Log.d(TAG, "👤 MMS autorisé : contact du téléphone")
                        return
                    }

                    // Vérifier si le numéro est dans la liste blanche
                    if (isNumberInWhiteList(context, phoneNumber)) {
                        Log.d(TAG, "✅ MMS autorisé : liste blanche")
                        return
                    }

                    // Vérifier si le numéro est dans la liste noire
                    if (isNumberInBlackList(context, phoneNumber)) {
                        Log.d(TAG, "🚫 MMS bloqué : liste noire")
                        abortBroadcast()
                        return
                    }
                }

                Log.d(TAG, "✅ MMS transmis au système")

            } catch (e: Exception) {
                Log.e(TAG, "Erreur filtrage MMS", e)
                // En cas d'erreur, laisser passer le MMS
            }
        }
    }

    private fun extractPhoneNumber(intent: Intent): String? {
        return try {
            val extras = intent.extras
            if (extras != null) {
                val pdus = extras.get("pdu") as? Array<*>
                // Pour les MMS, le numéro est plus difficile à extraire
                // On retourne null si on ne peut pas l'extraire
                null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur extraction numéro MMS", e)
            null
        }
    }

    private fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        return try {
            val formats = getAllFormats(phoneNumber)
            for (format in formats) {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(format)
                )
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Erreur vérification contacts MMS", e)
            true // Laisser passer par sécurité
        }
    }

    private fun isNumberInWhiteList(context: Context, phoneNumber: String): Boolean {
        val sharedPrefs = context.getSharedPreferences(
            "StopDemarchagePrefs", Context.MODE_PRIVATE
        )
        val contactsString = sharedPrefs.getString("white_list_contacts", "")
        if (contactsString.isNullOrEmpty()) return false

        val whiteListNumbers = contactsString.split(";").mapNotNull { contactData ->
            val parts = contactData.split("|")
            if (parts.size == 2) normalizePhoneNumber(parts[1]) else null
        }.toSet()

        val formats = getAllFormats(phoneNumber)
        for (format in formats) {
            if (whiteListNumbers.contains(normalizePhoneNumber(format))) {
                return true
            }
        }
        return false
    }

    private fun isNumberInBlackList(context: Context, phoneNumber: String): Boolean {
        val sharedPrefs = context.getSharedPreferences(
            "StopDemarchagePrefs", Context.MODE_PRIVATE
        )
        val blackList = sharedPrefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
        if (blackList.isEmpty()) return false

        val formats = getAllFormats(phoneNumber)
        for (format in formats) {
            if (blackList.contains(format)) return true
            if (blackList.contains(normalizePhoneNumber(format))) return true
        }
        return false
    }

    private fun getAllFormats(number: String): List<String> {
        val clean = number.replace(Regex("[^0-9+]"), "")
        val formats = mutableListOf(clean)

        if (clean.startsWith("+33")) {
            formats.add(clean.substring(1))
            formats.add("0${clean.substring(3)}")
        } else if (clean.startsWith("33") && clean.length > 4) {
            formats.add("+$clean")
            formats.add("0${clean.substring(2)}")
        } else if (clean.startsWith("0") && clean.length >= 10) {
            formats.add("+33${clean.substring(1)}")
            formats.add("33${clean.substring(1)}")
        }

        return formats.distinct()
    }

    private fun normalizePhoneNumber(number: String): String {
        var normalized = number.trim()
            .replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "")
            .replace(".", "")

        if (normalized.startsWith("0") && normalized.length == 10 &&
            normalized.matches("^0[1-9][0-9]{8}$".toRegex())) {
            normalized = "+33" + normalized.substring(1)
        }

        return normalized
    }
}