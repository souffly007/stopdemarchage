// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) { data = context.getSharedPreferences("interceptor_v1", Context.MODE_PRIVATE); }
    boolean enabled() { return data.getBoolean("enabled", true); }
    boolean contactsOnly() { return data.getBoolean("contacts_only", false); }
    boolean quiet() { return data.getBoolean("quiet", false); }
    boolean blockForeign() { return data.getBoolean("block_foreign", false); }
    boolean france() { return data.getBoolean("france", true); }
    String theme() { return data.getString("theme", "system"); }
    Set<String> set(String key) { return new HashSet<>(data.getStringSet(key, new HashSet<>())); }
    void flag(String key, boolean value) { data.edit().putBoolean(key, value).apply(); }
    void allow(String number) { move(number, "allowed", "blocked"); }
    void block(String number) { move(number, "blocked", "allowed"); }
    private void move(String n, String to, String from) {
        Set<String> add = set(to), remove = set(from);
        add.add(n); remove.remove(n);
        data.edit().putStringSet(to, add).putStringSet(from, remove).apply();
    }
    void remove(String key, String n) {
        Set<String> values = set(key); values.remove(n);
        data.edit().putStringSet(key, values).apply();
    }
    void prefix(String n) {
        Set<String> values = set("prefixes"); values.add(n);
        data.edit().putStringSet("prefixes", values).apply();
    }
    // Migration only on the first UI launch, never on the call-screening deadline.
    void migrate(Context context) {
        if (data.getBoolean("migrated", false)) { migrateOverseas(); return; }
        SharedPreferences old = context.getSharedPreferences("StopDemarchagePrefs", Context.MODE_PRIVATE);
        Set<String> allowed = set("allowed"), blocked = set("blocked"), prefixes = set("prefixes");
        for (String entry : old.getString("white_list_contacts", "").split(";")) {
            String[] parts = entry.split("\\|", -1);
            String n = FilterEngine.normalize(parts.length == 2 ? parts[1] : "");
            if (FilterEngine.fullNumber(n)) allowed.add(n);
        }
        for (String entry : old.getStringSet("blocked_numbers", new HashSet<>())) {
            String n = FilterEngine.normalize(entry);
            if (FilterEngine.fullNumber(n)) blocked.add(n);
            else if (n.matches("\\+?[0-9]{4,9}")) prefixes.add(n);
        }
        blocked.removeAll(allowed);
        data.edit().putStringSet("allowed", allowed).putStringSet("blocked", blocked)
            .putStringSet("prefixes", prefixes).putBoolean("migrated", true).apply();
        migrateOverseas();
    }
    private void migrateOverseas() {
        if (data.getBoolean("overseas_v1", false)) return;
        Set<String> allowed = new HashSet<>(), blocked = new HashSet<>();
        for (String n : set("allowed")) allowed.add(FilterEngine.migrateStoredNumber(n));
        for (String n : set("blocked")) blocked.add(FilterEngine.migrateStoredNumber(n));
        allowed.remove(""); blocked.remove(""); blocked.removeAll(allowed);
        data.edit().putStringSet("allowed", allowed).putStringSet("blocked", blocked)
            .putBoolean("overseas_v1", true).apply();
    }
}
