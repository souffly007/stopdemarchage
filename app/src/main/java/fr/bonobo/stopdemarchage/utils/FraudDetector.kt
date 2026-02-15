package fr.bonobo.stopdemarchage.utils

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FraudDetector @Inject constructor() {

    companion object {
        private const val TAG = "FraudDetector"

        // Mots-clés suspects pour détecter les SMS frauduleux
        private val FRAUD_KEYWORDS = listOf(
            // Arnaques classiques
            "félicitations", "gagné", "remporter", "prix", "cadeau",
            "héritage", "million", "euros gratuits", "lottery",

            // Phishing bancaire
            "compte bloqué", "vérifier votre compte", "mise à jour",
            "confirmer vos données", "cliquez ici", "lien sécurisé",
            "carte bancaire", "code secret", "pin",

            // Urgence factice
            "urgent", "immédiatement", "expire aujourd'hui",
            "dernière chance", "offre limitée", "dans 24h",

            // Démarchage agressif
            "remboursement", "crédit rapide", "prêt immédiat",
            "assurance gratuite", "investissement", "bitcoin",

            // Techniques de manipulation
            "ne pas ignorer", "action requise", "répondez vite",
            "numéro surtaxé", "rappeler", "stop au"
        )

        // Patterns suspects (regex)
        private val FRAUD_PATTERNS = listOf(
            // URLs suspectes
            Regex("""https?://[^\s]+(?:\.tk|\.ml|\.ga|\.cf)""", RegexOption.IGNORE_CASE),
            // Numéros surtaxés français
            Regex("""(?:08|3[0-9])[0-9]{7,8}"""),
            // Codes promo/réductions
            Regex("""(?:code|promo|reduction)[\s:]*[A-Z0-9]{4,}""", RegexOption.IGNORE_CASE),
            // Montants en euros suspicieux
            Regex("""[0-9]{2,}\s*(?:euros?|€)(?:\s+gratuits?)?""", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Analyse un SMS pour détecter s'il est potentiellement frauduleux
     * @param phoneNumber Numéro de l'expéditeur
     * @param message Contenu du message
     * @return true si le message semble frauduleux
     */
    fun analyzeMessage(phoneNumber: String, message: String): Boolean {
        Log.d(TAG, "Analyse anti-fraude pour: $phoneNumber")

        val messageNormalized = message.lowercase().trim()
        var suspicionScore = 0
        val reasons = mutableListOf<String>()

        // 1. Vérification des mots-clés suspects
        val foundKeywords = FRAUD_KEYWORDS.filter { keyword ->
            messageNormalized.contains(keyword.lowercase())
        }

        if (foundKeywords.isNotEmpty()) {
            suspicionScore += foundKeywords.size * 2
            reasons.add("Mots-clés suspects: ${foundKeywords.joinToString(", ")}")
        }

        // 2. Vérification des patterns suspects
        FRAUD_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(message)) {
                suspicionScore += 3
                reasons.add("Pattern suspect détecté: ${pattern.pattern}")
            }
        }

        // 3. Analyse du numéro expéditeur
        val senderSuspicion = analyzeSender(phoneNumber)
        suspicionScore += senderSuspicion.first
        if (senderSuspicion.second.isNotEmpty()) {
            reasons.add(senderSuspicion.second)
        }

        // 4. Analyse structurelle du message
        val structuralSuspicion = analyzeStructure(message)
        suspicionScore += structuralSuspicion.first
        if (structuralSuspicion.second.isNotEmpty()) {
            reasons.add(structuralSuspicion.second)
        }

        // Seuil de suspicion (ajustable)
        val isFraudulent = suspicionScore >= 5

        if (isFraudulent) {
            Log.w(TAG, "Message frauduleux détecté (score: $suspicionScore)")
            Log.w(TAG, "Raisons: ${reasons.joinToString(" | ")}")
        } else {
            Log.d(TAG, "Message normal (score: $suspicionScore)")
        }

        return isFraudulent
    }

    /**
     * Analyse le numéro de l'expéditeur pour détecter des signaux suspects
     */
    private fun analyzeSender(phoneNumber: String): Pair<Int, String> {
        var score = 0
        val reasons = mutableListOf<String>()

        // Numéros courts suspects (souvent surtaxés)
        if (phoneNumber.length <= 6) {
            score += 2
            reasons.add("Numéro court suspect")
        }

        // Numéros commençant par des préfixes suspects
        when {
            phoneNumber.startsWith("08") -> {
                score += 3
                reasons.add("Numéro surtaxé (08)")
            }
            phoneNumber.matches(Regex("3[0-9]{4,7}")) -> {
                score += 3
                reasons.add("Numéro court surtaxé (3xxxx)")
            }
            phoneNumber.startsWith("+") && !phoneNumber.startsWith("+33") -> {
                score += 1
                reasons.add("Numéro international")
            }
        }

        // Numéros avec des patterns bizarres
        if (phoneNumber.contains(Regex("[a-zA-Z]"))) {
            score += 2
            reasons.add("Numéro avec lettres")
        }

        return Pair(score, reasons.joinToString(", "))
    }

    /**
     * Analyse la structure du message
     */
    private fun analyzeStructure(message: String): Pair<Int, String> {
        var score = 0
        val reasons = mutableListOf<String>()

        // Messages très courts (souvent des pièges)
        if (message.length < 20) {
            score += 1
            reasons.add("Message très court")
        }

        // Excès de majuscules
        val uppercaseRatio = message.count { it.isUpperCase() }.toFloat() / message.length
        if (uppercaseRatio > 0.5 && message.length > 10) {
            score += 2
            reasons.add("Excès de majuscules")
        }

        // Beaucoup d'émojis ou caractères spéciaux
        val specialChars = message.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (specialChars > message.length / 4) {
            score += 1
            reasons.add("Beaucoup de caractères spéciaux")
        }

        // Répétitions suspectes (!!!, ???, etc.)
        if (message.contains(Regex("[!?]{3,}"))) {
            score += 1
            reasons.add("Ponctuation excessive")
        }

        return Pair(score, reasons.joinToString(", "))
    }

    /**
     * Met à jour la liste des mots-clés suspects (pour configuration future)
     */
    fun updateFraudKeywords(newKeywords: List<String>) {
        // TODO: Implémenter la mise à jour dynamique des mots-clés
        Log.i(TAG, "Mise à jour des mots-clés: ${newKeywords.size} nouveaux mots")
    }
}