package fr.bonobo.stopdemarchage.filter

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.json.JSONArray
import java.io.InputStreamReader

data class PrefixConfig(
    val version: String,
    val country: String,
    @SerializedName("last_updated") val lastUpdated: String,
    val description: String,
    @SerializedName("blocked_prefixes") val blockedPrefixes: Map<String, PrefixCategory>? = null,
    @SerializedName("trusted_prefixes") val trustedPrefixes: TrustedPrefixes? = null,
    @SerializedName("operator_prefixes") val operatorPrefixes: Map<String, List<String>>? = null,
    @SerializedName("validation_rules") val validationRules: ValidationRules? = null,
    // NOUVEAU: Support pour le format 2026
    @SerializedName("prefix_rules") val prefixRules: Map<String, Any>? = null,
    @SerializedName("nouveautes_2026") val nouveautes2026: Map<String, Any>? = null
)

data class PrefixCategory(
    val description: String,
    val prefixes: List<String>,
    @SerializedName("block_by_default") val blockByDefault: Boolean,
    @SerializedName("never_block") val neverBlock: Boolean? = false,
    val note: String? = null
)

data class TrustedPrefixes(
    val description: String,
    val prefixes: List<String>
)

data class ValidationRules(
    @SerializedName("belgian_mobile_length") val belgianMobileLength: Int? = 10,
    @SerializedName("belgian_fixed_length") val belgianFixedLength: Int? = 9,
    @SerializedName("international_prefix") val internationalPrefix: List<String>? = null,
    @SerializedName("national_prefix") val nationalPrefix: String? = null,
    // NOUVEAU: Support format 2026
    @SerializedName("number_format") val numberFormat: Map<String, String>? = null
)

class PrefixFilterManager(private val context: Context) {

    private val TAG = "PrefixFilterManager"
    private val gson = Gson()
    private var cachedConfig: PrefixConfig? = null

    // NOUVEAU: Référence au détecteur avancé (optionnel)
    private var advancedDetector: AdvancedSpamDetector? = null
    private var useAdvancedDetection = true

    init {
        // Initialiser le détecteur avancé si disponible
        try {
            advancedDetector = AdvancedSpamDetector(context)
            Log.d(TAG, "Détecteur avancé initialisé dans PrefixFilterManager")
        } catch (e: Exception) {
            Log.w(TAG, "Détecteur avancé non disponible, mode classique uniquement", e)
            useAdvancedDetection = false
        }
    }

    /**
     * Charge la configuration des préfixes pour le pays donné
     * Support à la fois ancien format et nouveau format 2026
     */
    fun loadPrefixConfig(country: String): PrefixConfig {
        if (cachedConfig?.country == country) {
            return cachedConfig!!
        }

        val fileName = when (country) {
            "BE" -> "prefixes_blocked_be.json"
            "FR" -> "prefixes_blocked_fr.json"
            else -> "prefixes_blocked_fr.json"
        }

        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val jsonText = reader.readText()

            // Vérifier le format du fichier
            if (jsonText.trimStart().startsWith("[")) {
                // Ancien format - Array simple
                Log.d(TAG, "Ancien format JSON détecté pour $country")
                createLegacyConfig(jsonText, country)
            } else {
                // Nouveau format - Object complexe
                Log.d(TAG, "Nouveau format JSON 2026 détecté pour $country")
                val config = gson.fromJson(jsonText, PrefixConfig::class.java)
                cachedConfig = config
                config
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement config pour $country", e)
            // Configuration par défaut en cas d'erreur
            PrefixConfig(
                version = "1.0",
                country = country,
                lastUpdated = "",
                description = "Configuration par défaut",
                blockedPrefixes = emptyMap(),
                trustedPrefixes = TrustedPrefixes("", emptyList())
            )
        }
    }

