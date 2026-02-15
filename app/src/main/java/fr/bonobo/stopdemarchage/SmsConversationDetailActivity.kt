package fr.bonobo.stopdemarchage

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.bonobo.stopdemarchage.adapters.SmsMessageAdapter
import fr.bonobo.stopdemarchage.data.SmsMessage
import fr.bonobo.stopdemarchage.helpers.SmsHelper

class SmsConversationDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SmsMessageAdapter
    private lateinit var smsAnalyzer: SmsSuspicionAnalyzer
    private lateinit var emptyView: TextView
    private lateinit var headerView: TextView

    private val messagesList = mutableListOf<SmsMessage>()
    private var phoneNumber: String = ""
    private var contactName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_conversation_detail)

        phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
        contactName = intent.getStringExtra("CONTACT_NAME")

        val countryDetector = fr.bonobo.stopdemarchage.utils.CountryDetector(this)
        val detectedCountry = countryDetector.detectCountry()
        smsAnalyzer = SmsSuspicionAnalyzer(this, detectedCountry)

        setupViews()
        setupToolbar()
        loadMessages()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_messages)
        emptyView = findViewById(R.id.text_empty_messages)
        headerView = findViewById(R.id.text_conversation_header)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SmsMessageAdapter(messagesList) { message ->
            showMessageDetails(message)
        }
        recyclerView.adapter = adapter

        val displayName = contactName ?: phoneNumber
        headerView.text = "Conversation avec\n$displayName"
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = contactName ?: phoneNumber
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadMessages() {
        emptyView.text = "📥 Chargement..."
        emptyView.visibility = View.VISIBLE

        Thread {
            val messages = SmsHelper.getSmsByPhoneNumber(this, phoneNumber, smsAnalyzer)

            runOnUiThread {
                messagesList.clear()
                messagesList.addAll(messages.sortedBy { it.date })
                adapter.notifyDataSetChanged()

                if (messagesList.isEmpty()) {
                    emptyView.text = "📭 Aucun message"
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.scrollToPosition(messagesList.size - 1)
                }
            }
        }.start()
    }

    private fun showMessageDetails(message: SmsMessage) {
        val result = message.suspicionResult
        val riskText = smsAnalyzer.getRiskText(result.risk)
        val riskEmoji = when (result.risk) {
            SuspicionLevel.LOW -> "🟢"
            SuspicionLevel.MEDIUM -> "🟡"
            SuspicionLevel.HIGH -> "🟠"
            SuspicionLevel.CRITICAL -> "🔴"
        }

        val info = StringBuilder()
        info.append("📅 ${message.formattedDate}\n\n")
        info.append("💬 ${message.body}\n\n")
        info.append("$riskEmoji ${result.score}% - $riskText\n\n")

        if (result.detectedWords.isNotEmpty()) {
            info.append("🔍 Mots:\n")
            result.detectedWords.take(5).forEach { info.append("• $it\n") }
            info.append("\n")
        }

        info.append("📋 ${result.explanation}")

        AlertDialog.Builder(this)
            .setTitle("Analyse")
            .setMessage(info.toString())
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Bloquer") { _, _ ->
                blockPhoneNumber(phoneNumber)
            }
            .show()
    }

    private fun blockPhoneNumber(number: String) {
        val prefs = getSharedPreferences("StopDemarchagePrefs", MODE_PRIVATE)
        val blacklist = prefs.getStringSet("blacklist", mutableSetOf()) ?: mutableSetOf()
        blacklist.add(number)
        prefs.edit().putStringSet("blacklist", blacklist).apply()

        Toast.makeText(this, "📵 Numéro bloqué", Toast.LENGTH_SHORT).show()
        finish()
    }
}