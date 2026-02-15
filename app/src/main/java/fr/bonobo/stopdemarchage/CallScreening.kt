package fr.bonobo.stopdemarchage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat

class CallScreening : CallScreeningService() {

    private val TAG = "CallScreeningService"

    // ============================================
    // PRÉFIXES À BLOQUER — Basés sur prefixes_blocked_fr.json
    // ============================================

    // 1. SURTAXÉS CRITIQUES (089x)
    private val SURTAXES_CRITIQUES = setOf(
        "+33890", "+33891", "+33892", "+33893", "+33894",
        "+33895", "+33896", "+33897", "+33898", "+33899",
        "0890", "0891", "0892", "0893", "0894",
        "0895", "0896", "0897", "0898", "0899"
    )

    // 2. SURTAXÉS ÉLEVÉS (081x, 082x)
    private val SURTAXES_ELEVES = setOf(
        "+33810", "+33811", "+33812", "+33813", "+33814",
        "+33815", "+33816", "+33817", "+33818", "+33819",
        "+33820", "+33821", "+33822", "+33823", "+33825",
        "+33826", "+33827",
        "0810", "0811", "0812", "0813", "0814",
        "0815", "0816", "0817", "0818", "0819",
        "0820", "0821", "0822", "0823", "0825",
        "0826", "0827"
    )

    // 3. ANCIEN FORMAT SURTAXÉ (080x sauf 0800/0801/0802)
    private val ANCIEN_SURTAXE = setOf(
        "+33803", "+33804", "+33805", "+33806", "+33807",
        "+33808", "+33809",
        "0803", "0804", "0805", "0806", "0807",
        "0808", "0809"
    )

    // 4. PRÉFIXES ARCEP — Démarchage commercial identifiable
    private val ARCEP_DEMARCHAGE = setOf(
        "+33162", "+33163", "+33270", "+33271",
        "+33377", "+33378", "+33424", "+33425",
        "+33568", "+33569", "+33948", "+33949",
        "0162", "0163", "0270", "0271",
        "0377", "0378", "0424", "0425",
        "0568", "0569", "0948", "0949"
    )

    // 5. PRÉFIXES ARCEP — Sous-plages 09
    private val ARCEP_09 = setOf(
        "+339475", "+339476", "+339477", "+339478", "+339479",
        "09475", "09476", "09477", "09478", "09479"
    )

    // 6. NUMÉROS D'URGENCE — NE JAMAIS BLOQUER
    private val NUMEROS_URGENCE = setOf(
        "15", "17", "18", "112", "114", "115",
        "116000", "119", "191", "196", "197",
        "3900", "3901", "3939", "3949", "3975"
    )

    // 7. NUMÉROS VERTS LÉGITIMES — NE PAS BLOQUER
    private val NUMEROS_VERTS_LEGITIMES = setOf(
        "+33800", "+33801", "+33802",
        "0800", "0801", "0802"
    )

    // Tous les préfixes à bloquer combinés
    private val ALL_BLOCKED_PREFIXES: Set<String> by lazy {
        mutableSetOf<String>().apply {
            addAll(SURTAXES_CRITIQUES)
            addAll(SURTAXES_ELEVES)
            addAll(ANCIEN_SURTAXE)
            addAll(ARCEP_DEMARCHAGE)
            addAll(ARCEP_09)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "🔥 SERVICE DÉMARRÉ - ${ALL_BLOCKED_PREFIXES.size} préfixes chargés")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawIncomingNumber = handle?.schemeSpecificPart
        val isPrivateNumber = handle == null || rawIncomingNumber.isNullOrBlank()

        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "📞 APPEL ENTRANT")
        Log.d(TAG, "Numéro brut: '$rawIncomingNumber'")
        Log.d(TAG, "Est masqué: $isPrivateNumber")
        Log.d(TAG, "═══════════════════════════════════════")

        val sharedPrefs = getSharedPreferences("StopDemarchagePrefs", MODE_PRIVATE)
        val blockPrivateNumbersEnabled = sharedPrefs.getBoolean("block_private_numbers", false)

        var shouldBlock = false
        var blockReason = ""