    /**
     * NOUVEAU: Crée une config depuis l'ancien format array
     */
    private fun createLegacyConfig(jsonText: String, country: String): PrefixConfig {
        val prefixes = mutableListOf<String>()
        val jsonArray = JSONArray(jsonText)

        for (i in 0 until jsonArray.length()) {
            prefixes.add(jsonArray.getString(i))
        }

        val config = PrefixConfig(
            version = "1.0-legacy",
            country = country,
            lastUpdated = "",
            description = "Configuration legacy",
            blockedPrefixes = mapOf(
                "legacy" to PrefixCategory(
                    description = "Préfixes legacy",
                    prefixes = prefixes,
                    blockByDefault = true
                )
            ),
            trustedPrefixes = TrustedPrefixes("", emptyList())
        )

        cachedConfig = config
        return config
    }

    /**
     * Normalise un numéro de téléphone
     * Support France et Belgique
     */
    fun normalizePhoneNumber(phoneNumber: String): String {
        var normalized = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Convertir format international en format national
        when {
            // Belgique
            normalized.startsWith("+32") -> {
                normalized = "0" + normalized.substring(3)
            }
            normalized.startsWith("0032") -> {
                normalized = "0" + normalized.substring(4)
            }
            // France
            normalized.startsWith("+33") -> {
                normalized = "0" + normalized.substring(3)
            }
            normalized.startsWith("0033") -> {
                normalized = "0" + normalized.substring(4)
            }
        }

        return normalized
    }

