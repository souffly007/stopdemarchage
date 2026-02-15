package fr.bonobo.stopdemarchage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.adapters.SmsConversationAdapter
import fr.bonobo.stopdemarchage.data.SmsConversation
import fr.bonobo.stopdemarchage.helpers.SmsHelper

class SmsJournalActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SmsConversationAdapter
    private lateinit var smsAnalyzer: SmsSuspicionAnalyzer
    private lateinit var emptyView: TextView
    private lateinit var statsView: TextView

    private val conversationsList = mutableListOf<SmsConversation>()

    companion object {
        private const val SMS_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_journal)

        // Initialiser l'analyseur SMS avec le pays détecté
        val countryDetector = fr.bonobo.stopdemarchage.utils.CountryDetector(this)
        val detectedCountry = countryDetector.detectCountry()
        smsAnalyzer = SmsSuspicionAnalyzer(this, detectedCountry)

        // Configuration de l'interface
        setupViews()
        setupToolbar()

        // Vérifier et demander les permissions
        if (checkSmsPermission()) {
            loadConversations()
        } else {
            requestSmsPermission()
        }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_sms_journal)
        emptyView = findViewById(R.id.text_empty_journal)
        statsView = findViewById(R.id.text_journal_stats)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SmsConversationAdapter(conversationsList) { conversation ->
            openConversationDetails(conversation)
        }
        recyclerView.adapter = adapter
    }

    private fun setupToolbar() {
        supportActionBar?.hide()
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS),
            SMS_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadConversations()
            } else {
                Toast.makeText(this, "Permission SMS requise", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadConversations() {
        emptyView.text = "📥 Chargement des conversations..."
        emptyView.visibility = View.VISIBLE

        Thread {
            val conversations = SmsHelper.getAllConversations(this, smsAnalyzer)

            runOnUiThread {
                conversationsList.clear()
                conversationsList.addAll(conversations)
                adapter.notifyDataSetChanged()
                updateUI()
            }
        }.start()
    }

    private fun updateUI() {
        if (conversationsList.isEmpty()) {
            emptyView.text = "📭 Aucune conversation"
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            statsView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            statsView.visibility = View.VISIBLE

            val total = conversationsList.size
            val suspicious = conversationsList.count { it.maxSuspicionScore >= 60 }
            val totalMessages = conversationsList.sumOf { it.messageCount }

            statsView.text = "📊 Statistiques\n\n💬 $total conversations\n📨 $totalMessages messages\n🔴 $suspicious suspectes"
        }
    }

    private fun openConversationDetails(conversation: SmsConversation) {
        val intent = Intent(this, SmsConversationDetailActivity::class.java)
        intent.putExtra("PHONE_NUMBER", conversation.address)
        intent.putExtra("CONTACT_NAME", conversation.contactName)
        startActivity(intent)
    }
}