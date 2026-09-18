// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import java.util.Set;

/** Pure decision logic: no I/O, Android dependency or network. */
public final class FilterEngine {
    private FilterEngine() {}
    // ARCEP national numbering plan, section 2.3.7 (checked 2026-09-16).
    public static final String[] FR_PREFIXES = {
        "0162", "0163", "0270", "0271", "0377", "0378", "0424", "0425",
        "0568", "0569", "0948", "0949", "05987", "09475", "05988", "09476",
        "05989", "09477", "02688", "02689", "09478", "09479"
    };
    private static String countryForNational(String n) {
        if (n.startsWith("05987") || n.startsWith("09475")) return "590";
        if (n.startsWith("05988") || n.startsWith("09476")) return "594";
        if (n.startsWith("05989") || n.startsWith("09477")) return "596";
        if (n.startsWith("02688") || n.startsWith("02689") || n.startsWith("09478") || n.startsWith("09479")) return "262";
        return "33";
    }
    public static String normalize(String raw) {
        if (raw == null) return "";
        String n = raw.replaceAll("[\\s().-]", "");
        if (n.startsWith("00")) n = "+" + n.substring(2);
        // These NPV national roots unambiguously identify their territory.
        if (n.matches("0[1-9][0-9]{8}")) return "+" + countryForNational(n) + n.substring(1);
        if (n.matches("33[1-9][0-9]{8}")) return "+" + n;
        return n.matches("\\+?[0-9]{2,15}") ? n : "";
    }
    public static boolean fullNumber(String n) {
        for (String country : new String[]{"33", "590", "594", "596", "262"}) {
            if (n.startsWith("+" + country)) return n.matches("\\+" + country + "[1-9][0-9]{8}");
        }
        return n.matches("\\+[1-9][0-9]{6,14}");
    }
    /** Country of the presented number, not the caller's physical location. */
    private static boolean frenchCountryCode(String n) {
        for (String code : new String[]{"33", "262", "508", "590", "594", "596", "681", "687", "689"}) {
            if (n.startsWith("+" + code)) return true;
        }
        return false;
    }
    public static String nationalNumber(String n) {
        for (String country : new String[]{"33", "590", "594", "596", "262"}) {
            if (n.matches("\\+" + country + "[1-9][0-9]{8}")) return "0" + n.substring(country.length() + 1);
        }
        return n;
    }
    /** Repair only saved V1.0 NPV rules previously misclassified as +33. */
    public static String migrateStoredNumber(String raw) {
        String n = normalize(raw);
        if (n.matches("\\+33[1-9][0-9]{8}")) return normalize("0" + n.substring(3));
        return n;
    }
    public static String reason(String raw, boolean enabled, boolean france,
                                Set<String> allowed, Set<String> blocked, Set<String> prefixes) {
        return reason(raw, enabled, france, allowed, blocked, prefixes, false);
    }
    public static String reason(String raw, boolean enabled, boolean france,
                                Set<String> allowed, Set<String> blocked, Set<String> prefixes,
                                boolean contactsOnly) {
        return reason(raw, enabled, france, allowed, blocked, prefixes, contactsOnly, false);
    }
    public static String reason(String raw, boolean enabled, boolean france,
                                Set<String> allowed, Set<String> blocked, Set<String> prefixes,
                                boolean contactsOnly, boolean communityBlocked) {
        return reason(raw, enabled, france, allowed, blocked, prefixes, contactsOnly, communityBlocked, false);
    }
    public static String reason(String raw, boolean enabled, boolean france,
                                Set<String> allowed, Set<String> blocked, Set<String> prefixes,
                                boolean contactsOnly, boolean communityBlocked, boolean blockForeign) {
        if (!enabled) return null;
        String n = normalize(raw);
        if (!fullNumber(n) || allowed.contains(n)) return null;
        if (blocked.contains(n)) return "Numéro bloqué manuellement";
        if (communityBlocked) return "Communauté PhoneZen : au moins 10 signalements";
        if (blockForeign && !frenchCountryCode(n)) return "Appel hors France : indicatif étranger";
        String local = nationalNumber(n);
        for (String p : prefixes) {
            if (n.startsWith(p) || local.startsWith(p)) return "Préfixe personnel : " + p;
        }
        if (france) {
            for (String p : FR_PREFIXES) {
                // Both the national root AND the territory must match.
                String internationalPrefix = "+" + countryForNational(p) + p.substring(1);
                if (n.startsWith(internationalPrefix)) return "Filtre France : " + p;
            }
        }
        return contactsOnly ? "Mode strict : hors contacts et autorisations" : null;
    }
}
