package fr.bonobo.stopdemarchage

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Suppression de Gson et TypeToken, car nous allons utiliser putStringSet/getStringSet

class BlockedNumbersActivity : AppCompatActivity() { // Renommée ici

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BlockedNumberAdapter // Nouvel adaptateur ou PrefixAdapter modifié
    private lateinit var btnAddNumber: Button // Renommé le bouton
    private lateinit var sharedPreferences: SharedPreferences

    // Clés SharedPreferences (DOIVENT CORRESPONDRE AUX AUTRES CLASSES)
    private val PREFS_FILE_NAME = "StopDemarchagePrefs"
    private val KEY_BLOCKED_NUMBERS = "blocked_numbers"

    // La liste de travail contiendra des String (numéros ou préfixes normalisés)
    private val blockedList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_numbers) // Renommé le layout

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Numéros Bloqués" // Nouveau titre

        initViews()
        initSharedPreferences()
        loadBlockedNumbers() // Nouvelle fonction de chargement
        setupRecyclerView()
        setupAddButton()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewBlockedNumbers) // Nouvel ID de RecyclerView
        btnAddNumber = findViewById(R.id.btnAddBlockedNumber) // Nouvel ID de bouton
    }

    private fun initSharedPreferences() {
        // Utiliser le nom du fichier SharedPreferences commun
        sharedPreferences = getSharedPreferences(PREFS_FILE_NAME, MODE_PRIVATE)
    }

    private fun loadBlockedNumbers() {
        // Lire directement le Set<String> depuis SharedPreferences
        val savedNumbersSet = sharedPreferences.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()
        blockedList.clear()
        blockedList.addAll(savedNumbersSet.sorted()) // Trier pour un affichage cohérent
    }

    private fun setupRecyclerView() {
        adapter = BlockedNumberAdapter(blockedList) { number -> // Passe la lambda de suppression
            showDeleteConfirmation(number)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupAddButton() {
        btnAddNumber.setOnClickListener {
            showAddNumberDialog()
        }
    }

    private fun showAddNumberDialog() {
        val editText = EditText(this)
        editText.hint = "Ex: +33612345678, 09475..." // Indication pour l'utilisateur

        AlertDialog.Builder(this)
            .setTitle("Ajouter un numéro ou préfixe")
            .setMessage("Entrez le numéro ou préfixe à bloquer (avec ou sans '+' au début) :")
            .setView(editText)
            .setPositiveButton("Ajouter") { _, _ ->
                val rawInput = editText.text.toString().trim()
                addBlockedNumber(rawInput)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun addBlockedNumber(input: String) {
        if (input.isEmpty()) {
            Toast.makeText(this, "L'entrée ne peut pas être vide", Toast.LENGTH_SHORT).show()
            return
        }

        // >>> Utiliser la même fonction de normalisation que CallLogManager et CallScreening
        val normalizedNumber = normalizePhoneNumber(input)

        if (blockedList.contains(normalizedNumber)) {
            Toast.makeText(this, "Ce numéro/préfixe est déjà bloqué", Toast.LENGTH_SHORT).show()
            return
        }

        // Validation simple: doit contenir que des chiffres et potentiellement '+' au début
        if (!normalizedNumber.matches(Regex("^[+][0-9]+$")) && !normalizedNumber.matches(Regex("^[0-9]+$"))) {
            Toast.makeText(this, "Format invalide. Utilisez des chiffres (ex: 0612345678) ou '+XX...' (ex: +33612345678).", Toast.LENGTH_LONG).show()
            return
        }

        blockedList.add(normalizedNumber)
        blockedList.sort() // Trier après ajout
        adapter.notifyDataSetChanged()
        saveBlockedNumbers()
        Toast.makeText(this, "'$normalizedNumber' ajouté à la liste noire", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmation(number: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer de la liste noire")
            .setMessage("Voulez-vous supprimer '$number' de la liste des numéros bloqués ?")
            .setPositiveButton("Supprimer") { _, _ ->
                deleteBlockedNumber(number)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteBlockedNumber(number: String) {
        if (blockedList.remove(number)) {
            adapter.notifyDataSetChanged()
            saveBlockedNumbers()
            Toast.makeText(this, "'$number' supprimé de la liste noire", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBlockedNumbers() {
        // Enregistrer le Set<String> directement
        sharedPreferences.edit().putStringSet(KEY_BLOCKED_NUMBERS, blockedList.toSet()).apply()
    }

    // --- Fonction de normalisation des numéros - DOIT ÊTRE LA MÊME PARTOUT ---
    private fun normalizePhoneNumber(number: String): String {
        var normalized = number.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        if (normalized.startsWith("0") && normalized.length == 10 && normalized.matches("^0[1-9][0-9]{8}$".toRegex())) {
            normalized = "+33" + normalized.substring(1)
        }
        // Pour les préfixes qui ne sont pas des numéros complets (ex: "09475"),
        // on ne les normalise pas en "+33" car ils ne sont pas des numéros de téléphone valides complets.
        // Ils restent tels quels pour la comparaison par "startsWith" dans CallScreeningService.
        // C'est un point délicat. Si vous voulez normaliser les préfixes aussi (ex: "09475" -> "+339475"),
        // alors il faudra adapter la liste 'blockedPrefixes' dans CallScreeningService et sa logique.
        // Pour l'instant, je les laisse bruts s'ils ne correspondent pas au format 0XXXXXXXXX.

        return normalized
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}