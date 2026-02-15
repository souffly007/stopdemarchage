package fr.bonobo.stopdemarchage.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import fr.bonobo.stopdemarchage.BackupRestoreManager
import fr.bonobo.stopdemarchage.BlockedNumbersActivity
import fr.bonobo.stopdemarchage.CallLogActivity
import fr.bonobo.stopdemarchage.R
import fr.bonobo.stopdemarchage.SmsJournalActivity
import fr.bonobo.stopdemarchage.WhiteListActivity

class ListsFragment : Fragment() {

    private lateinit var backupManager: BackupRestoreManager

    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = backupManager.saveBackupToUri(uri)
            showResultDialog(
                if (result.success) "💾 Sauvegarde" else "❌ Erreur",
                result.message
            )
        } else {
            Toast.makeText(requireContext(), "Sauvegarde annulée", Toast.LENGTH_SHORT).show()
        }
    }

    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showRestoreModeDialog(uri)
        } else {
            Toast.makeText(requireContext(), "Restauration annulée", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backupManager = BackupRestoreManager(requireContext())

        setupListCards(view)
        setupBackupRestoreCards(view)
    }

    private fun setupListCards(view: View) {
        view.findViewById<View>(R.id.cardBlockedNumbers)?.setOnClickListener {
            startActivity(Intent(requireContext(), BlockedNumbersActivity::class.java))
        }

        view.findViewById<View>(R.id.cardCallLog)?.setOnClickListener {
            startActivity(Intent(requireContext(), CallLogActivity::class.java))
        }

        view.findViewById<View>(R.id.cardSmsJournal)?.setOnClickListener {
            startActivity(Intent(requireContext(), SmsJournalActivity::class.java))
        }

        view.findViewById<View>(R.id.cardWhitelist)?.setOnClickListener {
            startActivity(Intent(requireContext(), WhiteListActivity::class.java))
        }
    }

    private fun setupBackupRestoreCards(view: View) {
        view.findViewById<View>(R.id.cardBackup)?.setOnClickListener {
            showBackupDialog()
        }

        view.findViewById<View>(R.id.cardRestore)?.setOnClickListener {
            showRestoreDialog()
        }
    }

    private fun showBackupDialog() {
        val summary = backupManager.getCurrentDataSummary()

        AlertDialog.Builder(requireContext())
            .setTitle("💾 Sauvegarder la liste noire")
            .setMessage(
                "$summary\n\n" +
                        "📁 Le fichier sera sauvegardé au format JSON.\n\n" +
                        "💡 Vous pourrez le partager par email, cloud, " +
                        "ou le garder pour un changement de smartphone.\n\n" +
                        "📌 Utile pour sauvegarder vos numéros +336/+337 " +
                        "ajoutés manuellement."
            )
            .setPositiveButton("Sauvegarder") { _, _ ->
                startBackup()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun startBackup() {
        try {
            val fileName = backupManager.generateBackupFileName()
            createBackupFile.launch(fileName)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "❌ Erreur : ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestoreDialog() {
        val summary = backupManager.getCurrentDataSummary()

        AlertDialog.Builder(requireContext())
            .setTitle("📥 Restaurer la liste noire")
            .setMessage(
                "$summary\n\n" +
                        "📁 Sélectionnez un fichier de sauvegarde JSON " +
                        "créé par Stop Démarchage.\n\n" +
                        "⚠️ Vous pourrez choisir de fusionner ou remplacer " +
                        "votre liste noire actuelle."
            )
            .setPositiveButton("Choisir un fichier") { _, _ ->
                startRestore()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun startRestore() {
        try {
            openBackupFile.launch(arrayOf("application/json", "*/*"))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "❌ Erreur : ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestoreModeDialog(uri: android.net.Uri) {
        val options = arrayOf(
            "🔄 Fusionner (ajouter les nouveaux numéros)",
            "♻️ Remplacer (écraser la liste actuelle)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Mode de restauration")
            .setItems(options) { _, which ->
                val mode = when (which) {
                    0 -> BackupRestoreManager.RestoreMode.MERGE
                    1 -> BackupRestoreManager.RestoreMode.REPLACE
                    else -> BackupRestoreManager.RestoreMode.MERGE
                }

                if (mode == BackupRestoreManager.RestoreMode.REPLACE) {
                    showReplaceConfirmation(uri)
                } else {
                    executeRestore(uri, mode)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showReplaceConfirmation(uri: android.net.Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Confirmation")
            .setMessage(
                "Attention ! Cette action va REMPLACER toute votre " +
                        "liste noire actuelle par celle du fichier de sauvegarde.\n\n" +
                        "Cette action est irréversible.\n\n" +
                        "Continuer ?"
            )
            .setPositiveButton("Remplacer") { _, _ ->
                executeRestore(uri, BackupRestoreManager.RestoreMode.REPLACE)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun executeRestore(uri: android.net.Uri, mode: BackupRestoreManager.RestoreMode) {
        val result = backupManager.restoreFromUri(uri, mode)
        showResultDialog(
            if (result.success) "📥 Restauration" else "❌ Erreur",
            result.message
        )
    }

    private fun showResultDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        fun newInstance() = ListsFragment()
    }
}