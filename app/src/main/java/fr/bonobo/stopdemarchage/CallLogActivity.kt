package fr.bonobo.stopdemarchage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.SearchView
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallLogActivity : AppCompatActivity() {

    private lateinit var callLogAdapter: CallLogAdapter
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var callLogManager: CallLogManager
    private lateinit var keypadManager: KeypadManager
    private val TAG = "CallLogActivity"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() démarré - Intent: ${intent.action}")

        try {
            setContentView(R.layout.activity_call_log)
            Log.d(TAG, "Layout chargé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement du layout", e)
            Toast.makeText(this, "Erreur: Layout non trouvé", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            callLogManager = CallLogManager(this)
            Log.d(TAG, "CallLogManager initialisé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation de CallLogManager", e)
            Toast.makeText(this, "Erreur: CallLogManager non disponible", Toast.LENGTH_LONG).show()
        }

        setupViews()
        handleDialerIntent()
        checkPermissionsAndLoadData()
    }

    /**
     * Gère les intents de composeur par défaut
     */
    private fun handleDialerIntent() {
        val action = intent.action
        val data = intent.data

        Log.d(TAG, "Gestion de l'intent - Action: $action, Data: $data")

        when (action) {
            Intent.ACTION_DIAL -> {
                // Lancé comme composeur par défaut
                showKeypadTab()
                updateTabSelection(TabType.KEYPAD)

                // Si un numéro est fourni, le pré-remplir
                data?.schemeSpecificPart?.let { phoneNumber ->
                    Log.d(TAG, "Numéro pré-rempli: $phoneNumber")
                    // Attendre que le clavier soit initialisé
                    findViewById<EditText>(R.id.et_phone_number)?.setText(phoneNumber)
                }

                Toast.makeText(this, "Composeur StopDémarchage activé", Toast.LENGTH_SHORT).show()
            }
            Intent.ACTION_CALL_BUTTON -> {
                // Bouton d'appel appuyé
                showKeypadTab()
                updateTabSelection(TabType.KEYPAD)
                Toast.makeText(this, "Prêt à composer", Toast.LENGTH_SHORT).show()
            }
            Intent.ACTION_VIEW -> {
                // Lien tel: cliqué
                showKeypadTab()
                updateTabSelection(TabType.KEYPAD)
                data?.schemeSpecificPart?.let { phoneNumber ->
                    findViewById<EditText>(R.id.et_phone_number)?.setText(phoneNumber)
                }
            }
            else -> {
                // Lancement normal depuis l'app
                showRecentTab()
                updateTabSelection(TabType.RECENT)
            }
        }
    }

    private fun setupViews() {
        Log.d(TAG, "Configuration des vues...")
        setupTabs()
        setupRecyclerViews()
        //setupFab()
        setupSearchView()
        setupKeypad()
        // Ne pas appeler showRecentTab() ici - c'est géré par handleDialerIntent()
        Log.d(TAG, "Vues configurées avec succès")
    }

    private fun setupSearchView() {
        Log.d(TAG, "Configuration de la barre de recherche...")

        // Récupérer le SearchView depuis le layout
        val searchView = findViewById<SearchView>(R.id.search_view)

        if (searchView == null) {
            Log.e(TAG, "SearchView non trouvé!")
            return
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                Log.d(TAG, "Recherche contacts: '$query' (longueur: ${query.length})")

                if (::contactAdapter.isInitialized) {
                    Log.d(TAG, "Total contacts avant filtrage: ${contactAdapter.getTotalContactsCount()}")
                    contactAdapter.filterContacts(query)
                    Log.d(TAG, "Contacts affichés après filtrage: ${contactAdapter.itemCount}")
                    updateContactsCount()

                    // Afficher/masquer l'état vide selon les résultats
                    if (contactAdapter.itemCount == 0 && query.isNotEmpty()) {
                        findViewById<TextView>(R.id.contacts_count)?.text = "Aucun résultat pour \"$query\""
                    }
                } else {
                    Log.w(TAG, "ContactAdapter non initialisé!")
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
        })

        Log.d(TAG, "SearchView configuré avec succès")
    }

    private fun setupKeypad() {
        Log.d(TAG, "Configuration du clavier numérique...")

        keypadManager = KeypadManager(this, this)

        // Récupération des vues du clavier
        val phoneField = findViewById<EditText>(R.id.et_phone_number)
        val contactField = findViewById<TextView>(R.id.tv_contact_name)

        // Vérifier si les vues existent (elles pourraient ne pas être dans le layout actuel)
        if (phoneField == null || contactField == null) {
            Log.w(TAG, "Vues du clavier non trouvées dans le layout actuel")
            return
        }

        // Mapping des boutons du clavier
        val keypadButtons = mapOf(
            '1' to findViewById<Button>(R.id.btn_1),
            '2' to findViewById<Button>(R.id.btn_2),
            '3' to findViewById<Button>(R.id.btn_3),
            '4' to findViewById<Button>(R.id.btn_4),
            '5' to findViewById<Button>(R.id.btn_5),
            '6' to findViewById<Button>(R.id.btn_6),
            '7' to findViewById<Button>(R.id.btn_7),
            '8' to findViewById<Button>(R.id.btn_8),
            '9' to findViewById<Button>(R.id.btn_9),
            '0' to findViewById<Button>(R.id.btn_0),
            '*' to findViewById<Button>(R.id.btn_star),
            '#' to findViewById<Button>(R.id.btn_hash)
        )

        // Boutons d'action
        val deleteButton = findViewById<Button>(R.id.btn_delete)
        val callButton = findViewById<MaterialButton>(R.id.btn_call)
        val addContactButton = findViewById<MaterialButton>(R.id.btn_add_contact)
        val smsButton = findViewById<MaterialButton>(R.id.btn_sms)
        val historyButton = findViewById<MaterialButton>(R.id.btn_history)

        // Vérifier que tous les boutons existent
        val allButtonsExist = keypadButtons.values.all { it != null } &&
                deleteButton != null && callButton != null &&
                addContactButton != null && smsButton != null && historyButton != null

        if (!allButtonsExist) {
            Log.w(TAG, "Certains boutons du clavier non trouvés")
            return
        }

        // Configuration du gestionnaire de clavier
        keypadManager.setupKeypad(
            phoneField, contactField,
            keypadButtons.mapValues { it.value!! },
            deleteButton!!, callButton!!, addContactButton!!, smsButton!!, historyButton!!
        )

        // Gestion du bouton historique pour basculer vers l'onglet Récents
        historyButton.setOnClickListener {
            showRecentTab()
            updateTabSelection(TabType.RECENT)
        }

        Log.d(TAG, "Clavier numérique configuré avec succès")
    }

    private fun updateContactsCount() {
        val count = contactAdapter.itemCount
        findViewById<TextView>(R.id.contacts_count)?.text = "$count contacts"
    }

    private fun setupTabs() {
        Log.d(TAG, "Configuration des onglets...")
        findViewById<TextView>(R.id.tab_recent)?.setOnClickListener {
            Log.d(TAG, "Onglet Recent cliqué")
            showRecentTab()
            updateTabSelection(TabType.RECENT)
        }
        findViewById<TextView>(R.id.tab_contacts)?.setOnClickListener {
            Log.d(TAG, "Onglet Contacts cliqué")
            showContactsTab()
            updateTabSelection(TabType.CONTACTS)
        }
        findViewById<TextView>(R.id.tab_keypad)?.setOnClickListener {
            Log.d(TAG, "Onglet Keypad cliqué")
            showKeypadTab()
            updateTabSelection(TabType.KEYPAD)
        }
    }

    private fun setupRecyclerViews() {
        Log.d(TAG, "Configuration des RecyclerViews...")
        findViewById<RecyclerView>(R.id.calls_recycler_view)?.let { recyclerView ->
            callLogAdapter = CallLogAdapter(
                context = this,
                onCallClick = { callEntry -> makeCall(callEntry.number) },
                onBlockToggle = { callEntry -> toggleBlockNumber(callEntry) },
                onAddToContacts = { callEntry -> addToContacts(callEntry) },
                onSendMessage = { callEntry -> sendMessage(callEntry.number) }
            )
            recyclerView.apply {
                layoutManager = LinearLayoutManager(this@CallLogActivity)
                adapter = callLogAdapter
            }
        } ?: Log.e(TAG, "RecyclerView d'appels non trouvé!")

        findViewById<RecyclerView>(R.id.recycler_view_contacts)?.let { contactsRecyclerView ->
            contactAdapter = ContactAdapter { contact ->
                makeCall(contact.phoneNumber ?: "")
            }
            contactsRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@CallLogActivity)
                adapter = contactAdapter
            }
        } ?: Log.e(TAG, "RecyclerView de contacts non trouvé!")
    }



    private fun showDuplicateCleanupDialog() {
        lifecycleScope.launch {
            val rawContacts = withContext(Dispatchers.IO) {
                getContactsFromCursor()
            }
            val duplicates = ContactDeduplicator.findDuplicates(rawContacts)

            if (duplicates.isNotEmpty()) {
                AlertDialog.Builder(this@CallLogActivity)
                    .setTitle("Doublons détectés")
                    .setMessage("${duplicates.size} groupes de contacts en doublon trouvés.\n\nVoulez-vous les fusionner automatiquement ?")
                    .setPositiveButton("Fusionner") { _, _ ->
                        val cleanedContacts = ContactDeduplicator.cleanContacts(rawContacts)
                        contactAdapter.updateContacts(cleanedContacts)
                        updateContactsCount()
                        Toast.makeText(this@CallLogActivity, "✅ ${rawContacts.size - cleanedContacts.size} doublons fusionnés", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            } else {
                Toast.makeText(this@CallLogActivity, "Aucun doublon détecté", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndLoadData() {
        Log.d(TAG, "Vérification des permissions...")
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Permissions manquantes: $missingPermissions")
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "Toutes les permissions accordées, chargement des données...")
            loadCallLogs()
            loadContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permissions accordées, chargement des données")
                loadCallLogs()
                loadContacts()
            } else {
                Log.w(TAG, "Permissions refusées")
                Toast.makeText(this, "Permissions nécessaires", Toast.LENGTH_LONG).show()
                showEmptyState()
            }
        }
    }

    private fun loadCallLogs() {
        Log.d(TAG, "Chargement du journal d'appels...")
        if (!::callLogManager.isInitialized) {
            Log.e(TAG, "CallLogManager non initialisé!")
            return
        }
        lifecycleScope.launch {
            val callLogs = withContext(Dispatchers.IO) {
                callLogManager.getCallLogs(50)
            }
            callLogAdapter.updateCalls(callLogs)
            updateStats(callLogs)
            if (callLogs.isEmpty()) {
                showEmptyState()
            }
        }
    }

    private fun loadContacts() {
        Log.d(TAG, "Chargement des contacts...")
        if (!::contactAdapter.isInitialized) {
            Log.e(TAG, "ContactAdapter non initialisé!")
            return
        }
        lifecycleScope.launch {
            val rawContacts = withContext(Dispatchers.IO) {
                getContactsFromCursor()
            }
            val cleanedContacts = ContactDeduplicator.cleanContacts(rawContacts)
            val sortedContacts = cleanedContacts.sortedBy { it.name }

            contactAdapter.updateContacts(sortedContacts)
            updateContactsCount()

            if (sortedContacts.isEmpty()) {
                showEmptyContactsState()
            } else {
                findViewById<RecyclerView>(R.id.recycler_view_contacts)?.visibility = View.VISIBLE
            }
        }
    }

    private fun getContactsFromCursor(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val contact = Contact(
                        id = it.getLong(idIndex),
                        name = it.getString(nameIndex),
                        phoneNumber = it.getString(numberIndex)
                    )
                    contacts.add(contact)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la lecture des contacts", e)
        }
        return contacts
    }

    private fun showEmptyState() {
        Log.d(TAG, "Affichage de l'état vide")
        try {
            findViewById<RecyclerView>(R.id.calls_recycler_view)?.visibility = View.GONE
            findViewById<TextView>(R.id.empty_state_text)?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de l'état vide", e)
            Toast.makeText(this, "Aucun appel dans l'historique\nPassez un appel pour voir les données", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEmptyContactsState() {
        Log.d(TAG, "Affichage de l'état vide des contacts")
        try {
            findViewById<RecyclerView>(R.id.recycler_view_contacts)?.visibility = View.GONE
            findViewById<View>(R.id.empty_view)?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de l'état vide des contacts", e)
            Toast.makeText(this, "Aucun contact trouvé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStats(callLogs: List<CallLogEntry>) {
        Log.d(TAG, "Mise à jour des statistiques...")
        try {
            val received = callLogs.count { it.type == android.provider.CallLog.Calls.INCOMING_TYPE }
            val outgoing = callLogs.count { it.type == android.provider.CallLog.Calls.OUTGOING_TYPE }
            val missed = callLogs.count { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
            val blocked = callLogs.count { it.isBlocked }
            Log.d(TAG, "Stats - Reçus: $received, Sortants: $outgoing, Manqués: $missed, Bloqués: $blocked")
            findViewById<TextView>(R.id.stats_received)?.text = "Reçus: $received"
            findViewById<TextView>(R.id.stats_outgoing)?.text = "Sortants: $outgoing"
            findViewById<TextView>(R.id.stats_missed)?.text = "Manqués: $missed"
            findViewById<TextView>(R.id.stats_blocked)?.text = "Bloqués: $blocked"
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour des stats", e)
        }
    }

    private fun showRecentTab() {
        Log.d(TAG, "Affichage de l'onglet Recent")
        try {
            findViewById<View>(R.id.recent_tab_content)?.visibility = View.VISIBLE
            findViewById<View>(R.id.contacts_tab_content)?.visibility = View.GONE
            findViewById<View>(R.id.keypad_tab_content)?.visibility = View.GONE
            findViewById<FloatingActionButton>(R.id.fab_clean_duplicates)?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de l'onglet Recent", e)
        }
    }

    private fun showContactsTab() {
        Log.d(TAG, "Affichage de l'onglet Contacts")
        try {
            findViewById<View>(R.id.recent_tab_content)?.visibility = View.GONE
            findViewById<View>(R.id.contacts_tab_content)?.visibility = View.VISIBLE
            findViewById<View>(R.id.keypad_tab_content)?.visibility = View.GONE
            findViewById<FloatingActionButton>(R.id.fab_clean_duplicates)?.visibility = View.VISIBLE
            if (!::contactAdapter.isInitialized) {
                loadContacts()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de l'onglet Contacts", e)
        }
    }

    private fun showKeypadTab() {
        Log.d(TAG, "Affichage de l'onglet Keypad")
        try {
            findViewById<View>(R.id.recent_tab_content)?.visibility = View.GONE
            findViewById<View>(R.id.contacts_tab_content)?.visibility = View.GONE
            findViewById<View>(R.id.keypad_tab_content)?.visibility = View.VISIBLE
            findViewById<FloatingActionButton>(R.id.fab_clean_duplicates)?.visibility = View.GONE

            // Focus sur le champ de numéro
            findViewById<EditText>(R.id.et_phone_number)?.requestFocus()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de l'onglet Keypad", e)
        }
    }

    private fun updateTabSelection(selectedTab: TabType) {
        Log.d(TAG, "Mise à jour de la sélection d'onglet: $selectedTab")
        try {
            findViewById<TextView>(R.id.tab_recent)?.isSelected = false
            findViewById<TextView>(R.id.tab_contacts)?.isSelected = false
            findViewById<TextView>(R.id.tab_keypad)?.isSelected = false
            when (selectedTab) {
                TabType.RECENT -> findViewById<TextView>(R.id.tab_recent)?.isSelected = true
                TabType.CONTACTS -> findViewById<TextView>(R.id.tab_contacts)?.isSelected = true
                TabType.KEYPAD -> findViewById<TextView>(R.id.tab_keypad)?.isSelected = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour de la sélection d'onglet", e)
        }
    }

    private fun makeCall(phoneNumber: String) {
        Log.d(TAG, "Appel direct vers: $phoneNumber")
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(callIntent)
                Log.d(TAG, "Appel direct initié vers: $phoneNumber")
            } else {
                Log.w(TAG, "Permission CALL_PHONE manquante, demande de permission...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    PERMISSION_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'appel direct", e)
            Toast.makeText(this, "Impossible de passer l'appel: ${e.message}", Toast.LENGTH_SHORT).show()
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(dialIntent)
                Log.d(TAG, "Fallback vers dialer pour: $phoneNumber")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Erreur même avec le dialer", fallbackException)
            }
        }
    }

    private fun toggleBlockNumber(callEntry: CallLogEntry) {
        Log.d(TAG, "Toggle blocage pour: ${callEntry.number}")
        try {
            if (!::callLogManager.isInitialized) {
                Log.e(TAG, "CallLogManager non initialisé!")
                Toast.makeText(this, "Erreur: Manager non disponible", Toast.LENGTH_SHORT).show()
                return
            }
            if (callEntry.isBlocked) {
                callLogManager.unblockNumber(callEntry.number)
                Log.d(TAG, "Numéro ${callEntry.number} débloqué")
                Toast.makeText(this, "Numéro ${callEntry.number} débloqué", Toast.LENGTH_SHORT).show()
            } else {
                callLogManager.blockNumber(callEntry.number)
                Log.d(TAG, "Numéro ${callEntry.number} bloqué")
                Toast.makeText(this, "Numéro ${callEntry.number} bloqué", Toast.LENGTH_SHORT).show()
            }
            loadCallLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du toggle de blocage pour ${callEntry.number}", e)
            Toast.makeText(this, "Erreur lors du blocage/déblocage: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToContacts(callEntry: CallLogEntry) {
        Log.d(TAG, "Ajout aux contacts: ${callEntry.number}")
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = "vnd.android.cursor.dir/contact"
                putExtra("phone", callEntry.number)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'ajout aux contacts", e)
            Toast.makeText(this, "Impossible d'ouvrir les contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(phoneNumber: String) {
        Log.d(TAG, "Envoi message vers: $phoneNumber")
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'envoi de message", e)
            Toast.makeText(this, "Impossible d'ouvrir les messages", Toast.LENGTH_SHORT).show()
        }
    }

    // Méthodes utilitaires pour interagir avec le clavier depuis d'autres parties de l'app
    fun setKeypadNumber(number: String) {
        if (::keypadManager.isInitialized) {
            keypadManager.setNumber(number)
            showKeypadTab()
            updateTabSelection(TabType.KEYPAD)
        }
    }

    fun clearKeypadNumber() {
        if (::keypadManager.isInitialized) {
            keypadManager.clearNumber()
        }
    }

    private enum class TabType {
        RECENT, CONTACTS, KEYPAD
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() appelé")
        if (::callLogManager.isInitialized) {
            loadCallLogs()
        }
        if (findViewById<View>(R.id.contacts_tab_content)?.visibility == View.VISIBLE) {
            loadContacts()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() appelé")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::keypadManager.isInitialized) {
            keypadManager.cleanup()
        }
        Log.d(TAG, "onDestroy() appelé")
    }
}