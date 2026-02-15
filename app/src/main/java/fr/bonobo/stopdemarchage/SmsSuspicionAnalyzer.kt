package fr.bonobo.stopdemarchage

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.text.Normalizer
import java.util.*
import kotlin.math.min

data class SuspicionResult(
    val score: Int,                    // Score de 0-100%
    val risk: SuspicionLevel,          // Niveau de risque
    val detectedWords: List<String>,   // Mots détectés
    val detectedPatterns: List<String>, // Patterns détectés
    val explanation: String            // Explication du score
)

enum class SuspicionLevel {
    LOW,      // 0-30%
    MEDIUM,   // 31-60%
    HIGH,     // 61-80%
    CRITICAL  // 81-100%
}

class SmsSuspicionAnalyzer(
    private val context: Context,
    private val country: String = "FR" // "FR" ou "BE"
) {

    // Configuration originale (blocked_words_xx.json)
    private var blockedWords: Map<String, List<String>> = emptyMap()
    private var categoryWeights: Map<String, Double> = emptyMap()

    // Nouvelle configuration (spam_detection_config_xx.json)
    private var highRiskPatterns = mutableListOf<Regex>()
    private var mediumRiskPatterns = mutableListOf<Regex>()
    private var highRiskWords = mutableListOf<String>()
    private var mediumRiskWords = mutableListOf<String>()
    private var courierScamPatterns = mutableListOf<Regex>()
    private var suspiciousDomainPatterns = mutableListOf<Regex>()
    private var suspiciousNumberPatterns = mutableListOf<Regex>()
    private var trustedSenders = mutableListOf<String>()
    private var trustedNumbers = mutableListOf<String>()

    private var config: JSONObject? = null
    private val TAG = "SmsSuspicionAnalyzer"

    init {
        Log.d(TAG, "Initialisation pour le pays: $country")
        loadBlockedWords(country)
        loadSpamDetectionConfig(country)
        setupWeights()
    }

    private fun loadBlockedWords(country: String) {
        try {
            val fileName = when(country.uppercase()) {
                "BE" -> "blocked_words_be.json"
                else -> "blocked_words_fr.json"
            }

            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)

            // Vérifier si la structure existe
            if (!json.has("blocked_words")) {
                Log.w(TAG, "Structure 'blocked_words' non trouvée dans $fileName")
                return
            }

            val blockedWordsSection = json.getJSONObject("blocked_words")

            // Charger la configuration
            if (blockedWordsSection.has("config")) {
                config = blockedWordsSection.getJSONObject("config")
                Log.d(TAG, "Config chargée: ${config?.optString("country")}")
            }

            // Charger les catégories
            if (blockedWordsSection.has("categories")) {
                val categories = blockedWordsSection.getJSONObject("categories")
                val mutableBlockedWords = mutableMapOf<String, List<String>>()

                val categoryNames = categories.keys()
                while (categoryNames.hasNext()) {
                    val category = categoryNames.next()
                    val wordsArray = categories.getJSONArray(category)
                    val wordsList = mutableListOf<String>()
                    for (i in 0 until wordsArray.length()) {
                        wordsList.add(normalizeText(wordsArray.getString(i)))
                    }
                    mutableBlockedWords[category] = wordsList
                }

                blockedWords = mutableBlockedWords
                Log.d(TAG, "Chargé ${blockedWords.size} catégories de mots depuis $fileName")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Fichier blocked_words_$country.json non trouvé", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing de blocked_words_$country.json", e)
        }
    }

    private fun loadSpamDetectionConfig(country: String) {
        try {
            val fileName = when(country.uppercase()) {
                "BE" -> "spam_detection_config_be.json"
                else -> "spam_detection_config_fr.json"
            }

            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

            // Votre structure utilise "config" -> "spam_detection"
            val rootConfig = JSONObject(jsonString)
            val spamConfig = if (rootConfig.has("config")) {
                rootConfig.getJSONObject("config").getJSONObject("spam_detection")
            } else if (rootConfig.has("spam_detection")) {
                rootConfig.getJSONObject("spam_detection")
            } else {
                throw Exception("Structure spam_detection non trouvée")
            }

            loadAdvancedPatterns(spamConfig)
            loadAdvancedWords(spamConfig)
            loadWhitelist(spamConfig)
            loadSuspiciousCharacteristics(spamConfig)

            Log.d(TAG, "Configuration spam avancée chargée avec succès pour $country")
        } catch (e: IOException) {
            Log.w(TAG, "spam_detection_config_$country.json non trouvé, utilisation de la configuration de base uniquement")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement de spam_detection_config_$country.json", e)
        }
    }

    private fun loadAdvancedPatterns(spamConfig: JSONObject) {
        try {
            val patterns = spamConfig.getJSONObject("patterns")

            // Patterns haut risque
            val highRiskArray = patterns.getJSONArray("high_risk")
            for (i in 0 until highRiskArray.length()) {
                try {
                    highRiskPatterns.add(Regex(highRiskArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern haut risque invalide: ${highRiskArray.getString(i)}")
                }
            }

            // Patterns risque moyen
            val mediumRiskArray = patterns.getJSONArray("medium_risk")
            for (i in 0 until mediumRiskArray.length()) {
                try {
                    mediumRiskPatterns.add(Regex(mediumRiskArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern risque moyen invalide: ${mediumRiskArray.getString(i)}")
                }
            }

            Log.d(TAG, "Chargé ${highRiskPatterns.size} patterns haut risque et ${mediumRiskPatterns.size} patterns risque moyen")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des patterns", e)
        }
    }

    private fun loadAdvancedWords(spamConfig: JSONObject) {
        try {
            val weightedWords = spamConfig.getJSONObject("weighted_words")

            // Mots haut risque
            val highRisk = weightedWords.getJSONObject("high_risk")
            val highRiskArray = highRisk.getJSONArray("words")
            for (i in 0 until highRiskArray.length()) {
                highRiskWords.add(normalizeText(highRiskArray.getString(i)))
            }

            // Mots risque moyen
            val mediumRisk = weightedWords.getJSONObject("medium_risk")
            val mediumRiskArray = mediumRisk.getJSONArray("words")
            for (i in 0 until mediumRiskArray.length()) {
                mediumRiskWords.add(normalizeText(mediumRiskArray.getString(i)))
            }

            Log.d(TAG, "Chargé ${highRiskWords.size} mots haut risque et ${mediumRiskWords.size} mots risque moyen")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des mots pondérés", e)
        }
    }

    private fun loadWhitelist(spamConfig: JSONObject) {
        try {
            val whitelist = spamConfig.getJSONObject("whitelist")

            // Expéditeurs de confiance
            val trustedSendersArray = whitelist.getJSONArray("trusted_senders")
            for (i in 0 until trustedSendersArray.length()) {
                trustedSenders.add(normalizeText(trustedSendersArray.getString(i)))
            }

            // Numéros de confiance
            val trustedNumbersArray = whitelist.getJSONArray("trusted_numbers")
            for (i in 0 until trustedNumbersArray.length()) {
                trustedNumbers.add(trustedNumbersArray.getString(i))
            }

            Log.d(TAG, "Chargé ${trustedSenders.size} expéditeurs de confiance et ${trustedNumbers.size} numéros de confiance")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement de la liste blanche", e)
        }
    }

    private fun loadSuspiciousCharacteristics(spamConfig: JSONObject) {
        try {
            val suspicious = spamConfig.getJSONObject("suspicious_characteristics")

            // Patterns de numéros suspects
            val suspiciousNumbers = suspicious.getJSONObject("suspicious_numbers")
            val numberPatternsArray = suspiciousNumbers.getJSONArray("patterns")
            for (i in 0 until numberPatternsArray.length()) {
                try {
                    suspiciousNumberPatterns.add(Regex(numberPatternsArray.getString(i)))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern numéro invalide: ${numberPatternsArray.getString(i)}")
                }
            }

            // Patterns de domaines suspects
            val suspiciousDomains = suspicious.getJSONObject("suspicious_domains")
            val domainPatternsArray = suspiciousDomains.getJSONArray("patterns")
            for (i in 0 until domainPatternsArray.length()) {
                try {
                    suspiciousDomainPatterns.add(Regex(domainPatternsArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern domaine invalide: ${domainPatternsArray.getString(i)}")
                }
            }

            // Patterns spécifiques aux arnaques coursier
            val courierScam = suspicious.getJSONObject("courier_scam_patterns")
            val courierPatternsArray = courierScam.getJSONArray("patterns")
            for (i in 0 until courierPatternsArray.length()) {
                try {
                    courierScamPatterns.add(Regex(courierPatternsArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern coursier invalide: ${courierPatternsArray.getString(i)}")
                }
            }

            Log.d(TAG, "Chargé ${courierScamPatterns.size} patterns coursier, ${suspiciousDomainPatterns.size} domaines suspects")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des caractéristiques suspectes", e)
        }
    }

    private fun setupWeights() {
        categoryWeights = mapOf(
            "urgency" to 3.0,      // Mots d'urgence = très suspect
            "banking" to 2.8,      // Bancaire = très dangereux
            "phishing" to 3.0,     // Liens = très dangereux
            "technical" to 2.5,    // Virus/hack = dangereux
            "gains" to 2.3,        // Gains = suspect
            "delivery" to 1.8,     // Colis = modérément suspect
            "call" to 1.2,         // Appel = peu suspect
            "government" to 2.5    // Gouvernement = dangereux
        )
    }

    /**
     * Vérifie si l'expéditeur est de confiance
     */
    private fun isFromTrustedSender(phoneNumber: String, message: String): Boolean {
        // Vérification des numéros de confiance
        if (trustedNumbers.any { phoneNumber.contains(it) }) {
            return true
        }

        // Vérification des expéditeurs de confiance dans le message
        val normalizedMessage = normalizeText(message)
        return trustedSenders.any { sender ->
            normalizedMessage.contains(sender)
        }
    }

    /**
     * Analyse principale d'un SMS - Version fusionnée
     */
    fun analyzeSms(phoneNumber: String, messageBody: String): SuspicionResult {
        if (messageBody.isBlank()) {
            return SuspicionResult(0, SuspicionLevel.LOW, emptyList(), emptyList(), "Message vide")
        }

        // Vérification de la liste blanche
        if (isFromTrustedSender(phoneNumber, messageBody)) {
            Log.d(TAG, "Expéditeur de confiance détecté")
            return SuspicionResult(0, SuspicionLevel.LOW, emptyList(), emptyList(), "Expéditeur de confiance")
        }

        val normalizedMessage = normalizeText(messageBody)
        var totalScore = 0.0
        val detectedWords = mutableListOf<String>()
        val detectedPatterns = mutableListOf<String>()
        val explanations = mutableListOf<String>()

        // 1. Analyse des mots par catégorie (système original)
        blockedWords.forEach { (category, words) ->
            val categoryWords = findWordsInMessage(normalizedMessage, words)
            if (categoryWords.isNotEmpty()) {
                val weight = categoryWeights[category] ?: 1.0
                val categoryScore = categoryWords.size * weight * 8 // Base de 8 points par mot
                totalScore += categoryScore

                detectedWords.addAll(categoryWords)
                explanations.add("$category: ${categoryWords.size} mot(s) (+${categoryScore.toInt()})")
            }
        }

        // 2. Analyse des nouveaux patterns avancés
        val advancedPatternScore = analyzeAdvancedPatterns(normalizedMessage, detectedPatterns)
        totalScore += advancedPatternScore

        // 3. Analyse des nouveaux mots pondérés
        val advancedWordScore = analyzeAdvancedWords(normalizedMessage, detectedWords)
        totalScore += advancedWordScore
        if (advancedWordScore > 0) {
            explanations.add("Mots suspects: +${advancedWordScore.toInt()}")
        }

        // 4. Analyse des patterns originaux (pour compatibilité)
        val originalPatternScore = analyzeOriginalPatterns(phoneNumber, normalizedMessage, detectedPatterns)
        totalScore += originalPatternScore

        // 5. Analyse contextuelle améliorée
        val contextScore = analyzeAdvancedContext(phoneNumber, messageBody, normalizedMessage)
        totalScore += contextScore
        if (contextScore > 0) {
            explanations.add("Contexte suspect (+${contextScore.toInt()})")
        }

        // 6. Calcul final du score (max 100)
        val finalScore = min(totalScore.toInt(), 100)
        val risk = when (finalScore) {
            in 0..30 -> SuspicionLevel.LOW
            in 31..60 -> SuspicionLevel.MEDIUM
            in 61..80 -> SuspicionLevel.HIGH
            else -> SuspicionLevel.CRITICAL
        }

        val explanation = if (explanations.isNotEmpty()) {
            explanations.joinToString(", ")
        } else {
            "Aucun indicateur suspect détecté"
        }

        Log.d(TAG, "Score final: $finalScore, Risque: $risk, Pays: $country")

        return SuspicionResult(
            score = finalScore,
            risk = risk,
            detectedWords = detectedWords.distinct(),
            detectedPatterns = detectedPatterns.distinct(),
            explanation = explanation
        )
    }

    private fun analyzeAdvancedPatterns(message: String, detectedPatterns: MutableList<String>): Double {
        var score = 0.0

        // Patterns haut risque (+30 points)
        for (pattern in highRiskPatterns) {
            if (pattern.containsMatchIn(message)) {
                score += 30
                detectedPatterns.add("Haut risque: ${pattern.pattern.take(25)}...")
                Log.d(TAG, "Pattern haut risque détecté: ${pattern.pattern}")
            }
        }

        // Patterns risque moyen (+15 points)
        for (pattern in mediumRiskPatterns) {
            if (pattern.containsMatchIn(message)) {
                score += 15
                detectedPatterns.add("Risque moyen: ${pattern.pattern.take(25)}...")
                Log.d(TAG, "Pattern risque moyen détecté: ${pattern.pattern}")
            }
        }

        // Patterns spécifiques coursier (+35 points car très actuels et dangereux)
        for (pattern in courierScamPatterns) {
            if (pattern.containsMatchIn(message)) {
                score += 35
                detectedPatterns.add("Arnaque coursier: ${pattern.pattern.take(25)}...")
                Log.d(TAG, "Pattern arnaque coursier détecté: ${pattern.pattern}")
            }
        }

        return score
    }

    private fun analyzeAdvancedWords(message: String, detectedWords: MutableList<String>): Double {
        var score = 0.0

        // Mots haut risque (+20 points)
        for (word in highRiskWords) {
            if (message.contains(word)) {
                score += 20
                detectedWords.add("$word (+20)")
                Log.d(TAG, "Mot haut risque détecté: $word")
            }
        }

        // Mots risque moyen (+10 points)
        for (word in mediumRiskWords) {
            if (message.contains(word)) {
                score += 10
                detectedWords.add("$word (+10)")
                Log.d(TAG, "Mot risque moyen détecté: $word")
            }
        }

        return score
    }

    private fun analyzeOriginalPatterns(phoneNumber: String, message: String, detectedPatterns: MutableList<String>): Double {
        var score = 0.0

        // Pattern 1: Urgence + Lien
        if (containsWords(message, listOf("urgent", "immediat", "action")) &&
            containsWords(message, listOf("http", "www", "bit.ly", "lien"))) {
            score += 25
            detectedPatterns.add("Urgence + Lien")
        }

        // Pattern 2: Argent + Action
        if (containsWords(message, listOf("virement", "argent", "gain", "€", "euro")) &&
            containsWords(message, listOf("cliquer", "appeler", "repondre"))) {
            score += 20
            detectedPatterns.add("Argent + Action requise")
        }

        // Pattern 3: Fausse entreprise + Problème
        if (containsWords(message, listOf("amazon", "paypal", "netflix", "google")) &&
            containsWords(message, listOf("probleme", "suspension", "verification"))) {
            score += 22
            detectedPatterns.add("Fausse entreprise")
        }

        // Pattern 4: Numéro suspect (amélioré avec les nouveaux patterns)
        if (isPhoneNumberSuspiciousAdvanced(phoneNumber)) {
            score += 15
            detectedPatterns.add("Numéro suspect")
        }

        // Pattern 5: Pression temporelle
        if (containsWords(message, listOf("24h", "immediat", "expir", "dernier", "echeanc"))) {
            score += 12
            detectedPatterns.add("Pression temporelle")
        }

        return score
    }

    private fun analyzeAdvancedContext(phoneNumber: String, originalMessage: String, normalizedMessage: String): Double {
        var score = 0.0

        // Message très court avec lien = suspect
        if (originalMessage.length < 50 && containsWords(normalizedMessage, listOf("http", "www", "bit.ly"))) {
            score += 15
        }

        // Trop de liens
        val linkCount = countLinks(originalMessage)
        if (linkCount > 1) {
            score += linkCount * 8
        }

        // Message en majuscules (agressif)
        val upperCaseRatio = originalMessage.count { it.isUpperCase() }.toDouble() / originalMessage.length
        if (upperCaseRatio > 0.7 && originalMessage.length > 10) {
            score += 10
        }

        // Trop de points d'exclamation (≥ 3)
        val exclamationCount = originalMessage.count { it == '!' }
        if (exclamationCount >= 3) {
            score += 8
        }

        // Domaines suspects
        for (pattern in suspiciousDomainPatterns) {
            if (pattern.containsMatchIn(normalizedMessage)) {
                score += 25
                Log.d(TAG, "Domaine suspect détecté: ${pattern.pattern}")
                break
            }
        }

        // Fautes d'orthographe typiques
        if (containsTypicalMistakes(normalizedMessage)) {
            score += 8
        }

        return score
    }

    private fun isPhoneNumberSuspiciousAdvanced(phoneNumber: String): Boolean {
        // Vérification avec les nouveaux patterns
        for (pattern in suspiciousNumberPatterns) {
            if (pattern.matches(phoneNumber)) {
                return true
            }
        }

        // Vérifications originales
        if (phoneNumber.startsWith("08") || phoneNumber.startsWith("09")) return true
        if (phoneNumber.startsWith("+") && !phoneNumber.startsWith("+33") && !phoneNumber.startsWith("+32")) return true

        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        return cleanNumber.length < 8 || cleanNumber.length > 12
    }

    private fun findWordsInMessage(message: String, words: List<String>): List<String> {
        return words.filter { word ->
            message.contains(word, ignoreCase = true)
        }
    }

    private fun containsWords(message: String, words: List<String>): Boolean {
        return words.any { word ->
            message.contains(word, ignoreCase = true)
        }
    }

    private fun countLinks(message: String): Int {
        val linkPatterns = listOf("http://", "https://", "www.", "bit.ly", "tinyurl")
        return linkPatterns.sumOf { pattern ->
            message.split(pattern).size - 1
        }
    }

    private fun containsTypicalMistakes(message: String): Boolean {
        val mistakes = listOf(
            "votre compte a ete",
            "clickez",
            "telephonez",
            "recu"
        )

        return mistakes.any { wrong ->
            message.contains(wrong, ignoreCase = true)
        }
    }

    private fun normalizeText(text: String): String {
        var normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            .lowercase(Locale.getDefault())

        // Substitution de caractères Unicode suspects (utilisés dans les arnaques)
        normalized = normalized
            .replace("а", "a") // Cyrillic a
            .replace("е", "e") // Cyrillic e
            .replace("о", "o") // Cyrillic o
            .replace("р", "p") // Cyrillic p
            .replace("с", "c") // Cyrillic c
            .replace("у", "y") // Cyrillic y
            .replace("х", "x") // Cyrillic x
            .replace("\\s+".toRegex(), " ") // Normaliser les espaces
            .trim()

        return normalized
    }

    /**
     * Méthode utilitaire pour tester un SMS rapidement
     */
    fun isSmsSuspicious(phoneNumber: String, messageBody: String): Boolean {
        val result = analyzeSms(phoneNumber, messageBody)
        return result.risk in listOf(SuspicionLevel.HIGH, SuspicionLevel.CRITICAL)
    }

    /**
     * Obtenir le niveau de risque sous forme de texte
     */
    fun getRiskText(level: SuspicionLevel): String {
        return when (level) {
            SuspicionLevel.LOW -> "Faible"
            SuspicionLevel.MEDIUM -> "Modéré"
            SuspicionLevel.HIGH -> "Élevé"
            SuspicionLevel.CRITICAL -> "Critique"
        }
    }

    /**
     * Obtenir la couleur correspondant au niveau de risque
     */
    fun getRiskColor(level: SuspicionLevel): Int {
        return when (level) {
            SuspicionLevel.LOW -> android.R.color.holo_green_light
            SuspicionLevel.MEDIUM -> android.R.color.holo_orange_light
            SuspicionLevel.HIGH -> android.R.color.holo_orange_dark
            SuspicionLevel.CRITICAL -> android.R.color.holo_red_dark
        }
    }

    /**
     * Obtenir le pays configuré
     */
    fun getCountry(): String {
        return country
    }
}