// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Platform widgets keep the binary small. All pages share one theme and safe-area layout. */
public final class MainActivity extends Activity {
    private Prefs prefs;
    private LinearLayout root, body, nav;
    private TextView heading;
    private int tab = 0, bg, surface, ink, muted, accent, tint;
    private boolean dark, receiverRegistered, backRegistered;
    private android.window.OnBackInvokedCallback backCallback;
    private String listKey;
    private int generation = 0;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (tab != 2) render(); }
    };

    @Override public void onCreate(Bundle saved) {
        prefs = new Prefs(this);
        palette();
        setTheme("cyan".equals(prefs.theme()) ? R.style.AppCyan : dark ? R.style.AppDark : R.style.AppLight);
        super.onCreate(saved);
        prefs.migrate(this);
        CommunityJob.schedule(this);
        if (saved != null) { tab = saved.getInt("tab"); listKey = saved.getString("list"); }
        root = column(); root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        heading = text("", 26, ink, true); heading.setPadding(dp(24), dp(20), dp(24), dp(16));
        root.addView(heading);
        body = column(); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this); nav.setPadding(dp(8), dp(6), dp(8), dp(6)); nav.setBackgroundColor(surface);
        root.addView(nav);
        // Install the decor before configuring the window (including on Xiaomi/HyperOS).
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        root.post(() -> {
            if (!isDestroyed()) {
                applySystemBarAppearance();
                root.requestApplyInsets();
            }
        });
        render();
    }
    private void applySystemBarAppearance() {
        if (root == null || !root.isAttachedToWindow()) return;
        if (Build.VERSION.SDK_INT >= 30) {
            // A View returns null when no controller is available. Do not call
            // Window.getInsetsController(): some implementations dereference a null decor.
            WindowInsetsController controller = root.getWindowInsetsController();
            if (controller != null) {
                int bars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : bars, bars);
            }
        } else {
            View decor = getWindow().getDecorView();
            int bars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility((decor.getSystemUiVisibility() & ~bars) | (dark ? 0 : bars));
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applySystemBarAppearance();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(History.CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            receiverRegistered = true;
        }
        render(); // Recheck role after returning from system settings.
    }
    @Override protected void onPause() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        super.onPause();
    }
    @Override protected void onDestroy() { generation++; io.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab); out.putString("list", listKey); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (listKey != null) { listKey = null; render(); }
        else if (tab != 0) { tab = 0; render(); }
        else super.onBackPressed();
    }
    private void palette() {
        String theme = prefs.theme();
        dark = "dark".equals(theme) || "cyan".equals(theme) || ("system".equals(theme)
            && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        boolean cyan = "cyan".equals(theme);
        bg = Color.parseColor(dark ? cyan ? "#091723" : "#10141C" : "#F4F7FC");
        surface = Color.parseColor(dark ? cyan ? "#112736" : "#1D2430" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#F0F5FC" : "#16253A");
        muted = Color.parseColor(dark ? "#B3C2D4" : "#52647C");
        accent = Color.parseColor(cyan ? "#32D5EB" : dark ? "#9ABEFF" : "#246CDA");
        tint = Color.parseColor(dark ? cyan ? "#163C4B" : "#293D5D" : "#E5EFFE");
    }
    private boolean hasRole() {
        RoleManager manager = getSystemService(RoleManager.class);
        return manager != null && manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            && manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }
    private void activate() {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            info("Filtrage indisponible", "Ce téléphone ne propose pas le rôle Android de filtrage des appels."); return;
        }
        try { startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 42); }
        catch (android.content.ActivityNotFoundException e) { info("Activation", "Le sélecteur de filtrage est indisponible sur ce téléphone."); }
    }
    private void render() {
        if (body == null || isFinishing()) return;
        generation++;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean interceptBack = listKey != null || tab != 0;
            if (interceptBack && !backRegistered) {
                backCallback = () -> { if (listKey != null) listKey = null; else tab = 0; render(); };
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
                backRegistered = true;
            } else if (!interceptBack && backRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
                backRegistered = false;
            }
        }
        body.removeAllViews(); nav.removeAllViews();
        String[] tabs = {"Protection", "Journal", "Réglages"};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            Button b = button(tabs[i], () -> { tab = target; listKey = null; render(); }, i == tab);
            b.setTextSize(13); b.setPadding(dp(4), dp(8), dp(4), dp(8));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(b, p); b.setSelected(i == tab);
        }
        heading.setText(listKey == null ? (tab == 0 ? "Stop Démarchage" : tabs[tab]) : listTitle(listKey));
        if (listKey != null) { numbers(); return; }
        if (tab == 0) home(); else if (tab == 1) journal(); else settings();
    }
    private void home() {
        LinearLayout page = scrollPage();
        TextView intro = text("Votre tranquillité, simplement.", 16, muted, false); page.addView(intro);
        LinearLayout status = card(page);
        boolean active = hasRole() && prefs.enabled();
        TextView badge = text(active ? "●  PROTECTION ACTIVE" : hasRole() ? "○  PROTECTION EN PAUSE" : "○  ACTIVATION NÉCESSAIRE", 13, accent, true);
        status.addView(badge);
        TextView title = text(active ? "Gardez l’esprit tranquille." : "À vous de choisir qui passe.", 27, ink, true);
        title.setPadding(0, dp(12), 0, dp(8)); status.addView(title);
        status.addView(text("Les appels indésirables sont filtrés sur votre téléphone. Votre application Téléphone habituelle reste en place.", 15, muted, false));
        if (!hasRole()) status.addView(button("Activer le filtrage", this::activate, true));
        else toggle(status, "Protection des appels", prefs.enabled(), value -> { prefs.flag("enabled", value); render(); });
        if (prefs.contactsOnly() && prefs.enabled()) status.addView(text("MODE STRICT : seuls les contacts et vos numéros autorisés passent.", 15, accent, true));
        LinearLayout rules = card(page);
        rules.addView(text("Filtrage", 19, ink, true));
        toggle(rules, "Filtre France et outre-mer", prefs.france(), value -> prefs.flag("france", value));
        rules.addView(text("Couvre les 22 préfixes sélectionnés de métropole et d’outre-mer. Les numéros autorisés ont toujours priorité.", 14, muted, false));
        rules.addView(button("Voir les préfixes", () -> info("Préfixes France", "Métropole : 0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949\n\nGuadeloupe, Saint-Martin, Saint-Barthélemy (+590) : 05987, 09475\n\nGuyane (+594) : 05988, 09476\n\nMartinique (+596) : 05989, 09477\n\nLa Réunion (+262) : 02688, 09479\n\nMayotte (+262) : 02689, 09478" + "\n\nUn préfixe ne garantit pas qu’un appel est indésirable. Autorisez un numéro depuis le journal en cas d’erreur."), false));
        toggle(rules, "Bloquer les appels hors France", prefs.blockForeign(), value -> prefs.flag("block_foreign", value));
        rules.addView(text("Bloque les indicatifs étrangers (ex. +39). Métropole et outre-mer sont exclus de cette règle. Vos contacts et numéros autorisés passent toujours. L’indicatif ne révèle pas la position réelle de l’appelant.", 14, muted, false));
        LinearLayout stats = card(page);
        TextView count = text("Chargement du journal…", 20, ink, true); stats.addView(count);
        stats.addView(text("Historique local limité aux 1 000 derniers blocages.", 14, muted, false));
        stats.addView(button("Ouvrir le journal", () -> { tab = 1; render(); }, false));
        loadHistory(rows -> {
            Calendar today = Calendar.getInstance(); today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
            int daily = 0; for (History.Entry row : rows) if (row.time >= today.getTimeInMillis()) daily++;
            count.setText(daily + " aujourd’hui  ·  " + rows.size() + " conservés");
        });
        LinearLayout lists = card(page);
        lists.addView(text("Vos exceptions", 19, ink, true));
        lists.addView(button("Numéros autorisés  ·  " + prefs.set("allowed").size(), () -> openList("allowed"), false));
        lists.addView(button("Numéros bloqués  ·  " + prefs.set("blocked").size(), () -> openList("blocked"), false));
        page.addView(text("Les contacts enregistrés restent autorisés par Android. Aucun accès à votre carnet d’adresses n’est demandé.", 13, muted, false));
    }
    private void journal() {
        LinearLayout tools = column(); tools.setPadding(dp(20), 0, dp(20), dp(8)); body.addView(tools);
        tools.addView(text("Touchez un appel pour autoriser le numéro.", 15, muted, false));
        tools.addView(button("Vider le journal", () -> new AlertDialog.Builder(this)
            .setTitle("Effacer le journal ?").setMessage("Les règles de blocage et les numéros autorisés seront conservés.")
            .setNegativeButton("Annuler", null).setPositiveButton("Effacer", (d, w) -> mutateHistory()).show(), false));
        TextView empty = text("Chargement…", 17, muted, false); empty.setGravity(Gravity.CENTER); body.addView(empty);
        loadHistory(rows -> {
            body.removeView(empty);
            if (rows.isEmpty()) { empty.setText("Aucun appel bloqué\nLes prochains blocages apparaîtront ici."); body.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1)); return; }
            ListView list = listView();
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return rows.size(); }
                @Override public Object getItem(int p) { return rows.get(p); }
                @Override public long getItemId(int p) { return rows.get(p).id; }
                @Override public View getView(int p, View old, ViewGroup parent) {
                    History.Entry item = rows.get(p);
                    LinearLayout row = row(old);
                    ((TextView) row.getChildAt(0)).setText(display(item.number));
                    ((TextView) row.getChildAt(1)).setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(item.time)) + "\n" + item.reason
                        + (prefs.set("allowed").contains(item.number) ? "\n✓ Désormais autorisé" : "\nAutoriser ce numéro →"));
                    return row;
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> {
                History.Entry item = rows.get(position);
                boolean allowed = prefs.set("allowed").contains(item.number);
                new AlertDialog.Builder(this).setTitle(display(item.number))
                    .setMessage(allowed ? "Ce numéro est déjà autorisé. Les anciens blocages restent dans le journal." : "Autoriser les prochains appels de ce numéro, même si son préfixe est filtré ?")
                    .setNegativeButton("Fermer", null)
                    .setPositiveButton(allowed ? "Gérer les autorisations" : "Débloquer / autoriser", (d, w) -> {
                        if (allowed) openList("allowed"); else { prefs.allow(item.number); render(); toast("Numéro autorisé"); }
                    }).show();
            });
            body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        });
    }
    private void settings() {
        LinearLayout page = scrollPage();
        LinearLayout appearance = card(page); appearance.addView(text("Apparence", 20, ink, true));
        String[] labels = {"Système", "Clair", "Sombre", "CyanogenMod"};
        String[] values = {"system", "light", "dark", "cyan"};
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton radio = new RadioButton(this); radio.setId(View.generateViewId()); radio.setText(labels[i]);
            radio.setTextColor(ink); radio.setTextSize(16); radio.setMinHeight(dp(52)); radio.setButtonTintList(ColorStateList.valueOf(accent));
            group.addView(radio); radio.setChecked(values[i].equals(prefs.theme()));
            String value = values[i]; radio.setOnClickListener(v -> {
                if (!value.equals(prefs.theme())) { prefs.data.edit().putString("theme", value).apply(); recreate(); }
            });
        }
        appearance.addView(group);
        LinearLayout community = card(page);
        community.addView(text("Communauté PhoneZen", 20, ink, true));
        community.addView(text("Liste partagée avec PhoneZen : au moins 10 signalements, non expirés. Téléchargement via Internet ; vérification des appels hors ligne. Aucun contact ni journal n’est envoyé.", 14, muted, false));
        boolean communityEnabled = prefs.data.getBoolean("community_enabled", false);
        if (!Community.configured()) {
            community.addView(text("Configuration manquante : renseigner supabase_url et supabase_publishable_key dans local.properties, puis recompiler.", 14, muted, false));
        } else {
            toggle(community, "Activer le filtre communautaire", communityEnabled, value -> {
                prefs.flag("community_enabled", value); CommunityJob.schedule(this);
                render(); if (value) syncCommunity();
            });
            community.addView(text(Community.count(this) + " numéros actifs en cache", 16, ink, true));
            long synced = prefs.data.getLong("community_sync", 0);
            community.addView(text(synced == 0 ? "Aucune synchronisation réussie" : "Dernière mise à jour : " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(synced)), 14, muted, false));
            String error = prefs.data.getString("community_error", "");
            if (!error.isEmpty()) community.addView(text("Dernière tentative : " + error, 14, muted, false));
            if (communityEnabled) community.addView(button("Synchroniser maintenant", this::syncCommunity, false));
            community.addView(text("Mise à jour quotidienne planifiée par Android, selon le réseau et la batterie. Les numéros autorisés restent prioritaires.", 14, muted, false));
        }
        LinearLayout filtering = card(page);
        filtering.addView(text("Options de filtrage", 20, ink, true));
        filtering.addView(text("Mode strict : les appels de numéros identifiés hors contacts sont refusés, sauf ceux que vous avez autorisés.", 15, muted, false));
        filtering.addView(button(prefs.contactsOnly() ? "Mode strict : activé" : "Mode strict : désactivé", () -> {
            if (prefs.contactsOnly()) { prefs.flag("contacts_only", false); render(); return; }
            new AlertDialog.Builder(this).setTitle("Activer le mode strict ?")
                .setMessage("Un livreur, un professionnel de santé ou un proche dont le numéro n’est pas enregistré pourra être bloqué. Ajoutez les numéros utiles aux contacts ou aux numéros autorisés.\n\nLa pause générale désactive aussi ce mode. Les appels masqués restent gérés par l’application Téléphone.")
                .setNegativeButton("Annuler", null).setPositiveButton("Activer", (d, w) -> { prefs.flag("contacts_only", true); render(); }).show();
        }, false));
        toggle(filtering, "Masquer la notification de rejet", prefs.quiet(), value -> prefs.flag("quiet", value));
        filtering.addView(text("Demande à Android de ne pas afficher sa notification d’appel rejeté. Les blocages restent dans le journal de Stop Démarchage. Le rendu dépend de l’application Téléphone.", 14, muted, false));
        filtering.addView(button("Tester un numéro", this::testNumber, false));
        LinearLayout manage = card(page); manage.addView(text("Règles personnelles", 20, ink, true));
        manage.addView(button("Numéros autorisés", () -> openList("allowed"), false));
        manage.addView(button("Numéros bloqués", () -> openList("blocked"), false));
        manage.addView(button("Préfixes personnels", () -> openList("prefixes"), false));
        LinearLayout about = card(page); about.addView(text("À propos", 20, ink, true));
        TextView signature = text("Créée par amour pour votre tranquillité\npar souffly007 (Franck R.-F.)", 18, accent, true);
        signature.setPadding(0, dp(14), 0, dp(14)); about.addView(signature);
        about.addView(text("Logiciel libre • GNU GPL v3", 15, ink, true));
        about.addView(button("Lire la licence GPL v3", () -> showLicense("GPL-3.0.txt", "Licence GNU GPL v3"), false));
        about.addView(button("Crédits et licences tierces", () -> showLicense("THIRD_PARTY_NOTICES.txt", "Crédits et licences tierces"), false));
        about.addView(text("Version 1.0 • bêta 7\n\nPas de compte, pas de publicité. La communauté utilise Internet uniquement pour télécharger les signalements. Le journal, les contacts et les règles personnelles ne sont pas envoyés. Aucun service ne tourne en permanence.", 15, muted, false));
        about.addView(button("Fonctionnement et limites", () -> info("À savoir", "• Android 10 minimum.\n\n• Choisissez Stop Démarchage comme application de filtrage / identification des appels. Un seul filtre peut être sélectionné à la fois.\n\n• Les contacts enregistrés ne sont pas transmis au filtre et restent autorisés, même s’ils figurent dans vos règles.\n\n• Android ne transmet pas les numéros masqués à ce service : utilisez le réglage de votre application Téléphone pour les refuser.\n\n• Le journal contient uniquement les appels rejetés par Stop Démarchage. Autoriser un numéro ne supprime pas son historique.\n\n• Les appels WhatsApp et autres appels d’applications ne sont pas filtrés.\n\n• Pour les numéros étrangers, saisissez le préfixe international + suivi de l’indicatif."), false));
        about.addView(button("Applications par défaut", () -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
            catch (android.content.ActivityNotFoundException e) { info("Réglages Android", "Ouvrez les réglages Android, puis Applications et Applications par défaut."); }
        }, false));
    }
    private boolean communitySyncRunning;
    private void syncCommunity() {
        if (communitySyncRunning) { toast("Synchronisation déjà en cours"); return; }
        communitySyncRunning = true; toast("Synchronisation en cours…");
        Context app = getApplicationContext();
        io.execute(() -> {
            String result = Community.sync(app);
            runOnUiThread(() -> {
                communitySyncRunning = false;
                if (!isDestroyed()) { render(); info("Communauté PhoneZen", result); }
            });
        });
    }
    private void testNumber() {
        EditText field = new EditText(this); field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_PHONE); field.setHint("Numéro complet, ex. +33…");
        LinearLayout box = column(); box.setPadding(dp(24), dp(12), dp(24), 0); box.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Tester un numéro")
            .setView(box).setNegativeButton("Fermer", null).setPositiveButton("Vérifier", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String number = FilterEngine.normalize(field.getText().toString());
            if (!FilterEngine.fullNumber(number)) { field.setError("Saisissez un numéro complet avec son indicatif."); return; }
            String reason = FilterEngine.reason(number, prefs.enabled(), prefs.france(), prefs.set("allowed"), prefs.set("blocked"), prefs.set("prefixes"), prefs.contactsOnly(), Community.blocked(this, number), prefs.blockForeign());
            String result = !prefs.enabled() ? "Protection en pause : l’appel passerait."
                : reason != null ? "Le filtre rejetterait ce numéro.\nMotif : " + reason
                : prefs.set("allowed").contains(number) ? "Autorisation personnelle prioritaire : l’appel passerait."
                : "Aucune règle ne bloque ce numéro.";
            info("Résultat du test", result + (hasRole() ? "" : "\n\nAttention : Stop Démarchage n’est pas actuellement le filtre Android sélectionné.")
                + "\n\nSimulation pour un numéro hors contacts, sans appel ni ajout au journal. Un contact enregistré passe directement par Android.");
        }));
        dialog.show();
    }
    private void showLicense(String asset, String title) {
        try (java.io.InputStream stream = getAssets().open(asset);
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = stream.read(bytes)) != -1) buffer.write(bytes, 0, count);
            TextView content = text(buffer.toString("UTF-8"), 14, ink, false);
            content.setTextIsSelectable(true); content.setPadding(dp(20), dp(16), dp(20), dp(16));
            ScrollView scroll = new ScrollView(this); scroll.addView(content);
            new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fermer", null).show();
        } catch (java.io.IOException error) { info(title, "Le texte n’a pas pu être chargé."); }
    }
    private void openList(String key) { listKey = key; render(); }
    private String listTitle(String key) { return "allowed".equals(key) ? "Numéros autorisés" : "blocked".equals(key) ? "Numéros bloqués" : "Préfixes personnels"; }
    private void numbers() {
        final String key = listKey;
        LinearLayout tools = column(); tools.setPadding(dp(20), 0, dp(20), dp(8)); body.addView(tools);
        tools.addView(button("← Retour", () -> { listKey = null; render(); }, false));
        tools.addView(text("prefixes".equals(key) ? "Une règle de préfixe bloque toute une plage. Les numéros autorisés restent prioritaires." : "allowed".equals(key) ? "Ces numéros passent avant toutes les règles de blocage." : "Blocage exact du numéro. Les contacts enregistrés restent autorisés par Android.", 15, muted, false));
        tools.addView(button("+ Ajouter", () -> addNumber(key), true));
        List<String> values = new ArrayList<>(prefs.set(key)); Collections.sort(values);
        if (values.isEmpty()) { TextView empty = text("Aucune règle personnelle", 17, muted, false); empty.setGravity(Gravity.CENTER); body.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1)); return; }
        ListView list = listView();
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return values.size(); }
            @Override public Object getItem(int p) { return values.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View old, ViewGroup parent) {
                LinearLayout row = row(old);
                ((TextView) row.getChildAt(0)).setText(display(values.get(p)));
                ((TextView) row.getChildAt(1)).setText("blocked".equals(key) ? "Toucher pour débloquer" : "Toucher pour retirer cette règle");
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            String n = values.get(position); boolean unblock = "blocked".equals(key);
            new AlertDialog.Builder(this).setTitle(unblock ? "Débloquer ce numéro ?" : "Retirer cette règle ?")
                .setMessage(display(n) + (unblock ? "\nIl sera ajouté aux numéros autorisés pour éviter un nouveau blocage par préfixe." : "\nLes autres règles de filtrage s’appliqueront à nouveau."))
                .setNegativeButton("Annuler", null).setPositiveButton(unblock ? "Débloquer" : "Retirer", (d, w) -> {
                    if (unblock) prefs.allow(n); else prefs.remove(key, n); render();
                }).show();
        });
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void addNumber(String key) {
        EditText field = new EditText(this); field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_PHONE);
        field.setHint("prefixes".equals(key) ? "Ex. 0162 ou +33162" : "Ex. 06 12 34 56 78 ou +32…");
        field.setTextColor(ink); field.setHintTextColor(muted);
        LinearLayout box = column(); box.setPadding(dp(24), dp(12), dp(24), 0); box.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("prefixes".equals(key) ? "Ajouter un préfixe" : "Ajouter un numéro")
            .setView(box).setNegativeButton("Annuler", null).setPositiveButton("Ajouter", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = FilterEngine.normalize(field.getText().toString()); boolean prefix = "prefixes".equals(key);
            if (prefix ? !n.matches("\\+?[0-9]{4,9}") : !FilterEngine.fullNumber(n)) {
                field.setError(prefix ? "Saisissez un préfixe de 4 à 9 chiffres." : "Saisissez un numéro complet, avec +indicatif pour l’étranger."); return;
            }
            Runnable save = () -> {
                if (prefix) prefs.prefix(n); else if ("allowed".equals(key)) prefs.allow(n); else prefs.block(n);
                dialog.dismiss(); render();
            };
            if (prefix) new AlertDialog.Builder(this).setTitle("Bloquer toute cette plage ?").setMessage("Tous les numéros commençant par " + n + " seront bloqués, sauf vos autorisations et les contacts enregistrés.")
                .setNegativeButton("Annuler", null).setPositiveButton("Bloquer la plage", (confirm, w) -> save.run()).show();
            else save.run();
        }));
        dialog.show();
    }
    private interface RowsReady { void accept(List<History.Entry> rows); }
    private void loadHistory(RowsReady ready) {
        int token = generation;
        io.execute(() -> {
            try (History history = new History(getApplicationContext())) {
                List<History.Entry> rows = history.all();
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) ready.accept(rows); });
            } catch (RuntimeException error) {
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) { ready.accept(Collections.emptyList()); info("Journal indisponible", "Impossible de lire le journal local. Le filtrage reste indépendant du journal."); } });
            }
        });
    }
    private void mutateHistory() {
        io.execute(() -> {
            try (History history = new History(getApplicationContext())) {
                history.clear(); runOnUiThread(() -> { if (!isDestroyed()) { render(); toast("Journal effacé"); } });
            } catch (RuntimeException error) { runOnUiThread(() -> { if (!isDestroyed()) info("Journal", "Impossible d’effacer le journal."); }); }
        });
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setMinHeight(dp(50));
        b.setTextColor(primary ? (dark ? bg : Color.WHITE) : accent);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), shape(primary ? accent : tint, 14), null));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout scrollPage() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = column(); page.setPadding(dp(20), 0, dp(20), dp(24)); scroll.addView(page);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -1)); return page;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(20), dp(20), dp(20), dp(20)); c.setBackground(shape(surface, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(16); p.bottomMargin = dp(6); parent.addView(c, p); return c;
    }
    private interface ToggleChanged { void change(boolean value); }
    private void toggle(LinearLayout parent, String label, boolean checked, ToggleChanged changed) {
        Switch s = new Switch(this); s.setText(label); s.setTextColor(ink); s.setTextSize(16); s.setMinHeight(dp(60)); s.setSwitchPadding(dp(12));
        s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{accent, muted}));
        s.setChecked(checked); s.setOnCheckedChangeListener((view, value) -> changed.change(value)); parent.addView(s);
    }
    private ListView listView() { ListView list = new ListView(this); list.setDivider(null); list.setPadding(dp(20), 0, dp(20), dp(16)); list.setClipToPadding(false); return list; }
    private LinearLayout row(View old) {
        if (old instanceof LinearLayout) return (LinearLayout) old;
        LinearLayout row = column(); row.setPadding(dp(18), dp(16), dp(18), dp(16)); row.setMinimumHeight(dp(96));
        row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22777777), shape(surface, 14), null));
        row.addView(text("", 19, ink, true)); TextView detail = text("", 14, muted, false); detail.setPadding(0, dp(6), 0, 0); row.addView(detail); return row;
    }
    private String display(String n) {
        return n.matches("\\+33[1-9][0-9]{8}") ? ("0" + n.substring(3)).replaceAll("(.{2})(?!$)", "$1 ") : n;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void info(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Compris", null).show(); }
}
