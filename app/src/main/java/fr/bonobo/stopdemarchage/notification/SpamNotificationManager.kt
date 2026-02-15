package com.stopdemarche.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.bonobo.stopdemarchage.MainActivity
import fr.bonobo.stopdemarchage.R

class SpamNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "spam_alerts"
        private const val CHANNEL_NAME = "Alertes Spam"
        private const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    /**
     * Crée le canal de notification pour Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour les SMS spam bloqués"
                enableVibration(false)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Affiche une notification pour un SMS spam bloqué
     */
    fun showSpamBlockedNotification(
        sender: String,
        message: String,
        reason: String,
        confidence: Double
    ) {
        // Intent pour ouvrir l'application
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_blocked_list", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Créer la notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block_24dp)
            .setContentTitle("SMS spam bloqué")
            .setContentText("De: $sender")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildString {
                    append("De: $sender\n")
                    append("Raison: $reason\n")
                    append("Confiance: ${(confidence * 100).toInt()}%\n")
                    append("Message: ${message.take(100)}")
                    if (message.length > 100) append("...")
                })
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(context.getColor(R.color.red_500))
            .addAction(
                R.drawable.ic_visibility,
                "Voir détails",
                pendingIntent
            )

        // Afficher la notification
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    /**
     * Affiche une notification de résumé quotidien
     */
    fun showDailySummaryNotification(blockedCount: Int, topReasons: List<String>) {
        if (blockedCount == 0) return

        // Intent pour ouvrir l'application
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_statistics", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Créer le message de résumé
        val summaryText = buildString {
            append("$blockedCount SMS bloqué(s) aujourd'hui\n\n")
            append("Principales raisons:\n")
            topReasons.take(3).forEachIndexed { index, reason ->
                append("${index + 1}. $reason\n")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Résumé quotidien - StopDémarchage")
            .setContentText("$blockedCount SMS bloqués aujourd'hui")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID + 1, builder.build())
        }
    }

    /**
     * Annule toutes les notifications
     */
    fun cancelAll() {
        with(NotificationManagerCompat.from(context)) {
            cancelAll()
        }
    }

    /**
     * Annule une notification spécifique
     */
    fun cancelNotification(notificationId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
    }
}