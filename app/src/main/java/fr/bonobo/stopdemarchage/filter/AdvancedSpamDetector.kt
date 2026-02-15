package fr.bonobo.stopdemarchage.filter

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

/**
 * Détecteur de spam avancé 2026
 * Utilise les nouveaux fichiers JSON complets avec algorithmes de détection
 */
class AdvancedSpamDetector(private val context: Context) {

    companion object {
        private const val TAG = "AdvancedSpamDetector"
        private const val FILE_FR = "prefixes_blocked_fr.json"
        private const val FILE_BE = "prefixes_blocked_be.json"
    }

    private var frenchConfig: JSONObject? = null
    private var belgianConfig: JSONObject? = null

    init {
        loadConfigurations()
    }

    /**
     * Charge les fichiers de configuration depuis assets/
     */
    private fun loadConfigurations() {
        try {
            frenchConfig = loadJSONFromAssets(FILE_FR)
            belgianConfig = loadJSONFromAssets(FILE_BE)

            Log.d(TAG, "Configurations chargées avec succès")
            Log.d(TAG, "Version FR: ${frenchConfig?.optString("version", "unknown")}")
            Log.d(TAG, "Version BE: ${belgianConfig?.optString("version", "unknown")}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement configurations", e)
        }
    }

    /**
     * Charge un fichier JSON depuis assets/
     */
    private fun loadJSONFromAssets(fileName: String): JSONObject {
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            JSONObject(jsonString)
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lecture fichier $fileName", e)
            JSONObject()
        } catch (e: JSONException) {
            Log.e(TAG, "Erreur parsing JSON $fileName", e)
            JSONObject()
        }
    }

    /**
     * Décision de blocage avec détails
     */
    data class BlockDecision(
        val shouldBlock: Boolean,
        val reason: String,
        val riskLevel: RiskLevel = RiskLevel.NONE,
        val askUser: Boolean = false,
        val riskScore: Int = 0
    )

    enum class RiskLevel {
        NONE,           // Légitime
        LOW,            // Suspect faible
        MEDIUM,         // Suspect moyen
        HIGH,           // Suspect élevé
        CRITICAL        // Critique - Bloquer immédiatement
    }

    /**
     * Point d'entrée principal - Détermine si un numéro doit être bloqué
     */
    fun shouldBlockNumber(phoneNumber: String, country: String = "FR"): BlockDecision {
        val config = if (country == "BE") belgianConfig else frenchConfig

        if (config == null) {
            Log.w(TAG, "Configuration non disponible pour $country")
            return BlockDecision(false, "Config unavailable")
        }

        // Normaliser le numéro (format national)
        val normalizedNumber = normalizePhoneNumber(phoneNumber, country)

        // IMPORTANT: Aussi obtenir format international pour patterns
        val internationalNumber = getInternationalFormat(phoneNumber, country)

        Log.d(TAG, "Checking: Original=$phoneNumber, National=$normalizedNumber, Intl=$internationalNumber")

        // 1. Vérifier numéros d'urgence (ne JAMAIS bloquer)
        if (isEmergencyNumber(normalizedNumber, config)) {
            return BlockDecision(false, "Emergency number", RiskLevel.NONE)
        }

        // 2. Vérifier whitelist
        if (isWhitelisted(normalizedNumber, config)) {
            return BlockDecision(false, "Whitelisted", RiskLevel.NONE)
        }

        // 3. Vérifier base de données spam connus
        val knownSpam = checkKnownSpam(normalizedNumber, config)
        if (knownSpam != null) {
            return BlockDecision(
                shouldBlock = true,
                reason = "Known spam: ${knownSpam.category}",
                riskLevel = knownSpamSeverityToRiskLevel(knownSpam.severity)
            )
        }

        // 4. Vérifier préfixes always_block (NOUVEAU: teste les deux formats)
        val alwaysBlockResult = checkAlwaysBlock(normalizedNumber, internationalNumber, config, country)
        if (alwaysBlockResult != null) {
            return alwaysBlockResult
        }

        // 5. Calcul du score de risque avec algorithmes avancés
        val riskScore = calculateRiskScore(normalizedNumber, config, country)

        return when {
            riskScore >= 80 -> BlockDecision(
                shouldBlock = true,
                reason = "High risk score",
                riskLevel = RiskLevel.CRITICAL,
                riskScore = riskScore
            )
            riskScore >= 60 -> BlockDecision(
                shouldBlock = false,
                reason = "Suspicious - Recommend user check",
                riskLevel = RiskLevel.HIGH,
                askUser = true,
                riskScore = riskScore
            )
            riskScore >= 40 -> BlockDecision(
                shouldBlock = false,
                reason = "Moderately suspicious",
                riskLevel = RiskLevel.MEDIUM,
                riskScore = riskScore
            )
            else -> BlockDecision(
                shouldBlock = false,
                reason = "Legitimate",
                riskLevel = RiskLevel.LOW,
                riskScore = riskScore
            )
        }
    }

    /**
     * Analyse un SMS pour détecter le spam
     */
    fun isSmsSpam(sender: String, message: String, country: String = "FR"): BlockDecision {
        val config = if (country == "BE") belgianConfig else frenchConfig

        if (config == null) {
            return BlockDecision(false, "Config unavailable")
        }

        var riskScore = 0
        val reasons = mutableListOf<String>()

        // 1. Vérifier le numéro d'abord
        val numberDecision = shouldBlockNumber(sender, country)
        if (numberDecision.shouldBlock) {
            return numberDecision
        }

        riskScore += numberDecision.riskScore / 2 // Contribue au score mais pas décisif

        // 2. Analyser le contenu du message
        try {
            val algorithms = config.getJSONObject("detection_algorithms")
            val contentAnalysis = algorithms.getJSONObject("content_analysis")

            // Vérifier mots-clés critiques
            val criticalKeywords = contentAnalysis.getJSONObject("sms_keywords").getJSONArray("critical")
            val criticalCount = countKeywordMatches(message, criticalKeywords)
            if (criticalCount > 0) {
                riskScore += criticalCount * 30
                reasons.add("$criticalCount critical keywords")
            }

            // Vérifier mots-clés à risque élevé
            val highKeywords = contentAnalysis.getJSONObject("sms_keywords").getJSONArray("high")
            val highCount = countKeywordMatches(message, highKeywords)
            if (highCount > 0) {
                riskScore += highCount * 15
                reasons.add("$highCount high-risk keywords")
            }

            // Vérifier URLs suspectes
            if (containsSuspiciousUrl(message, contentAnalysis)) {
                riskScore += 40
                reasons.add("Suspicious URL")
            }

        } catch (e: JSONException) {
            Log.e(TAG, "Erreur analyse SMS", e)
        }

        return when {
            riskScore >= 80 -> BlockDecision(
                shouldBlock = true,
                reason = "SMS spam: ${reasons.joinToString(", ")}",
                riskLevel = RiskLevel.CRITICAL,
                riskScore = riskScore
            )
            riskScore >= 50 -> BlockDecision(
                shouldBlock = false,
                reason = "Suspicious SMS: ${reasons.joinToString(", ")}",
                riskLevel = RiskLevel.HIGH,
                askUser = true,
                riskScore = riskScore
            )
            else -> BlockDecision(
                shouldBlock = false,
                reason = "Legitimate SMS",
                riskLevel = RiskLevel.LOW,
                riskScore = riskScore
            )
        }
    }

    /**
     * Normalise un numéro de téléphone
     * IMPORTANT: Conserve aussi le format international pour comparaison
     */
    private fun normalizePhoneNumber(phoneNumber: String, country: String): String {
        var normalized = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Convertir 00XX en +XX
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2)
        }

        // Convertir format international en national
        if (country == "FR" && normalized.startsWith("+33")) {
            normalized = "0" + normalized.substring(3)
        } else if (country == "BE" && normalized.startsWith("+32")) {
            normalized = "0" + normalized.substring(3)
        }

        return normalized
    }

    /**
     * NOUVEAU: Obtient le numéro au format international (pour matching patterns)
     */
    private fun getInternationalFormat(phoneNumber: String, country: String): String {
        var normalized = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Si déjà en format international
        if (normalized.startsWith("+")) {
            return normalized.substring(1) // Enlever le +
        }

        // Convertir 00XX en numéro international
        if (normalized.startsWith("00")) {
            return normalized.substring(2)
        }

        // Convertir format national en international
        if (country == "FR" && normalized.startsWith("0")) {
            return "33" + normalized.substring(1)
        } else if (country == "BE" && normalized.startsWith("0")) {
            return "32" + normalized.substring(1)
        }

        return normalized
    }

    /**
     * Vérifie si c'est un numéro d'urgence
     */
    private fun isEmergencyNumber(number: String, config: JSONObject): Boolean {
        try {
            val neverBlock = config.getJSONObject("prefix_rules").getJSONObject("never_block")
            val prefixes = neverBlock.getJSONArray("prefixes")

            for (i in 0 until prefixes.length()) {
                if (number.startsWith(prefixes.getString(i))) {
                    return true
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Erreur vérification urgence", e)
        }

        return false
    }

    /**
     * Vérifie si le numéro est en whitelist
     */
    private fun isWhitelisted(number: String, config: JSONObject): Boolean {
        try {
            if (!config.has("whitelist_exceptions")) return false

            val whitelist = config.getJSONObject("whitelist_exceptions")
            val organizations = whitelist.getJSONArray("trusted_organizations")

            for (i in 0 until organizations.length()) {
                val org = organizations.getJSONObject(i)

                // Vérifier numéros spécifiques
                if (org.has("specific_numbers")) {
                    val numbers = org.getJSONArray("specific_numbers")
                    for (j in 0 until numbers.length()) {
                        val whiteNumber = numbers.getString(j).replace(Regex("[^0-9]"), "")
                        if (number.contains(whiteNumber) || whiteNumber.contains(number)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Erreur vérification whitelist", e)
        }

        return false
    }

    /**
     * Vérifie dans la base de spam connus
     */
    private fun checkKnownSpam(number: String, config: JSONObject): KnownSpamEntry? {
        try {
            if (!config.has("known_spam_numbers")) return null

            val spamNumbers = config.getJSONObject("known_spam_numbers")
            if (!spamNumbers.has("entries")) return null

            val entries = spamNumbers.getJSONArray("entries")

            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val spamNumber = entry.getString("number").replace(Regex("[^0-9]"), "")

                if (number.contains(spamNumber) || spamNumber.contains(number)) {
                    return KnownSpamEntry(
                        number = spamNumber,
                        category = entry.getString("category"),
                        severity = entry.getString("severity")
                    )
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Erreur vérification spam connu", e)
        }

        return null
    }

    data class KnownSpamEntry(
        val number: String,
        val category: String,
        val severity: String
    )

    private fun knownSpamSeverityToRiskLevel(severity: String): RiskLevel {
        return when (severity.lowercase()) {
            "critical" -> RiskLevel.CRITICAL
            "high" -> RiskLevel.HIGH
            "medium" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    /**
     * Vérifie les préfixes always_block
     * MISE À JOUR: Teste à la fois format national et international
     */
    private fun checkAlwaysBlock(nationalNumber: String, internationalNumber: String, config: JSONObject, country: String): BlockDecision? {
        try {
            val prefixRules = config.getJSONObject("prefix_rules")
            val alwaysBlock = prefixRules.getJSONObject("always_block")

            // Pour la France - vérifier les catégories
            if (country == "FR") {
                if (alwaysBlock.has("categories")) {
                    val categories = alwaysBlock.getJSONObject("categories")

                    // Vérifier chaque catégorie
                    val categoryNames = categories.keys()
                    while (categoryNames.hasNext()) {
                        val catName = categoryNames.next()
                        val category = categories.getJSONObject(catName)
                        val prefixes = category.getJSONArray("prefixes")

                        for (i in 0 until prefixes.length()) {
                            val prefix = prefixes.getString(i)
                            // IMPORTANT: Tester les deux formats
                            if (nationalNumber.startsWith(prefix) || internationalNumber.startsWith(prefix) || matchesPattern(internationalNumber, prefix)) {
                                return BlockDecision(
                                    shouldBlock = true,
                                    reason = "Blocked prefix: $prefix ($catName)",
                                    riskLevel = RiskLevel.CRITICAL
                                )
                            }
                        }
                    }
                }
            }

            // Pour la Belgique ou format simple
            if (alwaysBlock.has("prefixes")) {
                val prefixes = alwaysBlock.getJSONArray("prefixes")
                for (i in 0 until prefixes.length()) {
                    val prefix = prefixes.getString(i)
                    // IMPORTANT: Tester les deux formats
                    if (nationalNumber.startsWith(prefix) || internationalNumber.startsWith(prefix) || matchesPattern(internationalNumber, prefix)) {
                        return BlockDecision(
                            shouldBlock = true,
                            reason = "Blocked prefix: $prefix",
                            riskLevel = RiskLevel.CRITICAL
                        )
                    }
                }
            }

            // Vérifier nouveautés 2026 (Belgique)
            if (country == "BE" && config.has("nouveautes_2026")) {
                val nouveautes = config.getJSONObject("nouveautes_2026")

                // Visual spoofing
                if (nouveautes.has("visual_spoofing")) {
                    val spoofing = nouveautes.getJSONObject("visual_spoofing")
                    val patterns = spoofing.getJSONArray("patterns")

                    for (i in 0 until patterns.length()) {
                        val pattern = patterns.getJSONObject(i)
                        val fakePattern = pattern.getString("fake_pattern")

                        if (matchesPattern(nationalNumber, fakePattern) || matchesPattern(internationalNumber, fakePattern)) {
                            return BlockDecision(
                                shouldBlock = true,
                                reason = "Visual spoofing: ${pattern.getString("imite")}",
                                riskLevel = RiskLevel.CRITICAL
                            )
                        }
                    }
                }

                // Séries de harcèlement
                if (nouveautes.has("harassment_series")) {
                    val harassment = nouveautes.getJSONObject("harassment_series")
                    val patterns = harassment.getJSONArray("patterns")

                    for (i in 0 until patterns.length()) {
                        val pattern = patterns.getJSONObject(i)
                        val patternStr = pattern.getString("pattern")

                        if (matchesPattern(nationalNumber, patternStr) || matchesPattern(internationalNumber, patternStr)) {
                            return BlockDecision(
                                shouldBlock = true,
                                reason = "Harassment series: $patternStr",
                                riskLevel = RiskLevel.HIGH
                            )
                        }
                    }
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "Erreur vérification always_block", e)
        }

        return null
    }

    /**
     * Calcule un score de risque basé sur plusieurs algorithmes
     */
    private fun calculateRiskScore(number: String, config: JSONObject, country: String): Int {
        var score = 0

        try {
            if (!config.has("detection_algorithms")) return 0

            val algorithms = config.getJSONObject("detection_algorithms")

            // 1. Détection de spoofing
            if (isSpoofed(number, algorithms, country)) {
                score += 50
            }

            // 2. Analyse temporelle
            if (isHighRiskHour()) {
                score += 15
            }

            if (isWeekend()) {
                score += 10
            }

            // 3. Pattern ping call (nécessiterait historique d'appels)
            // Pour l'instant, on vérifie juste le préfixe
            if (isPingCallPrefix(number, country)) {
                score += 40
            }

        } catch (e: JSONException) {
            Log.e(TAG, "Erreur calcul score risque", e)
        }

        return score
    }

    /**
     * Détecte le spoofing
     */
    private fun isSpoofed(number: String, algorithms: JSONObject, country: String): Boolean {
        // Pour la Belgique - détecter 002 au lieu de 02
        if (country == "BE" && number.startsWith("002")) {
            return true
        }

        // Pour la France - détecter 06/07 depuis l'étranger (nécessiterait info origine)
        // À implémenter avec les métadonnées de l'appel

        return false
    }

    /**
     * Vérifie si on est dans une heure à risque
     */
    private fun isHighRiskHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 8 || hour >= 20
    }

    /**
     * Vérifie si c'est le weekend
     */
    private fun isWeekend(): Boolean {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    /**
     * Vérifie si c'est un préfixe typique de ping call
     */
    private fun isPingCallPrefix(number: String, country: String): Boolean {
        val pingPrefixes = if (country == "FR") {
            listOf("089", "081", "082")
        } else {
            listOf("070", "090", "002")
        }

        return pingPrefixes.any { number.startsWith(it) }
    }

    /**
     * Compte les correspondances de mots-clés
     */
    private fun countKeywordMatches(message: String, keywords: JSONArray): Int {
        var count = 0
        val messageLower = message.lowercase()

        for (i in 0 until keywords.length()) {
            val keyword = keywords.getString(i).lowercase()
            if (messageLower.contains(keyword)) {
                count++
            }
        }

        return count
    }

    /**
     * Vérifie si le message contient une URL suspecte
     */
    private fun containsSuspiciousUrl(message: String, contentAnalysis: JSONObject): Boolean {
        try {
            val urlDetection = contentAnalysis.getJSONObject("url_detection")

            // Vérifier domaines suspects
            if (urlDetection.has("suspicious_domains")) {
                val domains = urlDetection.getJSONArray("suspicious_domains")
                for (i in 0 until domains.length()) {
                    if (message.contains(domains.getString(i))) {
                        return true
                    }
                }
            }

            // Vérifier URLs raccourcies
            if (urlDetection.has("shortened_urls")) {
                val shortened = urlDetection.getJSONArray("shortened_urls")
                for (i in 0 until shortened.length()) {
                    if (message.contains(shortened.getString(i))) {
                        return true
                    }
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "Erreur détection URL", e)
        }

        return false
    }

    /**
     * NOUVEAU: Vérifie si un numéro correspond à un pattern avec wildcards (#)
     * Gère aussi les patterns avec + au début
     */
    private fun matchesPattern(number: String, pattern: String): Boolean {
        // Enlever le + du pattern et du numéro pour comparaison
        val cleanPattern = pattern.replace("+", "")
        val cleanNumber = number.replace("+", "")

        if (cleanPattern.contains("#")) {
            val regex = cleanPattern.replace("#", "\\d")
            return cleanNumber.matches(Regex(regex))
        }
        return cleanNumber.startsWith(cleanPattern)
    }
}