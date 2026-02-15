package fr.bonobo.stopdemarchage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import android.util.Log

class ContactAdapter(
    private var contacts: MutableList<Contact> = mutableListOf(),
    private val onContactClick: (Contact) -> Unit = {}
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>(), Filterable {

    // Liste complète des contacts pour le filtrage
    private var contactsFullList: MutableList<Contact> = mutableListOf()

    private val TAG = "ContactAdapter"

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.contact_name)
        val phoneTextView: TextView = view.findViewById(R.id.contact_number)
        val contactPhoto: ImageView = view.findViewById(R.id.contact_photo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.nameTextView.text = contact.name
        holder.phoneTextView.text = contact.phoneNumber

        // Image de contact par défaut
        holder.contactPhoto.setImageResource(R.drawable.ic_contact_placeholder)

        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
    }

    override fun getItemCount() = contacts.size

    /**
     * Met à jour la liste des contacts avec détection des changements
     */
    fun updateContacts(newContacts: List<Contact>) {
        Log.d(TAG, "Mise à jour des contacts: ${newContacts.size} contacts")

        // IMPORTANT: Sauvegarder la liste complète pour le filtrage
        contactsFullList.clear()
        contactsFullList.addAll(newContacts)

        val diffCallback = ContactDiffCallback(contacts, newContacts)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        contacts.clear()
        contacts.addAll(newContacts)
        diffResult.dispatchUpdatesTo(this)

        Log.d(TAG, "Liste complète sauvegardée: ${contactsFullList.size} contacts")
    }

    // ============== SYSTÈME DE FILTRAGE ==============
    override fun getFilter(): Filter {
        return contactFilter
    }

    private val contactFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.lowercase()?.trim() ?: ""
            Log.d(TAG, "Filtrage avec query: '$query'")
            Log.d(TAG, "Liste complète disponible: ${contactsFullList.size} contacts")

            val filteredList = mutableListOf<Contact>()

            if (query.isEmpty()) {
                // Si pas de recherche, afficher tous les contacts
                filteredList.addAll(contactsFullList)
                Log.d(TAG, "Pas de filtre, affichage de tous les contacts: ${filteredList.size}")
            } else {
                for (contact in contactsFullList) {
                    // Recherche dans le nom ET le numéro (avec vérification null)
                    val contactName = contact.name?.lowercase() ?: ""
                    val contactPhone = contact.phoneNumber ?: ""

                    Log.d(TAG, "Test contact: '$contactName' avec '$contactPhone'")

                    if (contactName.contains(query) || contactPhone.contains(query)) {
                        filteredList.add(contact)
                        Log.d(TAG, "Contact correspond: ${contact.name}")
                    }
                }
                Log.d(TAG, "Filtrage terminé: ${filteredList.size} contacts trouvés")
            }

            val results = FilterResults()
            results.values = filteredList
            results.count = filteredList.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            Log.d(TAG, "Publication des résultats...")
            contacts.clear()
            @Suppress("UNCHECKED_CAST")
            val filteredContacts = results?.values as? List<Contact> ?: emptyList()
            contacts.addAll(filteredContacts)
            Log.d(TAG, "Résultats publiés: ${contacts.size} contacts affichés")
            notifyDataSetChanged()
        }
    }

    /**
     * Méthode publique pour filtrer facilement
     */
    fun filterContacts(query: String) {
        Log.d(TAG, "Demande de filtrage: '$query'")
        filter.filter(query)
    }

    /**
     * Remet tous les contacts (annule le filtre)
     */
    fun clearFilter() {
        filter.filter("")
    }

    /**
     * Retourne le nombre total de contacts (avant filtrage)
     */
    fun getTotalContactsCount(): Int {
        return contactsFullList.size
    }

    /**
     * Nettoie automatiquement les doublons dans la liste
     */
    fun cleanDuplicates(): Int {
        val originalSize = contactsFullList.size
        val cleanedContacts = ContactDeduplicator.cleanContacts(contactsFullList)
        updateContacts(cleanedContacts)
        return originalSize - cleanedContacts.size
    }

    /**
     * Détecte et retourne les groupes de doublons
     */
    fun findDuplicates(): Map<String, List<Contact>> {
        return ContactDeduplicator.findDuplicates(contactsFullList)
    }

    /**
     * Normalise tous les numéros de téléphone
     */
    fun normalizeAllPhones() {
        val updatedContacts = contactsFullList.map { contact ->
            val normalized = PhoneNormalizer.normalizePhoneNumber(contact.phoneNumber)
            if (normalized.startsWith("+33")) {
                contact.copy(phoneNumber = PhoneNormalizer.formatForDisplay(normalized))
            } else {
                contact
            }
        }
        updateContacts(updatedContacts)
    }
}

// PhoneNormalizer.kt
object PhoneNormalizer {

