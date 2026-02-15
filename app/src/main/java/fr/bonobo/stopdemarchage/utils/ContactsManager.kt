package fr.bonobo.stopdemarchage.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsManager @Inject constructor() {

    companion object {
        private const val TAG = "ContactsManager"
    }

    /**
     * Vérifie si un numéro de téléphone est présent dans les contacts
     * @param context Contexte Android
     * @param phoneNumber Numéro à vérifier (peut contenir des espaces, tirets, etc.)
     * @return true si le numéro est trouvé dans les contacts
     */
    fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        if (!hasContactsPermission(context)) {
            Log.w(TAG, "Permission READ_CONTACTS non accordée")
            return false
        }

        if (phoneNumber.isBlank()) {
            return false
        }

        // Nettoyer le numéro (supprimer espaces, tirets, parenthèses)
        val cleanedNumber = cleanPhoneNumber(phoneNumber)

        Log.d(TAG, "Vérification contact pour: $phoneNumber (nettoyé: $cleanedNumber)")

        return try {
            searchInContacts(context, cleanedNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la recherche dans les contacts", e)
            false
        }
    }

    /**
     * Recherche un numéro dans les contacts
     */
    private fun searchInContacts(context: Context, phoneNumber: String): Boolean {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.let {
                while (it.moveToNext()) {
                    val contactNumber = it.getString(0)
                    val contactName = it.getString(1)

                    if (contactNumber != null) {
                        val cleanedContactNumber = cleanPhoneNumber(contactNumber)

                        // Comparaison exacte ou en fin de chaîne (pour gérer les indicatifs)
                        if (numbersMatch(phoneNumber, cleanedContactNumber)) {
                            Log.d(TAG, "Contact trouvé: $contactName ($contactNumber)")
                            return true
                        }
                    }
                }
            }
        } finally {
            cursor?.close()
        }

        Log.d(TAG, "Aucun contact trouvé pour: $phoneNumber")
        return false
    }

    /**
     * Nettoie un numéro de téléphone en supprimant tous les caractères non-numériques
     * sauf le + initial pour les indicatifs internationaux
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^+\\d]"), "")
    }

    /**
     * Compare deux numéros de téléphone en tenant compte des variations possibles
     * (avec/sans indicatif pays, formatage différent)
     */
    private fun numbersMatch(number1: String, number2: String): Boolean {
        // Comparaison exacte
        if (number1 == number2) return true

        // Supprimer les + pour comparaison
        val num1Clean = number1.removePrefix("+")
        val num2Clean = number2.removePrefix("+")

        if (num1Clean == num2Clean) return true

        // Vérifier si l'un se termine par l'autre (gestion indicatifs)
        if (num1Clean.length != num2Clean.length) {
            val shorter = if (num1Clean.length < num2Clean.length) num1Clean else num2Clean
            val longer = if (num1Clean.length > num2Clean.length) num1Clean else num2Clean

            // Vérifier si le numéro court correspond à la fin du numéro long
            if (longer.endsWith(shorter) && longer.length - shorter.length <= 4) {
                return true
            }
        }

        return false
    }

    /**
     * Vérifie si l'application a la permission de lire les contacts
     */
    private fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Récupère la liste de tous les contacts (pour debug ou autre usage)
     */
    fun getAllContacts(context: Context): List<Contact> {
        if (!hasContactsPermission(context)) {
            Log.w(TAG, "Permission READ_CONTACTS non accordée")
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.let {
                while (it.moveToNext()) {
                    val number = it.getString(0)
                    val name = it.getString(1)
                    if (number != null && name != null) {
                        contacts.add(Contact(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la récupération des contacts", e)
        } finally {
            cursor?.close()
        }

        Log.d(TAG, "Trouvé ${contacts.size} contacts")
        return contacts
    }
}

/**
 * Classe de données pour représenter un contact
 */
data class Contact(
    val name: String,
    val phoneNumber: String
)