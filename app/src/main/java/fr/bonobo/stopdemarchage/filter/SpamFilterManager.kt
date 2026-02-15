package fr.bonobo.stopdemarchage.filter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.bonobo.stopdemarchage.utils.CountryDetector
import java.io.InputStreamReader

data class BlockedWordsConfig(
    val version: String?,
    val country: String?,
    val last_updated: String?,
    val keywords: List<String>?,
    val suspicious_patterns: List<SuspiciousPattern>? = null,
    val trusted_senders: List<String>? = null
)

data class SuspiciousPattern(val pattern: String, val description: String)

data class FilterStats(
    val country: String,
    val keywordCount: Int,
    val patternCount: Int,
    val trustedSenderCount: Int,
    val lastUpdated: String,
    val autoDetectEnabled: Boolean,
    val advancedModeEnabled: Boolean = false
)

data class SpamCheckResult(
    val isSpam: Boolean,
    val reason: String,
    val confidence: Double,
    val blockedByPrefix: Boolean,
    val blockedByKeyword: Boolean,
    val blockedByPattern: Boolean = false,
    val matchedKeywords: List<String> = emptyList(),
    val riskScore: Int = 0,
    val riskLevel: String = "NONE",
    val detectionMode: String = "classic"
)

class SpamFilterManager(private val context: Context) {

    private val TAG = "SpamFilterManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("spam_filter_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val countryDetector = CountryDetector(context)
    private var advancedDetector: AdvancedSpamDetector? = null
    private var useAdvancedDetection = true

    companion object {
        private const val PREF_SELECTED_COUNTRY = "selected_country"
        private const val PREF_AUTO_DETECT = "auto_detect_country"
        private const val PREF_ADVANCED_DETECTION = "advanced_spam_detection"
    }

    init {
        try {
            advancedDetector = AdvancedSpamDetector(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur init AdvancedDetector", e)
        }
        val prefEnabled = prefs.getBoolean(PREF_ADVANCED_DETECTION, true)
        useAdvancedDetection = prefEnabled && advancedDetector != null
    }

    fun initialize() {
        if (!prefs.contains(PREF_SELECTED_COUNTRY)) {
            val detectedCountry = countryDetector.detectCountry()
            setCountry(detectedCountry, autoDetect = true)
        }
    }

    fun setCountry(country: String, autoDetect: Boolean = false) {
        prefs.edit()
            .putString(PREF_SELECTED_COUNTRY, country)
            .putBoolean(PREF_AUTO_DETECT, autoDetect)
            .apply()
    }

    fun isAutoDetectEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_DETECT, true)
    }

    fun getDetectionMode(): String {
        return if (isAdvancedModeEnabled()) "advanced_2026" else "classic"
    }

    fun getCurrentCountry(): String {
        return prefs.getString(PREF_SELECTED_COUNTRY, CountryDetector.DEFAULT_COUNTRY) ?: CountryDetector.DEFAULT_COUNTRY
    }

    fun loadBlockedWords(): BlockedWordsConfig {
        val country = getCurrentCountry()
        val fileName = if (country == CountryDetector.COUNTRY_BELGIUM) "blocked_words_be.json" else "blocked_words_fr.json"
        return try {
            val reader = InputStreamReader(context.assets.open(fileName))
            val type = object : TypeToken<BlockedWordsConfig>() {}.type
            gson.fromJson(reader, type)
        } catch (e: Exception) {
            BlockedWordsConfig("1.0", "UNKNOWN", "", emptyList())
        }
    }

    fun isSpam(message: String, sender: String? = null): SpamCheckResult {
        val country = getCurrentCountry()

        // --- MODE AVANCÉ ---
        if (useAdvancedDetection && advancedDetector != null) {
            // CORRECTION : On utilise "sender ?: """ pour éviter l'erreur de type String? vs String
            val decision = advancedDetector!!.isSmsSpam(sender ?: "", message, country)
            if (decision.shouldBlock) {
                return SpamCheckResult(
                    isSpam = true,
                    reason = decision.reason,
                    confidence = decision.riskScore / 100.0,
                    blockedByPrefix = true,
                    blockedByKeyword = false,
                    riskScore = decision.riskScore,
                    riskLevel = decision.riskLevel.name,
                    detectionMode = "advanced"
                )
            }
        }

        // --- MODE CLASSIQUE ---
        val config = loadBlockedWords()
        val messageLower = message.lowercase()
        val blockedKeywords = config.keywords?.filter { messageLower.contains(it.lowercase()) } ?: emptyList()

        val isSpam = blockedKeywords.isNotEmpty()
        return SpamCheckResult(
            isSpam = isSpam,
            reason = if (isSpam) "Mots-clés suspects" else "RAS",
            confidence = if (isSpam) 0.7 else 0.0,
            blockedByPrefix = false,
            blockedByKeyword = isSpam,
            matchedKeywords = blockedKeywords,
            detectionMode = "classic"
        )
    }

    fun getFilterStats(): FilterStats {
        val config = loadBlockedWords()
        return FilterStats(
            country = config.country ?: getCurrentCountry(),
            keywordCount = config.keywords?.size ?: 0,
            patternCount = config.suspicious_patterns?.size ?: 0,
            trustedSenderCount = config.trusted_senders?.size ?: 0,
            lastUpdated = config.last_updated ?: "",
            autoDetectEnabled = isAutoDetectEnabled(),
            advancedModeEnabled = isAdvancedModeEnabled()
        )
    }

    fun isAdvancedModeEnabled(): Boolean = useAdvancedDetection && advancedDetector != null

    fun setAdvancedDetectionMode(enabled: Boolean) {
        useAdvancedDetection = enabled && advancedDetector != null
        prefs.edit().putBoolean(PREF_ADVANCED_DETECTION, enabled).apply()
    }
}