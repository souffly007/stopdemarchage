package fr.bonobo.stopdemarchage

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import fr.bonobo.stopdemarchage.databinding.ActivityMainBinding
import fr.bonobo.stopdemarchage.filter.AdvancedSpamDetector
import fr.bonobo.stopdemarchage.filter.PrefixFilterManager
import fr.bonobo.stopdemarchage.filter.SpamFilterManager
import fr.bonobo.stopdemarchage.fragments.ListsFragment

class MainActivity : AppCompatActivity() {
    private val PREFS_NAME = "StopDemarchagePrefs"
    private val KEY_BLOCK_PRIVATE_NUMBERS = "block_private_numbers"
    private val TAG = "MainActivity"
    private val NOTIFICATION_CHANNEL_ID = "blocked_calls_channel"

    // Couleurs du switch
    private val COLOR_GREEN = "#4CAF50"   // Activé
    private val COLOR_RED = "#F44336"     // Désactivé
    private val COLOR_WHITE = "#FFFFFF"   // Thumb activé
    private val COLOR_GREY = "#BDBDBD"    // Thumb désactivé

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var smsAnalyzer: SmsSuspicionAnalyzer

    private lateinit var spamFilterManager: SpamFilterManager
    private lateinit var prefixFilterManager: PrefixFilterManager
    private var advancedDetector: AdvancedSpamDetector? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "✅ Permissions accordées", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initializeDetectionSystems()
        createNotificationChannels()
        checkAndRequestPermissions()

        setupMainToggle()
        setupTabsAndViewPager()
        startEntranceAnimation()
        checkAndShowDefaultAppWarning()
        handleIntent(intent)
    }

    private fun initializeDetectionSystems() {
        val countryDetector = fr.bonobo.stopdemarchage.utils.CountryDetector(this)
        val detectedCountry = countryDetector.detectCountry()
        smsAnalyzer = SmsSuspicionAnalyzer(this, detectedCountry)

        try {
            spamFilterManager = SpamFilterManager(this)
            spamFilterManager.initialize()
            prefixFilterManager = PrefixFilterManager(this)
            advancedDetector = AdvancedSpamDetector(this)

            if (sharedPrefs.getBoolean("use_advanced_detection", true)) {
                val testNumber = "+33568262347"
                val result = advancedDetector?.shouldBlockNumber(testNumber)
                Log.d("TEST_BLOCAGE", "--- VERIFICATION SYSTEME 2026 ---")
                Log.d("TEST_BLOCAGE", "Numéro: $testNumber -> Result: ${if (result?.shouldBlock == true) "BLOQUÉ 🛑" else "PASSÉ ✅"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur init système avancé", e)
        }
    }

    // --- FONCTION REQUISE PAR SETTINGSFRAGMENT ---
    fun showProtectionTips() {
        val isAdvancedMode = sharedPrefs.getBoolean("use_advanced_detection", true)
        val tips = """
            🛡️ Conseils de Protection 2026
            
            • Ne cliquez jamais sur les liens SMS suspects.
            • Signalez les numéros au 33700.
            • Les préfixes ARCEP (0162, 0568...) sont bloqués automatiquement.
            
            🚀 Mode Avancé : ${if (isAdvancedMode) "ACTIF" else "INACTIF"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🛡️ Aide à la protection")
            .setMessage(tips)
            .setPositiveButton("Compris", null)
            .show()
    }

    // --- AUTRES FONCTIONS DE NAVIGATION ET UI ---
    fun showDetailedStats() {
        val blockedCount = sharedPrefs.getInt("blocked_calls_count", 0)
        AlertDialog.Builder(this)
            .setTitle("📊 Statistiques")
            .setMessage("Total appels bloqués : $blockedCount")
            .setPositiveButton("OK", null)
            .show()
    }

    // ============================================
    // ✅ CORRIGÉ — Switch avec couleurs dynamiques
    // ============================================
    private fun setupMainToggle() {
        val switch = binding.switchBlockPrivateNumbers

        // Charger l'état sauvegardé
        switch.isChecked = sharedPrefs.getBoolean(KEY_BLOCK_PRIVATE_NUMBERS, false)

        // Appliquer la couleur initiale
        updateSwitchColor(switch, switch.isChecked)

        // Écouter les changements
        switch.setOnCheckedChangeListener { _, isChecked ->
            // Sauvegarder la préférence
            sharedPrefs.edit().putBoolean(KEY_BLOCK_PRIVATE_NUMBERS, isChecked).apply()

            // Mettre à jour les couleurs
            updateSwitchColor(switch, isChecked)

            // Afficher un message à l'utilisateur
            val message = if (isChecked) {
                "🛡️ Blocage des numéros masqués ACTIVÉ"
            } else {
                "⚠️ Blocage des numéros masqués DÉSACTIVÉ"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // ✅ NOUVEAU — Mise à jour des couleurs du switch
    // ============================================
    private fun updateSwitchColor(switch: SwitchMaterial, isChecked: Boolean) {
        if (isChecked) {
            // ACTIVÉ → Vert + Blanc
            switch.trackTintList = ColorStateList.valueOf(Color.parseColor(COLOR_GREEN))
            switch.thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_WHITE))
        } else {
            // DÉSACTIVÉ → Rouge + Gris
            switch.trackTintList = ColorStateList.valueOf(Color.parseColor(COLOR_RED))
            switch.thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_GREY))
        }
    }

    private fun setupTabsAndViewPager() {
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "📋 Listes" else "⚙️ Réglages"
        }.attach()
    }

    private class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return if (position == 0) ListsFragment.newInstance() else SettingsFragment.newInstance()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Appels bloqués",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    // --- FONCTIONS BOILERPLATE RESTANTES ---
    private fun checkAndShowDefaultAppWarning() { /* ... */ }

    fun openCallScreeningSettings() { /* ... */ }

    private fun startEntranceAnimation() {
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).duration = 800
    }

    private fun handleIntent(intent: Intent?) { /* ... */ }

    private fun logDetectionStats() { /* ... */ }

    fun testSmsDetection(p: String, m: String) { /* ... */ }
}