package fr.bonobo.stopdemarchage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import fr.bonobo.stopdemarchage.filter.AdvancedSpamDetector
import fr.bonobo.stopdemarchage.utils.CountryDetector
import org.json.JSONObject
import java.io.IOException

data class SpamDetectionResult(
    val isSpam: Boolean,
    val riskScore: Int,
    val riskLevel: String,
    val detectedPatterns: List<String>,
    val detectedWords: List<String>,
    val reason: String,
    val shouldBlock: Boolean
)

class SMSDetector(private val context: Context) {

    private val TAG = "SMSDetector"
    private var config: JSONObject? = null

    // Configuration chargée depuis le JSON (mode classique)
    private var highRiskPatterns = mutableListOf<Regex>()
    private var mediumRiskPatterns = mutableListOf<Regex>()
    private var highRiskWords = mutableListOf<String>()
    private var mediumRiskWords = mutableListOf<String>()
    private var courierScamPatterns = mutableListOf<Regex>()
    private var suspiciousDomainPatterns = mutableListOf<Regex>()
    private var suspiciousNumberPatterns = mutableListOf<Regex>()
    private var trustedSenders = mutableListOf<String>()
    private var trustedDomains = mutableListOf<String>()
    private var trustedNumbers = mutableListOf<String>()

    // Seuils de configuration
    private var riskScoreBlock = 6
    private var riskScoreWarn = 4
    private var riskScoreContact = 8

    // Détecteur avancé 2026
    private var advancedDetector: AdvancedSpamDetector? = null
    private var useAdvancedDetection = true

    init {
        loadConfiguration()
        initializeAdvancedDetector()
    }

    private fun initializeAdvancedDetector() {
        try {
            advancedDetector = AdvancedSpamDetector(context)
            Log.d(TAG, "Détecteur SMS avancé initialisé")

            val sharedPrefs = context.getSharedPreferences("StopDemarchagePrefs", Context.MODE_PRIVATE)
            useAdvancedDetection = sharedPrefs.getBoolean("use_advanced_sms_detection", true)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur init détecteur avancé, mode classique uniquement", e)
            useAdvancedDetection = false
        }
    }

    private fun loadConfiguration() {
        try {
            val jsonString = context.assets.open("spam_detection_config.json")
                .bufferedReader().use { it.readText() }
            config = JSONObject(jsonString).getJSONObject("spam_detection")

            loadPatterns()
            loadWeightedWords()
            loadThresholds()
            loadWhitelist()
            loadSuspiciousCharacteristics()

            Log.d(TAG, "Configuration classique chargée avec succès")
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors du chargement de la configuration", e)
            loadDefaultConfiguration()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing JSON", e)
            loadDefaultConfiguration()
        }
    }

