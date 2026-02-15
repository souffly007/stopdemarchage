package fr.bonobo.stopdemarchage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeypadManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private lateinit var phoneNumberField: EditText
    private lateinit var contactNameField: TextView

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private val TAG = "KeypadManager"

    // Mapping des boutons vers les tons DTMF
    private val keypadTones = mapOf(
        '0' to ToneGenerator.TONE_DTMF_0,
        '1' to ToneGenerator.TONE_DTMF_1,
        '2' to ToneGenerator.TONE_DTMF_2,
        '3' to ToneGenerator.TONE_DTMF_3,
        '4' to ToneGenerator.TONE_DTMF_4,
        '5' to ToneGenerator.TONE_DTMF_5,
        '6' to ToneGenerator.TONE_DTMF_6,
        '7' to ToneGenerator.TONE_DTMF_7,
        '8' to ToneGenerator.TONE_DTMF_8,
        '9' to ToneGenerator.TONE_DTMF_9,
        '*' to ToneGenerator.TONE_DTMF_S,
        '#' to ToneGenerator.TONE_DTMF_P
    )

    init {
        initializeAudio()
    }

    private fun initializeAudio() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            Log.d(TAG, "Audio initialisé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation audio", e)
        }
    }

    fun setupKeypad(
        phoneField: EditText,
        contactField: TextView,
        keypadButtons: Map<Char, Button>,
        deleteButton: Button,
        callButton: MaterialButton,
        addContactButton: MaterialButton,
        smsButton: MaterialButton,
        historyButton: MaterialButton
    ) {
        phoneNumberField = phoneField
        contactNameField = contactField

        // Configuration des boutons numériques
        keypadButtons.forEach { (digit, button) ->
            button.setOnClickListener {
                addDigit(digit)
                playKeypadSound(digit)
                vibrateKeypress()
            }
        }

        // Configuration du bouton effacer
        deleteButton.setOnClickListener {
            deleteLastDigit()
        }

        // Configuration du bouton appeler
        callButton.setOnClickListener {
            makeCall()
        }

        // Configuration du bouton ajouter aux contacts
        addContactButton.setOnClickListener {
            addToContacts()
        }

        // Configuration du bouton SMS
        smsButton.setOnClickListener {
            sendSms()
        }

        // Configuration du bouton historique
        historyButton.setOnClickListener {
            showHistory()
        }

        // Listener pour détecter les changements et chercher des contacts
        phoneNumberField.setOnTextChangedListener { text ->
            searchContactByNumber(text.toString())
        }
    }

    private fun addDigit(digit: Char) {
        val currentText = phoneNumberField.text.toString()
        val newText = currentText + digit
        phoneNumberField.setText(newText)
        phoneNumberField.setSelection(newText.length)

        Log.d(TAG, "Chiffre ajouté: $digit, numéro: $newText")
    }

    private fun deleteLastDigit() {
        val currentText = phoneNumberField.text.toString()
        if (currentText.isNotEmpty()) {
            val newText = currentText.dropLast(1)
            phoneNumberField.setText(newText)
            phoneNumberField.setSelection(newText.length)

            // Effacer le nom du contact si plus de numéro
            if (newText.isEmpty()) {
                contactNameField.text = ""
                contactNameField.visibility = TextView.GONE
            }

            Log.d(TAG, "Chiffre supprimé, numéro: $newText")
        }
    }

    private fun playKeypadSound(digit: Char) {
        try {
            val tone = keypadTones[digit]
            if (tone != null && toneGenerator != null) {
                toneGenerator?.startTone(tone, 150)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la lecture du son", e)
        }
    }

    private fun vibrateKeypress() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la vibration", e)
        }
    }

    private fun makeCall() {
        val phoneNumber = phoneNumberField.text.toString().trim()

        if (phoneNumber.isEmpty()) {
            Toast.makeText(context, "Veuillez saisir un numéro", Toast.LENGTH_SHORT).show()
            return
        }

        // Normaliser le numéro
        val normalizedNumber = PhoneNormalizer.normalizePhoneNumber(phoneNumber)

        Log.d(TAG, "Tentative d'appel vers: $normalizedNumber")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {

            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$normalizedNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)

                // Sauvegarder dans l'historique
                saveToCallHistory(normalizedNumber)

                Log.d(TAG, "Appel initié vers: $normalizedNumber")
                Toast.makeText(context, "Appel vers $normalizedNumber", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'appel", e)
                Toast.makeText(context, "Erreur lors de l'appel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permission d'appel requise", Toast.LENGTH_LONG).show()
        }
    }

    private fun addToContacts() {
        val phoneNumber = phoneNumberField.text.toString().trim()

        if (phoneNumber.isEmpty()) {
            Toast.makeText(context, "Veuillez saisir un numéro", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Ouverture de l'ajout de contact pour: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'ajout aux contacts", e)
            Toast.makeText(context, "Impossible d'ouvrir les contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSms() {
        val phoneNumber = phoneNumberField.text.toString().trim()

        if (phoneNumber.isEmpty()) {
            Toast.makeText(context, "Veuillez saisir un numéro", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Ouverture SMS pour: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'ouverture SMS", e)
            Toast.makeText(context, "Impossible d'ouvrir les messages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistory() {
        // Basculer vers l'onglet Récents
        Toast.makeText(context, "Basculement vers l'historique des appels", Toast.LENGTH_SHORT).show()
        // Ici vous pourriez déclencher un changement d'onglet
    }

    private fun searchContactByNumber(phoneNumber: String) {
        if (phoneNumber.length < 4) {
            contactNameField.visibility = TextView.GONE
            return
        }

        lifecycleOwner.lifecycleScope.launch {
            val contactName = withContext(Dispatchers.IO) {
                findContactByNumber(phoneNumber)
            }

            if (contactName != null) {
                contactNameField.text = contactName
                contactNameField.visibility = TextView.VISIBLE
                Log.d(TAG, "Contact trouvé: $contactName pour $phoneNumber")
            } else {
                contactNameField.visibility = TextView.GONE
            }
        }
    }

    private fun findContactByNumber(phoneNumber: String): String? {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$phoneNumber%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    return if (nameIndex != -1) it.getString(nameIndex) else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la recherche de contact", e)
        }
        return null
    }

    private fun saveToCallHistory(phoneNumber: String) {
        // Ici vous pourriez sauvegarder l'appel dans votre base de données locale
        // ou utiliser votre CallLogManager
        try {
            Log.d(TAG, "Sauvegarde de l'appel dans l'historique: $phoneNumber")
            // Implémentation à ajouter selon votre architecture
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la sauvegarde", e)
        }
    }

    fun clearNumber() {
        phoneNumberField.setText("")
        contactNameField.text = ""
        contactNameField.visibility = TextView.GONE
    }

    fun setNumber(number: String) {
        phoneNumberField.setText(number)
        phoneNumberField.setSelection(number.length)
        searchContactByNumber(number)
    }

    fun cleanup() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            Log.d(TAG, "Ressources audio libérées")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage", e)
        }
    }
}

// Extension pour simplifier l'écoute des changements de texte
fun EditText.setOnTextChangedListener(callback: (String) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            callback(s?.toString() ?: "")
        }
    })
}