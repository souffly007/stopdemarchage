// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

public final class CallScreening extends CallScreeningService {
    @Override public void onScreenCall(Call.Details details) {
        // Outgoing calls must never be rejected or added to this journal.
        if (details.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;
        String number = details.getHandle() == null ? "" : details.getHandle().getSchemeSpecificPart();
        String reason = null;
        boolean quiet = false;
        try {
            Prefs prefs = new Prefs(this);
            reason = FilterEngine.reason(number, prefs.enabled(), prefs.france(),
                prefs.set("allowed"), prefs.set("blocked"), prefs.set("prefixes"), prefs.contactsOnly(), Community.blocked(this, number), prefs.blockForeign());
            quiet = prefs.quiet();
        } catch (RuntimeException error) {
            // Fail open; never write telephone numbers to logcat.
            Log.w("StopDemarchage", "Filtrage indisponible : appel autorisé");
        }
        boolean block = reason != null;
        respondToCall(details, new CallResponse.Builder()
            .setDisallowCall(block).setRejectCall(block)
            .setSkipCallLog(false).setSkipNotification(block && quiet).build());
        // Respond first: SQLite is deliberately outside the decision path.
        if (block) {
            try (History history = new History(this)) {
                history.add(FilterEngine.normalize(number), reason);
                sendBroadcast(new Intent(History.CHANGED).setPackage(getPackageName()));
            } catch (RuntimeException error) {
                Log.w("StopDemarchage", "Journal indisponible");
            }
        }
    }
}
