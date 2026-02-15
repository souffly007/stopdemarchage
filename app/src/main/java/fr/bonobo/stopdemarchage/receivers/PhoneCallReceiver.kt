package fr.bonobo.stopdemarchage.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import fr.bonobo.stopdemarchage.CallLogManager
import fr.bonobo.stopdemarchage.utils.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BroadcastReceiver pour intercepter les appels et déclencher le nettoyage.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Appel terminé - déclenchement du nettoyage du journal.")
                // Lancez la déduplication après chaque appel terminé.
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val callLogManager = CallLogManager(context)
                        callLogManager.performFullCleanup()
                        Log.d(TAG, "Nettoyage du journal d'appels terminé.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur lors du nettoyage du journal d'appels", e)
                    }
                }
            }
        }
    }
}

/**
 * Extensions pour votre CallLogManager
 */

/**
 * Extension pour fusionner automatiquement les formats de numéros.
 */
fun CallLogManager.mergeNumberFormats(oldFormat: String, normalizedFormat: String) {
    Log.d("CallLogManager", "Fusion des formats : $oldFormat -> $normalizedFormat")
    // Vous devez implémenter ici la logique de mise à jour dans votre base de données ou votre
    // source de données. Par exemple, une requête SQL "UPDATE calls SET number = ? WHERE number = ?".
}

/**
 * Extension pour nettoyer périodiquement tous les doublons.
 */
suspend fun CallLogManager.performFullCleanup() = withContext(Dispatchers.IO) {
    try {
        val allCalls = getCallLogs(1000) // Récupère tous les appels pour un nettoyage complet.
        val normalizedGroups = allCalls.groupBy { call ->
            PhoneNumberNormalizer.normalizePhoneNumber(call.number)
        }

        normalizedGroups.forEach { (normalized, calls) ->
            if (calls.size > 1) {
                Log.d("CallLogManager", "Groupe de doublons détecté pour $normalized. Fusion de ${calls.size} entrées.")
                calls.forEach { call ->
                    val originalNumber = call.number
                    // Vérifiez si le format original est déjà normalisé
                    if (originalNumber != normalized) {
                        // Mettez à jour chaque entrée vers le format normalisé.
                        mergeNumberFormats(originalNumber, normalized!!)
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("CallLogManager", "Erreur lors du nettoyage complet", e)
    }
}