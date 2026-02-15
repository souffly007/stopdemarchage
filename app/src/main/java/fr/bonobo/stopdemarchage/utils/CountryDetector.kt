package fr.bonobo.stopdemarchage.utils

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

class CountryDetector(private val context: Context) {

    companion object {
        const val TAG = "CountryDetector"
        const val COUNTRY_FRANCE = "FR"
        const val COUNTRY_BELGIUM = "BE"
        const val DEFAULT_COUNTRY = COUNTRY_FRANCE

        /**
         * Détecte le pays d'un numéro de téléphone (méthode statique)
         */
        fun detectCountry(phoneNumber: String): String {
            val normalized = phoneNumber.replace(Regex("[^0-9+]"), "")

            return when {
                normalized.startsWith("+33") || normalized.startsWith("0033") -> COUNTRY_FRANCE
                normalized.startsWith("+32") || normalized.startsWith("0032") -> COUNTRY_BELGIUM
                normalized.startsWith("0") && !normalized.startsWith("00") -> {
                    // Numéro national - deviner selon la longueur
                    if (normalized.length == 10) COUNTRY_FRANCE else COUNTRY_BELGIUM
                }
                else -> COUNTRY_FRANCE // Par défaut France
            }
        }
    }

    /**
     * Détecte le pays de l'utilisateur via la SIM ou les paramètres système
     */
    fun detectCountry(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            // Essayer de détecter via le code pays de la SIM
            val simCountry = telephonyManager?.simCountryIso?.uppercase()

            when {
                simCountry == "FR" -> COUNTRY_FRANCE
                simCountry == "BE" -> COUNTRY_BELGIUM
                else -> {
                    Log.d(TAG, "Pays non détecté via SIM: $simCountry, utilisation défaut")
                    DEFAULT_COUNTRY
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur détection pays", e)
            DEFAULT_COUNTRY
        }
    }
}