        // ============================================
        // ÉTAPE 1 : Numéros masqués
        // ============================================
        if (isPrivateNumber) {
            if (blockPrivateNumbersEnabled) {
                shouldBlock = true
                blockReason = "Numéro masqué/privé"
                Log.d(TAG, "🚫 BLOQUER : numéro masqué")
            } else {
                Log.d(TAG, "✅ AUTORISER : numéro masqué (blocage désactivé)")
            }
        }
        // ============================================
        // ÉTAPE 2-7 : Numéro visible
        // ============================================
        else if (!rawIncomingNumber.isNullOrEmpty()) {
            val cleanNumber = rawIncomingNumber.replace(Regex("[^0-9+]"), "")
            Log.d(TAG, "🔍 Numéro nettoyé: '$cleanNumber'")

            // --- ÉTAPE 2 : Numéros d'urgence → TOUJOURS AUTORISER ---
            if (isEmergencyNumber(cleanNumber)) {
                shouldBlock = false
                Log.d(TAG, "🆘 AUTORISER : numéro d'urgence")
            }
            // --- ÉTAPE 3 : Contact du téléphone → TOUJOURS AUTORISER ---
            else if (isNumberInContacts(cleanNumber)) {
                shouldBlock = false
                Log.d(TAG, "👤 AUTORISER : numéro dans les contacts")
            }
            // --- ÉTAPE 4 : Liste blanche → TOUJOURS AUTORISER ---
            else if (isNumberInWhiteList(cleanNumber, sharedPrefs)) {
                shouldBlock = false
                Log.d(TAG, "✅ AUTORISER : numéro en liste blanche")
            }
            // --- ÉTAPE 5 : Numéros verts légitimes → AUTORISER ---
            else if (isLegitimateGreenNumber(cleanNumber)) {
                shouldBlock = false
                Log.d(TAG, "📗 AUTORISER : numéro vert légitime")
            }
            // --- ÉTAPE 6 : Liste noire manuelle → BLOQUER ---
            else if (isNumberInBlackList(cleanNumber, sharedPrefs)) {
                shouldBlock = true
                blockReason = "Liste noire manuelle"
                Log.d(TAG, "🚫 BLOQUER : numéro en liste noire")
            }
            // --- ÉTAPE 7 : Préfixes bloqués (JSON) → BLOQUER ---
            else {
                val matchResult = matchesBlockedPrefix(cleanNumber)
                if (matchResult != null) {
                    shouldBlock = true
                    blockReason = matchResult
                    Log.d(TAG, "🚫 BLOQUER : $blockReason")
                } else {
                    Log.d(TAG, "✅ AUTORISER : aucune règle de blocage")
                }
            }
        }

        // ============================================
        // CONSTRUIRE ET ENVOYER LA RÉPONSE
        // ============================================
        val response = if (shouldBlock) {
            Log.d(TAG, "🛑 RÉPONSE: BLOCAGE - $blockReason")
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        } else {
            Log.d(TAG, "📞 RÉPONSE: AUTORISATION")
            CallResponse.Builder().build()
        }

        respondToCall(callDetails, response)

        if (shouldBlock) {
            showBlockedCallNotification(rawIncomingNumber ?: "Inconnu", blockReason)
            saveBlockedCallToHistory(rawIncomingNumber, blockReason)
        }

