package fr.bonobo.stopdemarchage

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import fr.bonobo.stopdemarchage.adapters.WhiteListAdapter
import fr.bonobo.stopdemarchage.data.WhiteListContact
import fr.bonobo.stopdemarchage.databinding.ActivityWhiteListBinding

class WhiteListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhiteListBinding
    private val whiteListContacts = mutableListOf<WhiteListContact>()
    private lateinit var adapter: WhiteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhiteListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView = binding.recyclerViewWhiteList
        recyclerView.layoutManager = LinearLayoutManager(this)

        whiteListContacts.addAll(loadWhiteListContacts())
        adapter = WhiteListAdapter(whiteListContacts) { index ->
            whiteListContacts.removeAt(index)
            adapter.notifyItemRemoved(index)
            saveWhiteListContacts()
        }
        recyclerView.adapter = adapter

        // Bouton pour ajouter un contact
        findViewById<FloatingActionButton?>(R.id.fabAddContact)?.setOnClickListener {
            pickContact()
        }
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
        startActivityForResult(intent, REQUEST_PICK_CONTACT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_CONTACT && resultCode == RESULT_OK) {
            data?.data?.let { contactUri ->
                val cursor = contentResolver.query(contactUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = it.getString(nameIndex)
                        val number = it.getString(phoneIndex)
                        addToWhiteList(name, number)
                    }
                }
            }
        }
    }

    private fun addToWhiteList(name: String, number: String) {
        val contact = WhiteListContact(name = name, number = number)
        whiteListContacts.add(contact)
        adapter.notifyItemInserted(whiteListContacts.size - 1)
        saveWhiteListContacts()
    }

    private fun loadWhiteListContacts(): List<WhiteListContact> {
        val prefs = getSharedPreferences("StopDemarchagePrefs", MODE_PRIVATE)
        val contactsString = prefs.getString("white_list_contacts", "")
        return if (contactsString.isNullOrEmpty()) {
            emptyList()
        } else {
            contactsString.split(";").mapNotNull { contactData ->
                val parts = contactData.split("|")
                if (parts.size == 2) {
                    WhiteListContact(name = parts[0], number = parts[1])
                } else {
                    null
                }
            }
        }
    }

    private fun saveWhiteListContacts() {
        val contactsString = whiteListContacts.joinToString(";") { "${it.name}|${it.number}" }
        getSharedPreferences("StopDemarchagePrefs", MODE_PRIVATE)
            .edit()
            .putString("white_list_contacts", contactsString)
            .apply()
    }

    companion object {
        private const val REQUEST_PICK_CONTACT = 1001
    }
}