// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

/** Notification « spam ou légitime ? » pour un mobile inconnu. Local uniquement, aucune requête réseau. */
final class Review {
    static final String CHANNEL = "review_v1";
    private static final long TIMEOUT_MS = 60L * 60 * 1000;
    private Review() {}

    static int id(String number) { return number.hashCode() & 0x7fffffff; }

    static void post(Context c, String number) {
        if (!FilterEngine.fullNumber(number)) return;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Numéros à confirmer", NotificationManager.IMPORTANCE_HIGH));
        String shown = FilterEngine.nationalNumber(number).replaceAll("(.{2})(?!$)", "$1 ");
        Notification.Action spam = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notify),
            "Spam", action(c, ReviewReceiver.ACTION_SPAM, number)).build();
        Notification.Action legit = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notify),
            "Légitime", action(c, ReviewReceiver.ACTION_LEGIT, number)).build();
        manager.notify(id(number), new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Mobile inconnu : " + shown)
            .setContentText("Démarchage ou appel légitime ?")
            .setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(TIMEOUT_MS)
            .addAction(spam).addAction(legit).build());
    }

    static void cancel(Context c, String number) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(id(number));
    }

    private static PendingIntent action(Context c, String action, String number) {
        // Intent explicite + données distinctes : un PendingIntent par numéro et par action.
        Intent intent = new Intent(action, Uri.fromParts("tel", number, null), c, ReviewReceiver.class)
            .putExtra(ReviewReceiver.EXTRA_NUMBER, number);
        return PendingIntent.getBroadcast(c, id(number), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