    /**
     * Normalise un numéro de téléphone français vers le format +33XXXXXXXXX
     */
    fun normalizePhoneNumber(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return ""

        // Nettoyer le numéro (garder seulement les chiffres et le +)
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")

        return when {
            // Si le numéro commence par +33
            cleanNumber.startsWith("+33") -> {
                val digits = cleanNumber.substring(3)
                if (digits.length == 9 && isValidFirstDigit(digits.first())) {
                    "+33$digits"
                } else phoneNumber
            }

            // Si le numéro commence par 0033
            cleanNumber.startsWith("0033") -> {
                val digits = cleanNumber.substring(4)
                if (digits.length == 9 && isValidFirstDigit(digits.first())) {
                    "+33$digits"
                } else phoneNumber
            }

            // Si le numéro commence par 0 (format français local)
            cleanNumber.startsWith("0") && cleanNumber.length == 10 -> {
                val digits = cleanNumber.substring(1)
                if (isValidFirstDigit(digits.first())) {
                    "+33$digits"
                } else phoneNumber
            }

            // Si c'est déjà 9 chiffres sans préfixe
            cleanNumber.length == 9 && isValidFirstDigit(cleanNumber.first()) -> {
                "+33$cleanNumber"
            }

            else -> phoneNumber // Retourner tel quel si aucun format reconnu
        }
    }

    /**
     * Vérifie si le premier chiffre est valide pour un numéro français
     */
    private fun isValidFirstDigit(digit: Char): Boolean {
        return digit in '1'..'9'
    }

    /**
     * Formate un numéro normalisé pour l'affichage
     * +33XXXXXXXXX -> +33 X XX XX XX XX
     */
    fun formatForDisplay(normalizedNumber: String): String {
        return if (normalizedNumber.startsWith("+33") && normalizedNumber.length == 12) {
            val digits = normalizedNumber.substring(3)
            "+33 ${digits[0]} ${digits.substring(1, 3)} ${digits.substring(3, 5)} ${digits.substring(5, 7)} ${digits.substring(7, 9)}"
        } else {
            normalizedNumber
        }
    }
}

// ContactDeduplicator.kt
object ContactDeduplicator {

    /**
     * Détecte et groupe les contacts dupliqués
     */
    fun findDuplicates(contacts: List<Contact>): Map<String, List<Contact>> {
        val phoneGroups = mutableMapOf<String, MutableList<Contact>>()

        contacts.forEach { contact ->
            val normalizedPhone = PhoneNormalizer.normalizePhoneNumber(contact.phoneNumber)

            if (normalizedPhone.startsWith("+33")) {
                phoneGroups.getOrPut(normalizedPhone) { mutableListOf() }.add(contact)
            }
        }

        // Retourner seulement les groupes avec des doublons
        return phoneGroups.filter { it.value.size > 1 }
    }

    /**
     * Fusionne automatiquement les contacts dupliqués
     */
    fun mergeContacts(duplicates: List<Contact>): Contact? {
        if (duplicates.isEmpty()) return null

        // Prendre le contact avec le nom le plus complet
        val bestContact = duplicates.maxByOrNull { it.name?.length ?: 0 } ?: duplicates.first()

        // Créer le contact fusionné
        return Contact(
            id = bestContact.id,
            name = bestContact.name,
            phoneNumber = PhoneNormalizer.formatForDisplay(
                PhoneNormalizer.normalizePhoneNumber(bestContact.phoneNumber)
            )
        )
    }

    /**
     * Nettoie automatiquement la liste de contacts
     */
    fun cleanContacts(originalContacts: List<Contact>): List<Contact> {
        val cleanedContacts = mutableListOf<Contact>()
        val processedPhones = mutableSetOf<String>()

        val duplicates = findDuplicates(originalContacts)

        // Traiter les doublons
        duplicates.forEach { (_, duplicateGroup) ->
            val merged = mergeContacts(duplicateGroup)
            if (merged != null) {
                cleanedContacts.add(merged)
                // Marquer tous les numéros originaux comme traités
                duplicateGroup.forEach { contact ->
                    processedPhones.add(contact.phoneNumber ?: "")
                }
            }
        }

        // Ajouter les contacts sans doublons
        originalContacts.forEach { contact ->
            if (contact.phoneNumber !in processedPhones) {
                val normalized = PhoneNormalizer.normalizePhoneNumber(contact.phoneNumber)
                val updatedContact = if (normalized.startsWith("+33")) {
                    contact.copy(phoneNumber = PhoneNormalizer.formatForDisplay(normalized))
                } else {
                    contact
                }
                cleanedContacts.add(updatedContact)
            }
        }

        return cleanedContacts
    }
}

// ContactDiffCallback.kt
class ContactDiffCallback(
    private val oldList: List<Contact>,
    private val newList: List<Contact>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        // Comparer par numéro normalisé
        val oldNormalized = PhoneNormalizer.normalizePhoneNumber(oldItem.phoneNumber)
        val newNormalized = PhoneNormalizer.normalizePhoneNumber(newItem.phoneNumber)

        return oldNormalized == newNormalized
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

// Extensions pour faciliter l'utilisation dans votre Fragment/Activity
fun ContactAdapter.showDuplicateCleanupDialog(
    context: android.content.Context,
    onCleanup: (Int) -> Unit
) {
    val duplicates = findDuplicates()

    if (duplicates.isNotEmpty()) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Doublons détectés")
            .setMessage("${duplicates.size} groupes de contacts en doublon trouvés.\n\nVoulez-vous les fusionner automatiquement ?")
            .setPositiveButton("Fusionner") { _, _ ->
                val removedCount = cleanDuplicates()
                onCleanup(removedCount)
            }
            .setNegativeButton("Annuler", null)
            .show()
    } else {
        android.widget.Toast.makeText(context, "Aucun doublon détecté", android.widget.Toast.LENGTH_SHORT).show()
    }
}