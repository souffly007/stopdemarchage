package fr.bonobo.stopdemarchage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * ContentProvider pour les SMS
 * Nécessaire si vous voulez que votre app soit reconnue comme une app SMS complète
 */
class SmsProvider : ContentProvider() {

    private val TAG = "SmsProvider"

    override fun onCreate(): Boolean {
        Log.d(TAG, "SmsProvider créé")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "Query appelée sur: $uri")
        // Pour l'instant, retourne null
        // Vous pouvez implémenter la logique de requête si nécessaire
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.d(TAG, "Insert appelée sur: $uri")
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        Log.d(TAG, "Delete appelée sur: $uri")
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        Log.d(TAG, "Update appelée sur: $uri")
        return 0
    }
}