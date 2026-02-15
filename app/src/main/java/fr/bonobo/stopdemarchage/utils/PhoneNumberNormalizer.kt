package fr.bonobo.stopdemarchage.utils

// Classe utilitaire pour normaliser les numéros de téléphone
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.regex.Pattern

class PhoneNumberNormalizer {

    companion object {
        private val FRENCH_MOBILE_PATTERN = Pattern.compile("^(\\+33|0033|33)?[67][0-9]{8}$")
        private val FRENCH_LANDLINE_PATTERN = Pattern.compile("^(\\+33|0033|33)?[1-59][0-9]{8}$")

        /**
         * Normalise un numéro de téléphone au format international uniforme
         * @param phoneNumber Le numéro à normaliser
         * @param defaultCountryIso Code pays par défaut (ex: "FR")
         * @return Numéro normalisé au format +33xxxxxxxxx
         */
        fun normalizePhoneNumber(phoneNumber: String?, defaultCountryIso: String = "FR"): String? {
            if (phoneNumber.isNullOrBlank()) return null

            // Nettoyer le numéro (supprimer espaces, tirets, points, parenthèses)
            val cleanNumber = phoneNumber.replace(Regex("[\\s\\-\\.\\(\\)]"), "")

            // Utiliser l'API Android pour normaliser
            val normalizedNumber = PhoneNumberUtils.formatNumberToE164(cleanNumber, defaultCountryIso)

            // Si l'API Android échoue, normaliser manuellement pour la France
            return normalizedNumber ?: normalizeForFrance(cleanNumber)
        }

        /**
         * Normalisation spécifique pour les numéros français
         */
        private fun normalizeForFrance(cleanNumber: String): String? {
            var number = cleanNumber

            // Supprimer les préfixes internationaux français
            when {
                number.startsWith("+33") -> number = number.substring(3)
                number.startsWith("0033") -> number = number.substring(4)
                number.startsWith("33") -> number = number.substring(2)
            }

            // Ajouter le 0 initial si manquant pour les numéros français
            if (number.length == 9 && (number.startsWith("6") || number.startsWith("7") ||
                        number.matches(Regex("^[1-59].*")))) {
                number = "0$number"
            }

            // Vérifier la validité et retourner au format international
            return when {
                FRENCH_MOBILE_PATTERN.matcher("0$number".takeLast(10)).matches() -> "+33${number.substring(1)}"
                FRENCH_LANDLINE_PATTERN.matcher("0$number".takeLast(10)).matches() -> "+33${number.substring(1)}"
                else -> null
            }
        }

        /**
         * Compare deux numéros après normalisation
         */
        fun areNumbersEqual(number1: String?, number2: String?, countryIso: String = "FR"): Boolean {
            val normalized1 = normalizePhoneNumber(number1, countryIso)
            val normalized2 = normalizePhoneNumber(number2, countryIso)

            return normalized1 != null && normalized2 != null && normalized1 == normalized2
        }

        /**
         * Génère tous les formats possibles d'un numéro normalisé
         */
        fun generatePossibleFormats(normalizedNumber: String): List<String> {
            if (!normalizedNumber.startsWith("+33")) return listOf(normalizedNumber)

            val nationalNumber = "0" + normalizedNumber.substring(3)

            return listOf(
                normalizedNumber,                                           // +33612345678
                nationalNumber,                                            // 0612345678
                nationalNumber.chunked(2).joinToString(" "),              // 06 12 34 56 78
                nationalNumber.chunked(2).joinToString("."),              // 06.12.34.56.78
                nationalNumber.chunked(2).joinToString("-"),              // 06-12-34-56-78
                "33${normalizedNumber.substring(3)}",                     // 33612345678
                "0033${normalizedNumber.substring(3)}",                   // 0033612345678
                nationalNumber.replaceFirst("0", "").chunked(2).joinToString(" "), // 6 12 34 56 78
                "+33 ${nationalNumber.substring(1).chunked(2).joinToString(" ")}"  // +33 6 12 34 56 78
            ).distinct()
        }
    }
}