    /**
     * Vérifie si un numéro doit être bloqué par préfixe
     * MISE À JOUR: Utilise le détecteur avancé 2026 si disponible
     */
    fun shouldBlockByPrefix(phoneNumber: String, country: String = "BE"): BlockResult {
        val normalized = normalizePhoneNumber(phoneNumber)

        // === MODE AVANCÉ 2026 (si activé) ===
        if (useAdvancedDetection && advancedDetector != null) {
            try {
                val decision = advancedDetector!!.shouldBlockNumber(normalized, country)

                return BlockResult(
                    shouldBlock = decision.shouldBlock,
                    reason = decision.reason,
                    category = decision.riskLevel.name,
                    matchedPrefix = null,
                    riskScore = decision.riskScore,
                    riskLevel = decision.riskLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur détecteur avancé, fallback mode classique", e)
                // Continue avec mode classique ci-dessous
            }
        }

        // === MODE CLASSIQUE (fallback ou si avancé désactivé) ===
        val config = loadPrefixConfig(country)

        // Vérifier d'abord les préfixes de confiance (ne jamais bloquer)
        if (config.trustedPrefixes != null && isTrustedPrefix(normalized, config)) {
            return BlockResult(
                shouldBlock = false,
                reason = "Numéro de confiance",
                category = "trusted"
            )
        }

        // Vérifier les préfixes d'urgence (nouveau format 2026)
        if (config.prefixRules != null) {
            val neverBlockResult = checkNeverBlockPrefixes(normalized, config)
            if (neverBlockResult != null) return neverBlockResult

            // Vérifier always_block (nouveau format 2026)
            val alwaysBlockResult = checkAlwaysBlockPrefixes(normalized, config, country)
            if (alwaysBlockResult != null) return alwaysBlockResult
        }

        // Vérifier les préfixes d'urgence (ancien format)
        val emergencyCategory = config.blockedPrefixes?.get("emergency")
        if (emergencyCategory?.neverBlock == true) {
            if (matchesAnyPrefix(normalized, emergencyCategory.prefixes)) {
                return BlockResult(
                    shouldBlock = false,
                    reason = "Numéro d'urgence",
                    category = "emergency"
                )
            }
        }

        // Vérifier chaque catégorie de préfixes bloqués (ancien format)
        config.blockedPrefixes?.forEach { (categoryName, category) ->
            if (category.blockByDefault && matchesAnyPrefix(normalized, category.prefixes)) {
                return BlockResult(
                    shouldBlock = true,
                    reason = category.description,
                    category = categoryName,
                    matchedPrefix = findMatchingPrefix(normalized, category.prefixes)
                )
            }
        }

        return BlockResult(
            shouldBlock = false,
            reason = "Aucune règle de blocage",
            category = "none"
        )
    }

    /**
     * NOUVEAU: Vérifie les préfixes never_block (format 2026)
     */
    private fun checkNeverBlockPrefixes(phoneNumber: String, config: PrefixConfig): BlockResult? {
        try {
            val prefixRules = config.prefixRules ?: return null
            val neverBlock = prefixRules["never_block"] as? Map<*, *> ?: return null

            @Suppress("UNCHECKED_CAST")
            val prefixes = neverBlock["prefixes"] as? List<String> ?: return null

            if (prefixes.any { phoneNumber.startsWith(it) }) {
                return BlockResult(
                    shouldBlock = false,
                    reason = "Numéro d'urgence",
                    category = "never_block",
                    riskLevel = "NONE"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur vérification never_block", e)
        }

        return null
    }

    /**
     * NOUVEAU: Vérifie les préfixes always_block (format 2026)
     */
    private fun checkAlwaysBlockPrefixes(phoneNumber: String, config: PrefixConfig, country: String): BlockResult? {
        try {
            val prefixRules = config.prefixRules ?: return null
            val alwaysBlock = prefixRules["always_block"] as? Map<*, *> ?: return null

            // Pour la Belgique - vérifier préfixes simples
            @Suppress("UNCHECKED_CAST")
            val prefixes = alwaysBlock["prefixes"] as? List<String>
            if (prefixes != null && prefixes.any { phoneNumber.startsWith(it) }) {
                return BlockResult(
                    shouldBlock = true,
                    reason = "Préfixe surtaxé",
                    category = "always_block",
                    riskLevel = "CRITICAL"
                )
            }

            // Pour la France - vérifier catégories
            @Suppress("UNCHECKED_CAST")
            val categories = alwaysBlock["categories"] as? Map<String, Map<*, *>>
            if (categories != null) {
                categories.forEach { (catName, category) ->
                    @Suppress("UNCHECKED_CAST")
                    val catPrefixes = category["prefixes"] as? List<String>
                    if (catPrefixes != null && catPrefixes.any { phoneNumber.startsWith(it) }) {
                        return BlockResult(
                            shouldBlock = true,
                            reason = catName.replace("_", " "),
                            category = catName,
                            riskLevel = "CRITICAL"
                        )
                    }
                }
            }

            // NOUVEAU 2026: Visual spoofing Belgique
            if (country == "BE" && config.nouveautes2026 != null) {
                val nouveautes = config.nouveautes2026
                @Suppress("UNCHECKED_CAST")
                val visualSpoofing = nouveautes["visual_spoofing"] as? Map<*, *>

                if (visualSpoofing != null) {
                    @Suppress("UNCHECKED_CAST")
                    val patterns = visualSpoofing["patterns"] as? List<Map<*, *>>

                    patterns?.forEach { pattern ->
                        val fakePattern = pattern["fake_pattern"] as? String
                        if (fakePattern != null && matchesPattern(phoneNumber, fakePattern)) {
                            return BlockResult(
                                shouldBlock = true,
                                reason = "Visual spoofing détecté: ${pattern["imite"]}",
                                category = "visual_spoofing",
                                riskLevel = "CRITICAL"
                            )
                        }
                    }
                }

                // Séries de harcèlement
                @Suppress("UNCHECKED_CAST")
                val harassment = nouveautes["harassment_series"] as? Map<*, *>
                if (harassment != null) {
                    @Suppress("UNCHECKED_CAST")
                    val patterns = harassment["patterns"] as? List<Map<*, *>>

                    patterns?.forEach { pattern ->
                        val patternStr = pattern["pattern"] as? String
                        if (patternStr != null && matchesPattern(phoneNumber, patternStr)) {
                            return BlockResult(
                                shouldBlock = true,
                                reason = "Série harcèlement: $patternStr",
                                category = "harassment",
                                riskLevel = "HIGH"
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Erreur vérification always_block", e)
        }

        return null
    }

    /**
     * NOUVEAU: Vérifie si un numéro correspond à un pattern avec wildcards (#)
     */
    private fun matchesPattern(number: String, pattern: String): Boolean {
        if (pattern.contains("#")) {
            val regex = pattern.replace("#", "\\d")
            return number.matches(Regex(regex))
        }
        return number.startsWith(pattern)
    }

    /**
     * Vérifie si un numéro est dans les préfixes de confiance
     */
    private fun isTrustedPrefix(phoneNumber: String, config: PrefixConfig): Boolean {
        return config.trustedPrefixes?.let {
            matchesAnyPrefix(phoneNumber, it.prefixes)
        } ?: false
    }

    /**
     * Vérifie si un numéro correspond à l'un des préfixes donnés
     */
    private fun matchesAnyPrefix(phoneNumber: String, prefixes: List<String>): Boolean {
        return prefixes.any { prefix -> phoneNumber.startsWith(prefix) }
    }

    /**
     * Trouve le préfixe correspondant
     */
    private fun findMatchingPrefix(phoneNumber: String, prefixes: List<String>): String? {
        return prefixes.find { prefix -> phoneNumber.startsWith(prefix) }
    }

    /**
     * Obtient la catégorie d'un numéro
     */
    fun getNumberCategory(phoneNumber: String, country: String = "BE"): String? {
        val config = loadPrefixConfig(country)
        val normalized = normalizePhoneNumber(phoneNumber)

        config.blockedPrefixes?.forEach { (categoryName, category) ->
            if (matchesAnyPrefix(normalized, category.prefixes)) {
                return categoryName
            }
        }

        return null
    }

    /**
     * Obtient l'opérateur d'un numéro mobile
     */
    fun getOperator(phoneNumber: String, country: String = "BE"): String? {
        val config = loadPrefixConfig(country)
        val normalized = normalizePhoneNumber(phoneNumber)

        config.operatorPrefixes?.forEach { (operator, prefixes) ->
            if (matchesAnyPrefix(normalized, prefixes)) {
                return operator
            }
        }

        return null
    }

    /**
     * Valide le format d'un numéro
     */
    fun isValidBelgianNumber(phoneNumber: String): Boolean {
        val config = loadPrefixConfig("BE")
        val normalized = normalizePhoneNumber(phoneNumber)

        val rules = config.validationRules ?: return true

        // Vérifier si c'est un mobile (commence par 04)
        if (normalized.startsWith("04")) {
            return normalized.length == (rules.belgianMobileLength ?: 10)
        }

        // Vérifier si c'est une ligne fixe
        if (normalized.startsWith("0") && normalized[1] in '2'..'9') {
            return normalized.length == (rules.belgianFixedLength ?: 9) ||
                    normalized.length == (rules.belgianMobileLength ?: 10)
        }

        return true // Laisser passer les formats spéciaux
    }

    /**
     * Obtient des informations détaillées sur un numéro
     */
    fun getNumberInfo(phoneNumber: String, country: String = "BE"): NumberInfo {
        val normalized = normalizePhoneNumber(phoneNumber)
        val category = getNumberCategory(normalized, country)
        val operator = getOperator(normalized, country)
        val blockResult = shouldBlockByPrefix(normalized, country)
        val isValid = if (country == "BE") isValidBelgianNumber(normalized) else true

        return NumberInfo(
            originalNumber = phoneNumber,
            normalizedNumber = normalized,
            category = category,
            operator = operator,
            shouldBlock = blockResult.shouldBlock,
            blockReason = blockResult.reason,
            isValid = isValid,
            riskScore = blockResult.riskScore,
            riskLevel = blockResult.riskLevel
        )
    }

    /**
     * NOUVEAU: Active/désactive le mode avancé
     */
    fun setAdvancedDetectionMode(enabled: Boolean) {
        useAdvancedDetection = enabled
        Log.d(TAG, "Mode détection avancée des préfixes: $enabled")
    }

    /**
     * NOUVEAU: Vérifie si le mode avancé est actif
     */
    fun isAdvancedModeEnabled(): Boolean {
        return useAdvancedDetection && advancedDetector != null
    }
}

data class BlockResult(
    val shouldBlock: Boolean,
    val reason: String,
    val category: String,
    val matchedPrefix: String? = null,
    val riskScore: Int = 0,
    val riskLevel: String = "NONE"
)

data class NumberInfo(
    val originalNumber: String,
    val normalizedNumber: String,
    val category: String?,
    val operator: String?,
    val shouldBlock: Boolean,
    val blockReason: String,
    val isValid: Boolean,
    val riskScore: Int = 0,
    val riskLevel: String = "NONE"
)