package fr.bonobo.stopdemarchage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreManager(private val context: Context) {

    private val TAG = "BackupRestoreManager"
    private val PREFS_FILE_NAME = "StopDemarchagePrefs"
    private val KEY_BLOCKED_NUMBERS = "blocked_numbers"

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    fun createBackupJson(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = dateFormat.format(Date())

        val blockedNumbers = sharedPrefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()

        val json = JSONObject().apply {
            put("app", "Stop Démarchage")
            put("version", "3.0.0")
            put("date_backup", now)
            put("total_blocked", blockedNumbers.size)

            val blockedArray = JSONArray()
            blockedNumbers.sorted().forEach { number ->
                val item = JSONObject().apply {
                    put("number", number)
                }
                blockedArray.put(item)
            }
            put("blocked_numbers", blockedArray)
        }

        return json.toString(4)
    }

    fun saveBackupToUri(uri: Uri): BackupResult {
        return try {
            val json = createBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
                writer.write(json)
                writer.flush()
            }

            val blockedCount = sharedPrefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet())?.size ?: 0

            Log.d(TAG, "Sauvegarde réussie: $blockedCount numéros bloqués")

            BackupResult(
                success = true,
                message = "✅ Sauvegarde réussie !\n\n" +
                        "📊 Données sauvegardées :\n" +
                        "• Liste noire : $blockedCount numéros",
                blockedCount = blockedCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde", e)
            BackupResult(
                success = false,
                message = "❌ Erreur lors de la sauvegarde :\n${e.message}"
            )
        }
    }

    fun restoreFromUri(uri: Uri, mode: RestoreMode): BackupResult {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                reader.readText()
            } ?: throw Exception("Impossible de lire le fichier")

            val json = JSONObject(jsonString)

            val appName = json.optString("app", "")
            if (appName != "Stop Démarchage") {
                return BackupResult(
                    success = false,
                    message = "❌ Ce fichier n'est pas une sauvegarde Stop Démarchage"
                )
            }

            val importedBlocked = mutableSetOf<String>()
            val blockedArray = json.optJSONArray("blocked_numbers")
            if (blockedArray != null) {
                for (i in 0 until blockedArray.length()) {
                    val item = blockedArray.getJSONObject(i)
                    val number = item.getString("number")
                    if (number.isNotBlank()) {
                        importedBlocked.add(number)
                    }
                }
            }

            val editor = sharedPrefs.edit()
            var finalBlockedCount = 0
            var newBlockedCount = 0

            when (mode) {
                RestoreMode.MERGE -> {
                    val existingBlocked = sharedPrefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()
                    val mergedBlocked = existingBlocked.toMutableSet()

                    newBlockedCount = importedBlocked.count { it !in existingBlocked }
                    mergedBlocked.addAll(importedBlocked)

                    editor.putStringSet(KEY_BLOCKED_NUMBERS, mergedBlocked)
                    finalBlockedCount = mergedBlocked.size
                }
                RestoreMode.REPLACE -> {
                    editor.putStringSet(KEY_BLOCKED_NUMBERS, importedBlocked)
                    finalBlockedCount = importedBlocked.size
                    newBlockedCount = finalBlockedCount
                }
            }

            editor.apply()

            val dateBackup = json.optString("date_backup", "Inconnue")
            val modeText = if (mode == RestoreMode.MERGE) "Fusion" else "Remplacement"

            Log.d(TAG, "Restauration réussie ($modeText): $finalBlockedCount bloqués")

            val messageBuilder = StringBuilder()
            messageBuilder.append("✅ Restauration réussie !\n\n")
            messageBuilder.append("📅 Date de la sauvegarde : $dateBackup\n")
            messageBuilder.append("🔄 Mode : $modeText\n\n")
            messageBuilder.append("📊 Résultat :\n")
            messageBuilder.append("• Liste noire : $finalBlockedCount numéros")
            if (mode == RestoreMode.MERGE) {
                messageBuilder.append(" ($newBlockedCount nouveaux)")
            }

            BackupResult(
                success = true,
                message = messageBuilder.toString(),
                blockedCount = finalBlockedCount
            )

        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Erreur parsing JSON", e)
            BackupResult(
                success = false,
                message = "❌ Le fichier est corrompu ou dans un format invalide"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur restauration", e)
            BackupResult(
                success = false,
                message = "❌ Erreur lors de la restauration :\n${e.message}"
            )
        }
    }

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val blockedCount: Int = 0
    )

    enum class RestoreMode {
        MERGE,
        REPLACE
    }

    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val date = dateFormat.format(Date())
        return "StopDemarchage_backup_$date.json"
    }

    fun getCurrentDataSummary(): String {
        val blockedCount = sharedPrefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet())?.size ?: 0
        return "📊 Données actuelles :\n• Liste noire : $blockedCount numéros"
    }
}