    private fun loadPatterns() {
        config?.let { config ->
            val patterns = config.getJSONObject("patterns")

            val highRiskArray = patterns.getJSONArray("high_risk")
            for (i in 0 until highRiskArray.length()) {
                try {
                    highRiskPatterns.add(Regex(highRiskArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern invalide: ${highRiskArray.getString(i)}")
                }
            }

            val mediumRiskArray = patterns.getJSONArray("medium_risk")
            for (i in 0 until mediumRiskArray.length()) {
                try {
                    mediumRiskPatterns.add(Regex(mediumRiskArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern invalide: ${mediumRiskArray.getString(i)}")
                }
            }
        }
    }

    private fun loadWeightedWords() {
        config?.let { config ->
            val weightedWords = config.getJSONObject("weighted_words")

            val highRisk = weightedWords.getJSONObject("high_risk")
            val highRiskArray = highRisk.getJSONArray("words")
            for (i in 0 until highRiskArray.length()) {
                highRiskWords.add(highRiskArray.getString(i))
            }

            val mediumRisk = weightedWords.getJSONObject("medium_risk")
            val mediumRiskArray = mediumRisk.getJSONArray("words")
            for (i in 0 until mediumRiskArray.length()) {
                mediumRiskWords.add(mediumRiskArray.getString(i))
            }
        }
    }

    private fun loadThresholds() {
        config?.let { config ->
            val thresholds = config.getJSONObject("thresholds")
            riskScoreBlock = thresholds.optInt("risk_score_block", 6)
            riskScoreWarn = thresholds.optInt("risk_score_warn", 4)
            riskScoreContact = thresholds.optInt("risk_score_contact", 8)
        }
    }

    private fun loadWhitelist() {
        config?.let { config ->
            val whitelist = config.getJSONObject("whitelist")

            val trustedSendersArray = whitelist.getJSONArray("trusted_senders")
            for (i in 0 until trustedSendersArray.length()) {
                trustedSenders.add(trustedSendersArray.getString(i))
            }

            val trustedDomainsArray = whitelist.getJSONArray("trusted_domains")
            for (i in 0 until trustedDomainsArray.length()) {
                trustedDomains.add(trustedDomainsArray.getString(i))
            }

            val trustedNumbersArray = whitelist.getJSONArray("trusted_numbers")
            for (i in 0 until trustedNumbersArray.length()) {
                trustedNumbers.add(trustedNumbersArray.getString(i))
            }
        }
    }

    private fun loadSuspiciousCharacteristics() {
        config?.let { config ->
            val suspicious = config.getJSONObject("suspicious_characteristics")

            val suspiciousNumbers = suspicious.getJSONObject("suspicious_numbers")
            val numberPatternsArray = suspiciousNumbers.getJSONArray("patterns")
            for (i in 0 until numberPatternsArray.length()) {
                try {
                    suspiciousNumberPatterns.add(Regex(numberPatternsArray.getString(i)))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern numéro invalide: ${numberPatternsArray.getString(i)}")
                }
            }

            val suspiciousDomains = suspicious.getJSONObject("suspicious_domains")
            val domainPatternsArray = suspiciousDomains.getJSONArray("patterns")
            for (i in 0 until domainPatternsArray.length()) {
                try {
                    suspiciousDomainPatterns.add(Regex(domainPatternsArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern domaine invalide: ${domainPatternsArray.getString(i)}")
                }
            }

            val courierScam = suspicious.getJSONObject("courier_scam_patterns")
            val courierPatternsArray = courierScam.getJSONArray("patterns")
            for (i in 0 until courierPatternsArray.length()) {
                try {
                    courierScamPatterns.add(Regex(courierPatternsArray.getString(i), RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern coursier invalide: ${courierPatternsArray.getString(i)}")
                }
            }
        }
    }

    private fun loadDefaultConfiguration() {
        Log.w(TAG, "Chargement de la configuration par défaut")

        highRiskPatterns.addAll(listOf(
            Regex("vous\\s+avez\\s+gagn[eé]", RegexOption.IGNORE_CASE),
            Regex("compte\\s+(suspendu|bloqu[eé])", RegexOption.IGNORE_CASE),
            Regex("cliquez\\s+(ici|sur\\s+le\\s+lien)", RegexOption.IGNORE_CASE),
            Regex("urgent.*action\\s+requise", RegexOption.IGNORE_CASE)
        ))

        highRiskWords.addAll(listOf("compte suspendu", "cliquez ici", "vous avez gagne"))
        mediumRiskWords.addAll(listOf("gratuit", "promotion", "telecharger"))
        trustedNumbers.addAll(listOf("36180", "36179", "36173"))

        riskScoreBlock = 6
        riskScoreWarn = 4
    }

    // ============================================
    // ✅ NOUVEAU : Vérification contacts du téléphone
    // ============================================
    private fun isNumberInContacts(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

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
                    null,
                    null,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        Log.d(TAG, "👤 Contact trouvé pour SMS de: $format")
                        return true
                    }
                }
            }

            false
        } catch (e: SecurityException) {
            Log.e(TAG, "⚠️ Permission contacts manquante pour SMS", e)
            // En cas d'erreur de permission, ne pas filtrer par sécurité
            true
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Erreur vérification contacts SMS", e)
            true
        }
    }

    // ============================================
    // ✅ NOUVEAU : Vérification liste blanche
    // ============================================
    private fun isNumberInWhiteList(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        val sharedPrefs = context.getSharedPreferences("StopDemarchagePrefs", Context.MODE_PRIVATE)
        val contactsString = sharedPrefs.getString("white_list_contacts", "")
        if (contactsString.isNullOrEmpty()) return false

        // Extraire et normaliser les numéros
        val whiteListNumbers = contactsString.split(";").mapNotNull { contactData ->
            val parts = contactData.split("|")
            if (parts.size == 2) {
                normalizePhoneNumber(parts[1])
            } else {
                null
            }
        }.toSet()

        if (whiteListNumbers.isEmpty()) return false

        val formats = getAllFormats(phoneNumber)
        for (format in formats) {
            val normalizedFormat = normalizePhoneNumber(format)
            if (whiteListNumbers.contains(normalizedFormat)) {
                Log.d(TAG, "✅ SMS liste blanche : match pour $format")
                return true
            }
            for (whiteNumber in whiteListNumbers) {
                if (format == whiteNumber ||
                    normalizePhoneNumber(format) == whiteNumber) {
                    Log.d(TAG, "✅ SMS liste blanche : match $format = $whiteNumber")
                    return true
                }
            }
        }

        Log.d(TAG, "❌ SMS liste blanche : aucun match pour $phoneNumber")
        return false
    }

    // ============================================
    // ✅ NOUVEAU : Vérification liste noire
    // ============================================
    private fun isNumberInBlackList(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        val sharedPrefs = context.getSharedPreferences("StopDemarchagePrefs", Context.MODE_PRIVATE)
        val blackList = sharedPrefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
        if (blackList.isEmpty()) return false

        val formats = getAllFormats(phoneNumber)
        for (format in formats) {
            if (blackList.contains(format)) return true
            val normalized = normalizePhoneNumber(format)
            if (blackList.contains(normalized)) return true

            for (blocked in blackList) {
                if (blocked.replace("+", "").length >= 4) {
                    if (format.startsWith(blocked) ||
                        normalized.startsWith(normalizePhoneNumber(blocked))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    // ============================================
    // ✅ NOUVEAU : Génération de tous les formats
    // ============================================
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

    // ============================================
    // ✅ NOUVEAU : Normalisation des numéros
    // ============================================
    private fun normalizePhoneNumber(number: String): String {
        var normalized = number.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        if (normalized.startsWith("0") && normalized.length == 10 &&
            normalized.matches("^0[1-9][0-9]{8}$".toRegex())) {
            normalized = "+33" + normalized.substring(1)
        }

        return normalized
    }

    private fun normalizeText(text: String): String {
        var normalized = text.lowercase()

        config?.let { config ->
            val normalization = config.getJSONObject("normalization")

            if (normalization.optBoolean("remove_accents", true)) {
                normalized = normalized
                    .replace("[àâäáãå]".toRegex(), "a")
                    .replace("[éèêë]".toRegex(), "e")
                    .replace("[îïìí]".toRegex(), "i")
                    .replace("[ôöòóõ]".toRegex(), "o")
                    .replace("[ùûüú]".toRegex(), "u")
                    .replace("ç", "c")
                    .replace("ñ", "n")
            }

            if (normalization.optBoolean("normalize_spaces", true)) {
                normalized = normalized.replace("\\s+".toRegex(), " ")
            }

            normalized = normalized
                .replace("а", "a")
                .replace("е", "e")
                .replace("о", "o")
                .replace("р", "p")
                .replace("с", "c")
                .replace("у", "y")
                .replace("х", "x")
        }

        return normalized.trim()
    }

    private fun isFromTrustedSender(phoneNumber: String, message: String): Boolean {
        if (trustedNumbers.any { phoneNumber.contains(it) }) {
            return true
        }

        val normalizedMessage = normalizeText(message)
        return trustedSenders.any { sender ->
            normalizedMessage.contains(normalizeText(sender))
        }
    }

    private fun checkPatterns(message: String): Pair<List<String>, Int> {
        val normalizedMessage = normalizeText(message)
        val detectedPatterns = mutableListOf<String>()
        var patternScore = 0

        for (pattern in highRiskPatterns) {
            if (pattern.containsMatchIn(normalizedMessage)) {
                detectedPatterns.add("HIGH_RISK: ${pattern.pattern.take(30)}")
                patternScore += 4
            }
        }

        for (pattern in mediumRiskPatterns) {
            if (pattern.containsMatchIn(normalizedMessage)) {
                detectedPatterns.add("MEDIUM_RISK: ${pattern.pattern.take(30)}")
                patternScore += 2
            }
        }

        for (pattern in courierScamPatterns) {
            if (pattern.containsMatchIn(normalizedMessage)) {
                detectedPatterns.add("COURIER_SCAM: ${pattern.pattern.take(30)}")
                patternScore += 5
            }
        }

        return Pair(detectedPatterns, patternScore)
    }

    private fun calculateWordScore(message: String): Pair<List<String>, Int> {
        val normalizedMessage = normalizeText(message)
        val detectedWords = mutableListOf<String>()
        var wordScore = 0

        for (word in highRiskWords) {
            val normalizedWord = normalizeText(word)
            if (normalizedMessage.contains(normalizedWord)) {
                detectedWords.add("$word (+4)")
                wordScore += 4
            }
        }

        for (word in mediumRiskWords) {
            val normalizedWord = normalizeText(word)
            if (normalizedMessage.contains(normalizedWord)) {
                detectedWords.add("$word (+2)")
                wordScore += 2
            }
        }

        return Pair(detectedWords, wordScore)
    }

    private fun checkSuspiciousCharacteristics(message: String, phoneNumber: String): Pair<Boolean, String?> {
        val normalizedMessage = normalizeText(message)

        config?.let { config ->
            val suspicious = config.getJSONObject("suspicious_characteristics")

            val shortWithLink = suspicious.getJSONObject("short_with_link")
            val maxLength = shortWithLink.getInt("max_length")
            val containsArray = shortWithLink.getJSONArray("contains")

            if (message.length < maxLength) {
                for (i in 0 until containsArray.length()) {
                    val linkPattern = containsArray.getString(i)
                    if (normalizedMessage.contains(linkPattern)) {
                        return Pair(true, "SMS court avec lien suspect ($linkPattern)")
                    }
                }
            }

            val excessiveCaps = suspicious.getJSONObject("excessive_caps")
            val capsThreshold = excessiveCaps.getDouble("threshold")
            val upperCaseCount = message.count { it.isUpperCase() }
            if (message.length > 10 && upperCaseCount > message.length * capsThreshold) {
                return Pair(true, "Trop de majuscules (${upperCaseCount}/${message.length})")
            }

            val excessiveExclamation = suspicious.getJSONObject("excessive_exclamation")
            val exclamationThreshold = excessiveExclamation.getInt("threshold")
            val exclamationCount = message.count { it == '!' }
            if (exclamationCount >= exclamationThreshold) {
                return Pair(true, "Trop de points d'exclamation ($exclamationCount)")
            }
        }

        for (pattern in suspiciousNumberPatterns) {
            if (pattern.containsMatchIn(message)) {
                return Pair(true, "Numéro suspect dans le message")
            }
        }

        for (pattern in suspiciousDomainPatterns) {
            if (pattern.containsMatchIn(normalizedMessage)) {
                return Pair(true, "Domaine suspect détecté")
            }
        }

        return Pair(false, null)
    }

    // ============================================
    // ✅ CORRIGÉ : Analyse SMS avec priorité contacts/liste blanche
    // ============================================
    fun analyzeSMS(phoneNumber: String, message: String): SpamDetectionResult {
        if (message.isBlank()) {
            return SpamDetectionResult(
                false, 0, "LOW", emptyList(), emptyList(),
                "Message vide", false
            )
        }

        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "📱 ANALYSE SMS de $phoneNumber")
        Log.d(TAG, "Message: ${message.take(80)}...")
        Log.d(TAG, "═══════════════════════════════════════")

        // ============================================
        // ÉTAPE 1 : Contact du téléphone → NE PAS FILTRER
        // ============================================
        if (isNumberInContacts(phoneNumber)) {
            Log.d(TAG, "👤 SKIP FILTRAGE : numéro dans les contacts")
            return SpamDetectionResult(
                isSpam = false,
                riskScore = 0,
                riskLevel = "SAFE",
                detectedPatterns = emptyList(),
                detectedWords = emptyList(),
                reason = "👤 Contact du téléphone — Non filtré",
                shouldBlock = false
            )
        }

        // ============================================
        // ÉTAPE 2 : Liste blanche → NE PAS FILTRER
        // ============================================
        if (isNumberInWhiteList(phoneNumber)) {
            Log.d(TAG, "✅ SKIP FILTRAGE : numéro en liste blanche")
            return SpamDetectionResult(
                isSpam = false,
                riskScore = 0,
                riskLevel = "SAFE",
                detectedPatterns = emptyList(),
                detectedWords = emptyList(),
                reason = "✅ Liste blanche — Non filtré",
                shouldBlock = false
            )
        }

        // ============================================
        // ÉTAPE 3 : Liste noire → BLOQUER directement
        // ============================================
        if (isNumberInBlackList(phoneNumber)) {
            Log.d(TAG, "🚫 BLOQUER : numéro en liste noire")
            return SpamDetectionResult(
                isSpam = true,
                riskScore = 100,
                riskLevel = "CRITICAL",
                detectedPatterns = listOf("BLACKLIST"),
                detectedWords = emptyList(),
                reason = "🚫 Liste noire — Bloqué automatiquement",
                shouldBlock = true
            )
        }

        // ============================================
        // ÉTAPE 4 : Expéditeurs de confiance (config JSON)
        // ============================================
        if (isFromTrustedSender(phoneNumber, message)) {
            Log.d(TAG, "✓ Expéditeur de confiance détecté")
            return SpamDetectionResult(
                false, 0, "LOW", emptyList(), emptyList(),
                "Expéditeur de confiance", false
            )
        }

        // ============================================
        // ÉTAPE 5 : Mode avancé 2026
        // ============================================
        if (useAdvancedDetection && advancedDetector != null) {
            try {
                Log.d(TAG, "→ Détection avancée 2026")

                val country = CountryDetector.detectCountry(phoneNumber)
                val advancedDecision = advancedDetector!!.isSmsSpam(
                    phoneNumber, message, country
                )

                Log.d(TAG, "Résultat avancé: Block=${advancedDecision.shouldBlock}, " +
                        "Score=${advancedDecision.riskScore}")

                return SpamDetectionResult(
                    isSpam = advancedDecision.shouldBlock,
                    riskScore = advancedDecision.riskScore,
                    riskLevel = advancedDecision.riskLevel.name,
                    detectedPatterns = emptyList(),
                    detectedWords = emptyList(),
                    reason = advancedDecision.reason,
                    shouldBlock = advancedDecision.shouldBlock
                )

            } catch (e: Exception) {
                Log.e(TAG, "Erreur détection avancée, fallback classique", e)
            }
        }

        // ============================================
        // ÉTAPE 6 : Mode classique (fallback)
        // ============================================
        Log.d(TAG, "→ Détection classique")

        val (detectedPatterns, patternScore) = checkPatterns(message)
        val (detectedWords, wordScore) = calculateWordScore(message)
        val totalScore = patternScore + wordScore
        val (hasSuspiciousChar, suspiciousReason) =
            checkSuspiciousCharacteristics(message, phoneNumber)

        val riskLevel = when {
            totalScore >= riskScoreBlock || hasSuspiciousChar -> "CRITICAL"
            totalScore >= riskScoreWarn -> "MEDIUM"
            else -> "LOW"
        }

        val isSpam = totalScore >= riskScoreBlock || hasSuspiciousChar
        val shouldBlock = config?.getJSONObject("thresholds")
            ?.optBoolean("pattern_match_block", true) == true && isSpam

        val reasons = mutableListOf<String>()
        if (detectedPatterns.isNotEmpty()) reasons.add("Patterns: ${detectedPatterns.size}")
        if (detectedWords.isNotEmpty()) reasons.add("Mots: ${detectedWords.size}")
        if (hasSuspiciousChar) reasons.add(suspiciousReason ?: "Caractéristiques suspectes")

        val finalReason = if (reasons.isEmpty()) {
            "Message semble légitime"
        } else {
            "Score: $totalScore - ${reasons.joinToString(", ")}"
        }

        Log.d(TAG, "Résultat: isSpam=$isSpam, score=$totalScore, level=$riskLevel")
        Log.d(TAG, "═══════════════════════════════════════")

        return SpamDetectionResult(
            isSpam = isSpam,
            riskScore = totalScore,
            riskLevel = riskLevel,
            detectedPatterns = detectedPatterns,
            detectedWords = detectedWords,
            reason = finalReason,
            shouldBlock = shouldBlock
        )
    }

    fun isSpamMessage(message: String): Boolean {
        return analyzeSMS("", message).isSpam
    }

    fun setAdvancedDetectionMode(enabled: Boolean) {
        useAdvancedDetection = enabled
        val sharedPrefs = context.getSharedPreferences("StopDemarchagePrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("use_advanced_sms_detection", enabled).apply()
        Log.d(TAG, "Mode SMS détection avancée: $enabled")
    }

    fun isAdvancedModeEnabled(): Boolean {
        return useAdvancedDetection && advancedDetector != null
    }

    fun updateConfiguration(newConfigJson: String): Boolean {
        return try {
            config = JSONObject(newConfigJson).getJSONObject("spam_detection")
            loadPatterns()
            loadWeightedWords()
            loadThresholds()
            loadWhitelist()
            loadSuspiciousCharacteristics()
            Log.d(TAG, "Configuration classique mise à jour avec succès")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour de la configuration", e)
            false
        }
    }
}