        Log.d(TAG, "═══════════════════════════════════════")
    }

    // ============================================
    // ✅ VÉRIFICATION NUMÉROS D'URGENCE
    // ============================================
    private fun isEmergencyNumber(number: String): Boolean {
        val formats = getAllFormats(number)
        for (format in formats) {
            if (NUMEROS_URGENCE.contains(format)) {
                return true
            }
        }
        return false
    }

    // ============================================
    // ✅ VÉRIFICATION CONTACTS DU TÉLÉPHONE
    // ============================================
    private fun isNumberInContacts(number: String): Boolean {
        return try {
            val formats = getAllFormats(number)

            for (format in formats) {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(format)
                )

                val cursor: Cursor? = contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        Log.d(TAG, "👤 Contact trouvé pour: $format")
                        return true
                    }
                }
            }

            Log.d(TAG, "👤 Aucun contact trouvé pour: $number")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "⚠️ Permission contacts manquante", e)
            true // Laisser passer par sécurité
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Erreur vérification contacts", e)
            true // Laisser passer par sécurité
        }
    }

    // ============================================
    // ✅ VÉRIFICATION LISTE BLANCHE — CORRIGÉE
    // ============================================
    private fun isNumberInWhiteList(
        number: String,
        sharedPrefs: android.content.SharedPreferences
    ): Boolean {
        // La liste blanche est stockée au format "nom|numéro;nom|numéro"
        val contactsString = sharedPrefs.getString("white_list_contacts", "")
        if (contactsString.isNullOrEmpty()) return false

        // Extraire et normaliser les numéros de la liste blanche
        val whiteListNumbers = contactsString.split(";").mapNotNull { contactData ->
            val parts = contactData.split("|")
            if (parts.size == 2) {
                normalizeNumber(parts[1])
            } else {
                null
            }
        }.toSet()

        if (whiteListNumbers.isEmpty()) return false

        // Vérifier tous les formats du numéro entrant
        val formats = getAllFormats(number)
        for (format in formats) {
            val normalizedFormat = normalizeNumber(format)
            if (whiteListNumbers.contains(normalizedFormat)) {
                Log.d(TAG, "✅ Liste blanche : match pour $format")
                return true
            }
            // Vérifier aussi chaque numéro de la liste blanche
            for (whiteNumber in whiteListNumbers) {
                if (format == whiteNumber ||
                    normalizeNumber(format) == whiteNumber) {
                    Log.d(TAG, "✅ Liste blanche : match $format = $whiteNumber")
                    return true
                }
            }
        }

        Log.d(TAG, "❌ Liste blanche : aucun match pour $number")
        return false
    }

    // ============================================
    // ✅ NORMALISATION DES NUMÉROS
    // ============================================
    private fun normalizeNumber(number: String): String {
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

    // ============================================
    // ✅ VÉRIFICATION NUMÉROS VERTS LÉGITIMES
    // ============================================
    private fun isLegitimateGreenNumber(number: String): Boolean {
        val formats = getAllFormats(number)
        for (format in formats) {
            for (greenPrefix in NUMEROS_VERTS_LEGITIMES) {
                if (format.startsWith(greenPrefix)) {
                    return true
                }
            }
        }
        return false
    }

    // ============================================
    // ✅ VÉRIFICATION LISTE NOIRE MANUELLE
    // ============================================
    private fun isNumberInBlackList(
        number: String,
        sharedPrefs: android.content.SharedPreferences
    ): Boolean {
        val blackList = sharedPrefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
        if (blackList.isEmpty()) return false

        val formats = getAllFormats(number)

        for (format in formats) {
            // Vérification exacte
            if (blackList.contains(format)) {
                return true
            }
            // Vérification normalisée
            val normalizedFormat = normalizeNumber(format)
            if (blackList.contains(normalizedFormat)) {
                return true
            }

            // Vérification par préfixe (numéros partiels)
            for (blocked in blackList) {
                if (blocked.replace("+", "").length >= 4) {
                    if (format.startsWith(blocked) ||
                        normalizedFormat.startsWith(normalizeNumber(blocked))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    // ============================================
    // ✅ VÉRIFICATION PRÉFIXES BLOQUÉS
    // ============================================
    private fun matchesBlockedPrefix(number: String): String? {
        val formats = getAllFormats(number)

        for (format in formats) {
            for (prefix in SURTAXES_CRITIQUES) {
                if (format.startsWith(prefix)) {
                    return "Surtaxé critique: $prefix"
                }
            }
            for (prefix in SURTAXES_ELEVES) {
                if (format.startsWith(prefix)) {
                    return "Surtaxé élevé: $prefix"
                }
            }
            for (prefix in ANCIEN_SURTAXE) {
                if (format.startsWith(prefix)) {
                    return "Ancien surtaxé: $prefix"
                }
            }
            for (prefix in ARCEP_DEMARCHAGE) {
                if (format.startsWith(prefix)) {
                    return "Démarchage ARCEP: $prefix"
                }
            }
            for (prefix in ARCEP_09) {
                if (format.startsWith(prefix)) {
                    return "Démarchage ARCEP 09: $prefix"
                }
            }
        }

        return null
    }

    // ============================================
    // ✅ GÉNÉRATION DE TOUS LES FORMATS
    // ============================================
    private fun getAllFormats(number: String): List<String> {
        val clean = number.replace(Regex("[^0-9+]"), "")

        val formats = mutableListOf(clean)

        if (clean.startsWith("+33")) {
            formats.add(clean.substring(1))              // 33XXXXXXXXX
            formats.add("0${clean.substring(3)}")        // 0XXXXXXXXX
        } else if (clean.startsWith("33") && clean.length > 4) {
            formats.add("+$clean")                        // +33XXXXXXXXX
            formats.add("0${clean.substring(2)}")        // 0XXXXXXXXX
        } else if (clean.startsWith("0") && clean.length >= 10) {
            formats.add("+33${clean.substring(1)}")      // +33XXXXXXXXX
            formats.add("33${clean.substring(1)}")       // 33XXXXXXXXX
        }

        return formats.distinct()
    }

    // ============================================
    // NOTIFICATIONS
    // ============================================
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "call_blocking_channel"
            val channelName = "Blocage d'appels"
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications des appels bloqués"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showBlockedCallNotification(number: String, reason: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val iconRes = try {
            R.drawable.ic_blocked
        } catch (e: Exception) {
            android.R.drawable.stat_sys_phone_call_on_hold
        }

        val notification = NotificationCompat.Builder(this, "call_blocking_channel")
            .setSmallIcon(iconRes)
            .setContentTitle("🛡️ Appel bloqué")
            .setContentText("$number - $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Numéro bloqué: $number\n\n" +
                                "Raison: $reason\n\n" +
                                "Cet appel a été automatiquement rejeté."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveBlockedCallToHistory(number: String?, reason: String) {
        try {
            val sharedPrefs = getSharedPreferences("StopDemarchagePrefs", MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            val currentCount = sharedPrefs.getInt("blocked_calls_count", 0)
            editor.putInt("blocked_calls_count", currentCount + 1)

            editor.putString("last_blocked_number", number ?: "Inconnu")
            editor.putString("last_blocked_reason", reason)
            editor.putLong("last_blocked_time", System.currentTimeMillis())

            editor.apply()

            Log.d(TAG, "💾 Sauvegardé - Total bloqués: ${currentCount + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde", e)
        }
    }
}