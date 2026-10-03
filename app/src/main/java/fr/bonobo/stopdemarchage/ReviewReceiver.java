// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reçoit « Spam » / « Légitime » depuis la notification. Non exporté ; aucun accès réseau. */
public final class ReviewReceiver extends BroadcastReceiver {
    static final String ACTION_SPAM = "fr.bonobo.stopdemarchage.REVIEW_SPAM";
    static final String ACTION_LEGIT = "fr.bonobo.stopdemarchage.REVIEW_LEGIT";
    static final String EXTRA_NUMBER = "number";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String number = FilterEngine.normalize(intent.getStringExtra(EXTRA_NUMBER));
        if (action == null || !FilterEngine.fullNumber(number)) return;
        Prefs prefs = new Prefs(context);
        if (ACTION_SPAM.equals(action)) prefs.block(number);
        else if (ACTION_LEGIT.equals(action)) prefs.allow(number);
        else return;
        Review.cancel(context, number);
    }
}
