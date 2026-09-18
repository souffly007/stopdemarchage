// SPDX-License-Identifier: GPL-3.0-only
package fr.bonobo.stopdemarchage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** Read-only Supabase client. Never called from the network during screening. */
final class Community {
    private static final int MAX_ROWS = 10000;
    static SharedPreferences cache(Context c) { return c.getSharedPreferences("community_v1", Context.MODE_PRIVATE); }
    static boolean configured() { return !BuildConfig.SUPABASE_URL.isEmpty() && !BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty(); }
    static boolean blocked(Context c, String number) {
        return configured() && BuildConfig.SUPABASE_URL.equals(cache(c).getString("__source", ""))
            && new Prefs(c).data.getBoolean("community_enabled", false)
            && cache(c).getLong(FilterEngine.normalize(number), 0) > System.currentTimeMillis();
    }
    static int count(Context c) {
        if (!BuildConfig.SUPABASE_URL.equals(cache(c).getString("__source", ""))) return 0;
        int count = 0; long now = System.currentTimeMillis();
        for (Object v : cache(c).getAll().values()) if (v instanceof Long && (Long) v > now) count++;
        return count;
    }
    static synchronized String sync(Context context) {
        Prefs prefs = new Prefs(context);
        if (!configured()) return "Configuration Supabase manquante";
        if (!prefs.data.getBoolean("community_enabled", false)) return "Filtre communautaire désactivé";
        try {
            URL base = new URL(BuildConfig.SUPABASE_URL);
            if (!"https".equals(base.getProtocol()) || base.getUserInfo() != null || base.getQuery() != null
                || base.getRef() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath())))
                throw new IOException("L’URL doit être une origine HTTPS");
            String key = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
            boolean legacy = !key.startsWith("sb_publishable_");
            if (legacy) {
                String[] parts = key.split("\\.");
                if (parts.length != 3 || !"anon".equals(new JSONObject(new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8)).optString("role")))
                    throw new IOException("Utiliser une clé publique publishable ou anon");
            }
            Map<String, Long> snapshot = new HashMap<>();
            String after = null; int received = 0;
            long started = android.os.SystemClock.elapsedRealtime();
            String nowIso = Instant.now().toString();
            while (true) {
                if (Thread.currentThread().isInterrupted() || !prefs.data.getBoolean("community_enabled", false)) throw new IOException("Synchronisation annulée");
                if (android.os.SystemClock.elapsedRealtime() - started > 120000) throw new IOException("Synchronisation trop longue");
                Uri.Builder uri = Uri.parse(base.toString()).buildUpon().appendPath("rest").appendPath("v1").appendPath("reported_numbers")
                    .appendQueryParameter("select", "number,reports,expires_at")
                    .appendQueryParameter("reports", "gte.10")
                    .appendQueryParameter("expires_at", "gt." + nowIso)
                    .appendQueryParameter("order", "number.asc").appendQueryParameter("limit", "200");
                if (after != null) uri.appendQueryParameter("number", "gt." + after);
                HttpsURLConnection connection = (HttpsURLConnection) new URL(uri.build().toString()).openConnection();
                JSONArray rows;
                try {
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                    connection.setRequestProperty("apikey", key);
                    if (legacy) connection.setRequestProperty("Authorization", "Bearer " + key);
                    connection.setRequestProperty("Accept", "application/json");
                    int status = connection.getResponseCode();
                    if (status != 200 && status != 206) throw new IOException("Supabase HTTP " + status);
                    try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096]; int read;
                        while ((read = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted() || android.os.SystemClock.elapsedRealtime() - started > 120000) throw new IOException("Synchronisation interrompue");
                            if (out.size() + read > 1024 * 1024) throw new IOException("Réponse trop volumineuse");
                            out.write(buffer, 0, read);
                        }
                        rows = new JSONArray(out.toString("UTF-8"));
                    }
                } finally { connection.disconnect(); }
                if (rows.length() == 0) break;
                received += rows.length();
                if (received > MAX_ROWS) throw new IOException("Plus de 10 000 entrées : cache précédent conservé");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String raw = row.getString("number");
                    if (after != null && raw.compareTo(after) <= 0) throw new IOException("Ordre des données invalide");
                    after = raw;
                    long reports = row.getLong("reports");
                    long expiry = CommunityPolicy.expiry(row.getString("expires_at"));
                    String number = FilterEngine.normalize(raw);
                    if (CommunityPolicy.eligible(number, reports, expiry, System.currentTimeMillis()))
                        snapshot.merge(number, expiry, Math::max);
                }
            }
            if (Thread.currentThread().isInterrupted() || !prefs.data.getBoolean("community_enabled", false)) throw new IOException("Synchronisation annulée");
            SharedPreferences.Editor edit = cache(context).edit().clear().putString("__source", BuildConfig.SUPABASE_URL);
            for (Map.Entry<String, Long> row : snapshot.entrySet()) edit.putLong(row.getKey(), row.getValue());
            if (!edit.commit()) throw new IOException("Impossible d’enregistrer le cache");
            prefs.data.edit().putLong("community_sync", System.currentTimeMillis()).putString("community_error", "").apply();
            return snapshot.size() + " numéros actifs téléchargés";
        } catch (Exception error) {
            String message = error instanceof IOException ? error.getMessage() : "Données ou configuration Supabase invalides";
            // No remote response, API key, number or server detail in logcat.
            prefs.data.edit().putString("community_error", message).apply();
            return message + ". Le dernier cache reste utilisable jusqu’à expiration des entrées.";
        }
    }
}
