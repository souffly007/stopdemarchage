package fr.bonobo.stopdemarchage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import fr.bonobo.stopdemarchage.filter.SpamFilterManager
import fr.bonobo.stopdemarchage.utils.CountryDetector

class SettingsFragment : Fragment() {

    private lateinit var filterManager: SpamFilterManager
    private lateinit var countryDetector: CountryDetector

    private var switchAutoDetect: Switch? = null
    private var switchAdvancedDetection: Switch? = null
    private var spinnerCountry: Spinner? = null
    private var textDetectionMode: TextView? = null
    private var textDetectionStats: TextView? = null
    private var layoutDetectionStats: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        filterManager = SpamFilterManager(requireContext())
        countryDetector = CountryDetector(requireContext())
        filterManager.initialize()

        setupCardClicks(view)
        setupAdvancedDetectionToggle(view)
        setupCountrySettings(view)

        updateDetectionStats()
    }

    // ============================================
    // Configuration du mode de détection avancée
    // ============================================
    private fun setupAdvancedDetectionToggle(view: View) {
        switchAdvancedDetection = view.findViewById(R.id.switch_advanced_detection)
        textDetectionMode = view.findViewById(R.id.text_detection_mode)
        textDetectionStats = view.findViewById(R.id.text_detection_stats)
        layoutDetectionStats = view.findViewById(R.id.layout_detection_stats)

        if (switchAdvancedDetection == null) return

        val sharedPrefs = requireContext().getSharedPreferences(
            "StopDemarchagePrefs", android.content.Context.MODE_PRIVATE
        )
        val isAdvancedEnabled = sharedPrefs.getBoolean("use_advanced_detection", true)

        switchAdvancedDetection?.isChecked = isAdvancedEnabled

        switchAdvancedDetection?.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().apply {
                putBoolean("use_advanced_detection", isChecked)
                putBoolean("use_advanced_sms_detection", isChecked)
                apply()
            }

            try {
                filterManager.setAdvancedDetectionMode(isChecked)
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erreur changement mode", e)
            }

            val message = if (isChecked) {
                "✅ Mode avancé 2026 activé\nProtection renforcée active"
            } else {
                "Mode classique activé\nDétection par préfixes et mots-clés"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

            updateDetectionStats()
        }

        updateDetectionStats()
    }

    // ============================================
    // Mise à jour des statistiques de détection
    // ============================================
    private fun updateDetectionStats() {
        if (textDetectionMode == null || textDetectionStats == null) return

        try {
            val stats = filterManager.getFilterStats()
            val mode = filterManager.getDetectionMode()

            val modeText = when (mode) {
                "advanced_2026" -> "📊 Mode actif : 🚀 Avancé 2026"
                else -> "📊 Mode actif : Classique"
            }
            textDetectionMode?.text = modeText

            val statsText = buildString {
                append("Pays configuré : ${getCountryName(stats.country)}\n")
                append("Mots-clés surveillés : ${stats.keywordCount}\n")
                append("Patterns détection : ${stats.patternCount}\n")

                if (stats.advancedModeEnabled) {
                    append("\n✅ Protections actives :\n")
                    append("  • Anti-spoofing\n")
                    append("  • Visual spoofing (BE)\n")
                    append("  • Analyse temporelle\n")
                    append("  • Score de risque 0-100")
                } else {
                    append("\nℹ️ Mode classique actif")
                }
            }
            textDetectionStats?.text = statsText

            layoutDetectionStats?.alpha = 0f
            layoutDetectionStats?.animate()?.alpha(1f)?.setDuration(300)?.start()

        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Erreur affichage stats", e)
            textDetectionStats?.text = "Statistiques non disponibles"
        }
    }

    // ============================================
    // Configuration pays
    // ============================================
    private fun setupCountrySettings(view: View) {
        switchAutoDetect = view.findViewById(R.id.switch_auto_detect_country)
        spinnerCountry = view.findViewById(R.id.spinner_country_selection)

        if (switchAutoDetect == null || spinnerCountry == null) return

        switchAutoDetect?.apply {
            isChecked = filterManager.isAutoDetectEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    val detectedCountry = countryDetector.detectCountry()
                    filterManager.setCountry(detectedCountry, autoDetect = true)
                    Toast.makeText(
                        requireContext(),
                        "Pays détecté: ${getCountryName(detectedCountry)}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    filterManager.setCountry(
                        filterManager.getCurrentCountry(), autoDetect = false
                    )
                    Toast.makeText(requireContext(), "Mode manuel activé", Toast.LENGTH_SHORT).show()
                }
                updateCountryUI()
                updateDetectionStats()
            }
        }

        spinnerCountry?.apply {
            val countries = arrayOf("🇫🇷 France", "🇧🇪 Belgique")
            val adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, countries
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    if (filterManager.isAutoDetectEnabled()) return

                    val country = when (position) {
                        0 -> CountryDetector.COUNTRY_FRANCE
                        1 -> CountryDetector.COUNTRY_BELGIUM
                        else -> CountryDetector.COUNTRY_FRANCE
                    }

                    filterManager.setCountry(country, autoDetect = false)
                    updateCountryUI()
                    updateDetectionStats()

                    Toast.makeText(
                        requireContext(),
                        "Filtrage configuré pour ${getCountryName(country)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        updateCountryUI()
    }

    private fun updateCountryUI() {
        val currentCountry = filterManager.getCurrentCountry()
        val isAutoDetect = filterManager.isAutoDetectEnabled()

        switchAutoDetect?.isChecked = isAutoDetect

        spinnerCountry?.apply {
            isEnabled = !isAutoDetect
            alpha = if (isAutoDetect) 0.5f else 1.0f
            setSelection(
                when (currentCountry) {
                    CountryDetector.COUNTRY_FRANCE -> 0
                    CountryDetector.COUNTRY_BELGIUM -> 1
                    else -> 0
                }
            )
        }
    }

    private fun getCountryName(countryCode: String): String {
        return when (countryCode) {
            CountryDetector.COUNTRY_FRANCE -> "France 🇫🇷"
            CountryDetector.COUNTRY_BELGIUM -> "Belgique 🇧🇪"
            else -> "Inconnu"
        }
    }

    override fun onResume() {
        super.onResume()
        updateCountryUI()
        updateDetectionStats()
    }

    // ============================================
    // ✅ Clics sur les cartes de réglages
    // ============================================
    private fun setupCardClicks(view: View) {
        // Paramètres Android
        view.findViewById<View>(R.id.cardAppSettings)?.setOnClickListener {
            openAndroidSettings()
        }

        // À propos
        view.findViewById<View>(R.id.cardAbout)?.setOnClickListener {
            showAboutDialog()
        }

        // Aide et Conseils
        view.findViewById<View>(R.id.cardHelp)?.setOnClickListener {
            (activity as? MainActivity)?.showProtectionTips()
        }
    }

    // ============================================
    // ✅ Menu paramètres Android avec 5 options
    // ============================================
    private fun openAndroidSettings() {
        try {
            val options = arrayOf(
                "📱 Paramètres de l'application",
                "🔔 Paramètres de notifications",
                "🛡️ Application par défaut (ID appelant & spam)",
                "⚙️ Paramètres généraux Android"
            )

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Ouvrir les paramètres")
                .setItems(options) { _, which ->
                    val intent = when (which) {
                        0 -> android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        ).apply {
                            data = android.net.Uri.parse(
                                "package:${requireContext().packageName}"
                            )
                        }
                        1 -> {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                ).apply {
                                    putExtra(
                                        android.provider.Settings.EXTRA_APP_PACKAGE,
                                        requireContext().packageName
                                    )
                                }
                            } else {
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = android.net.Uri.parse(
                                        "package:${requireContext().packageName}"
                                    )
                                }
                            }
                        }
                        2 -> {
                            openDefaultCallerIdSettings()
                            return@setItems
                        }
                        3 -> android.content.Intent(
                            android.provider.Settings.ACTION_SETTINGS
                        )
                        else -> android.content.Intent(
                            android.provider.Settings.ACTION_SETTINGS
                        )
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Annuler", null)
                .show()

        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Erreur ouverture paramètres", e)
            try {
                startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                )
            } catch (e2: Exception) {
                Toast.makeText(
                    requireContext(),
                    "❌ Impossible d'ouvrir les paramètres",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ============================================
    // ✅ NOUVEAU — Ouvrir paramètres ID appelant & spam
    // ============================================
    private fun openDefaultCallerIdSettings() {
        val context = requireContext()

        // Méthode 1 : Android 12+ — Ouvrir directement les apps par défaut
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                )
                startActivity(intent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.d("SettingsFragment", "ACTION_MANAGE_DEFAULT non disponible", e)
        }

        // Méthode 2 : Android 7+ — Ouvrir "Applications par défaut"
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val intent = android.content.Intent(
                    "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"
                )
                startActivity(intent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.d("SettingsFragment", "MANAGE_DEFAULT_APPS non disponible", e)
        }

        // Méthode 3 : Fallback — Afficher les instructions
        try {
            showCallerIdInstructions()
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Erreur ouverture paramètres défaut", e)
            Toast.makeText(
                context,
                "❌ Impossible d'ouvrir les paramètres",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================
    // ✅ NOUVEAU — Instructions pour configurer l'app par défaut
    // ============================================
    private fun showCallerIdInstructions() {
        val message = """
            📋 Pour définir Stop Démarchage comme application par défaut :
            
            1️⃣ Ouvrez : Paramètres
            2️⃣ Allez dans : Applications
            3️⃣ Appuyez sur : Applications par défaut
            4️⃣ Sélectionnez : ID de l'appelant et spam
            5️⃣ Choisissez : Stop Démarchage
            
            🔄 Voulez-vous ouvrir les paramètres maintenant ?
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("🛡️ Application par défaut")
            .setMessage(message)
            .setPositiveButton("Ouvrir les paramètres") { _, _ ->
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = android.net.Uri.parse(
                            "package:${requireContext().packageName}"
                        )
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    )
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ============================================
    // Dialog À propos
    // ============================================
    private fun showAboutDialog() {
        val mode = filterManager.getDetectionMode()
        val modeText = if (mode == "advanced_2026") "Avancé 2026 🚀" else "Classique"

        val message = """
            📱 Stop Démarchage
            
            Version: 2.1.0
            Mode de détection: $modeText
            
            ${if (mode == "advanced_2026") {
            """
                ✅ Fonctionnalités avancées actives :
                • Anti-spoofing mobile (FR)
                • Visual spoofing (BE: 002)
                • Séries harcèlement (BE: 071960###)
                • Analyse temporelle
                • Score de risque 0-100
                • Analyse contenu SMS
                """.trimIndent()
        } else {
            "Détection par préfixes et mots-clés"
        }}
            
            Développé avec ❤️ pour votre tranquillité
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("À propos")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Plus d'infos") { _, _ ->
                showDetectionDetailsDialog()
            }
            .show()
    }

    // ============================================
    // Dialog détails techniques
    // ============================================
    private fun showDetectionDetailsDialog() {
        val stats = filterManager.getFilterStats()

        val details = """
            🔍 Détails techniques
            
            Pays: ${getCountryName(stats.country)}
            Mode: ${if (stats.advancedModeEnabled) "Avancé 2026" else "Classique"}
            
            📊 Données de filtrage :
            • Mots-clés surveillés: ${stats.keywordCount}
            • Patterns regex: ${stats.patternCount}
            • Expéditeurs de confiance: ${stats.trustedSenderCount}
            • Dernière mise à jour: ${stats.lastUpdated}
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Détails techniques")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}