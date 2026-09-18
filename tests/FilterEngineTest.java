// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import java.util.Set;

/** Run using ./test-filter.sh. No Android SDK or third-party test dependency required. */
public final class FilterEngineTest {
    private static int checks;
    private static void same(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static String decision(String n, boolean enabled, boolean france, Set<String> allowed, Set<String> blocked, Set<String> prefixes) {
        return FilterEngine.reason(n, enabled, france, allowed, blocked, prefixes);
    }
    private static String decide(String n) { return decision(n, true, true, Set.of(), Set.of(), Set.of()); }
    public static void main(String[] args) {
        same("+33162123456", FilterEngine.normalize("01 62 12 34 56"));
        same("+33162123456", FilterEngine.normalize("0033 1 62 12 34 56"));
        same("+33162123456", FilterEngine.normalize("+33 (1) 62-12.34.56"));
        same("+32470123456", FilterEngine.normalize("0032 470 12 34 56"));
        same("", FilterEngine.normalize(null));
        same("", FilterEngine.normalize("anonymous"));
        same("", FilterEngine.normalize("+33162123456;123"));
        same("", FilterEngine.normalize("++33162123456"));
        same("", FilterEngine.normalize("sip:0162123456"));
        same("", FilterEngine.normalize("call0162123456"));
        // Independent fixtures, not derived from the implementation's country mapping.
        String[][] territories = {
            {"33", "0162", "0163", "0270", "0271", "0377", "0378", "0424", "0425", "0568", "0569", "0948", "0949"},
            {"590", "05987", "09475"}, {"594", "05988", "09476"},
            {"596", "05989", "09477"}, {"262", "02688", "02689", "09478", "09479"}
        };
        for (String[] territory : territories) for (int i = 1; i < territory.length; i++) {
            String p = territory[i];
            String local = p + "1234567890".substring(p.length());
            String intl = "+" + territory[0] + local.substring(1);
            same(intl, FilterEngine.normalize(local));
            same(local, FilterEngine.nationalNumber(intl));
            same("Filtre France : " + p, decide(local));
            same("Filtre France : " + p, decide(intl));
            same("Filtre France : " + p, decide("00" + intl.substring(1)));
            same(null, decision(local, true, true, Set.of(intl), Set.of(intl), Set.of(p)));
            same(null, decision(local, false, true, Set.of(), Set.of(intl), Set.of(p)));
            same(null, decision(local, true, false, Set.of(), Set.of(), Set.of()));
            same("Numéro bloqué manuellement", decision(intl, true, false, Set.of(), Set.of(intl), Set.of()));
            same("Préfixe personnel : " + p, decision(intl, true, false, Set.of(), Set.of(), Set.of(p)));
            for (String wrong : new String[]{"33", "590", "594", "596", "262", "44"}) {
                if (!wrong.equals(territory[0])) same(null, decide("+" + wrong + local.substring(1)));
            }
            if (!territory[0].equals("33")) same(intl, FilterEngine.migrateStoredNumber("+33" + local.substring(1)));
        }
        for (String n : new String[]{"0612345678", "0112345678", "0800123456", "0805123456", "+441621234567", "112", "15", "17", "18", "", "private", "0162"}) same(null, decide(n));
        same("Numéro bloqué manuellement", decision("06 12 34 56 78", true, false, Set.of(), Set.of("+33612345678"), Set.of()));
        same(null, decision("0612345679", true, true, Set.of(), Set.of("+33612345678"), Set.of()));
        same(null, decision("+33612345678", true, true, Set.of("+33612345678"), Set.of("+33612345678"), Set.of("06")));
        same("Préfixe personnel : 0612", decision("+33612345678", true, false, Set.of(), Set.of(), Set.of("0612")));
        same("Préfixe personnel : +32470", decision("0032470123456", true, false, Set.of(), Set.of(), Set.of("+32470")));
        same(null, decision("+33612345678", true, false, Set.of(), Set.of(), Set.of("+32470")));
        same(null, decide("+330162123456"));
        same(false, FilterEngine.fullNumber("061234567"));
        same(false, FilterEngine.fullNumber("+3316212"));
        same(false, FilterEngine.fullNumber("+330162123456"));
        same(true, FilterEngine.fullNumber("+33612345678"));
        same(true, FilterEngine.fullNumber("+32470123456"));
        same("+33162123456", FilterEngine.normalize("33162123456"));
        same("+590947512345", FilterEngine.normalize("00 590 94751 2345"));
        same(null, decision("0947512345", true, true, Set.of("+590947512345"), Set.of(), Set.of()));
        same(null, decide("+590590123456"));
        same(null, decide("+262262123456"));
        same(false, FilterEngine.fullNumber("+59094751234"));
        same(false, FilterEngine.fullNumber("+2622688912345"));
        same("+32470123456", FilterEngine.migrateStoredNumber("+32470123456"));
        same("Mode strict : hors contacts et autorisations", FilterEngine.reason("0612345678", true, true, Set.of(), Set.of(), Set.of(), true));
        same(null, FilterEngine.reason("0612345678", true, true, Set.of("+33612345678"), Set.of("+33612345678"), Set.of("06"), true));
        same(null, FilterEngine.reason("0612345678", false, true, Set.of(), Set.of(), Set.of(), true));
        same(null, FilterEngine.reason("", true, true, Set.of(), Set.of(), Set.of(), true));
        same(null, FilterEngine.reason("112", true, true, Set.of(), Set.of(), Set.of(), true));
        same("Filtre France : 0424", FilterEngine.reason("0424119501", true, true, Set.of(), Set.of(), Set.of(), true));
        same("Numéro bloqué manuellement", FilterEngine.reason("0612345678", true, false, Set.of(), Set.of("+33612345678"), Set.of(), true));
        same(null, FilterEngine.reason("0424119501", true, true, Set.of("+33424119501"), Set.of(), Set.of(), true));
        long now = CommunityPolicy.expiry("2026-09-17T10:00:00Z");
        same(now, CommunityPolicy.expiry("2026-09-17T12:00:00+02:00"));
        same(false, CommunityPolicy.eligible("0612345678", 9, now + 1, now));
        same(true, CommunityPolicy.eligible("0612345678", 10, now + 1, now));
        same(false, CommunityPolicy.eligible("0612345678", 100, now, now));
        same(false, CommunityPolicy.eligible("0612345678", 100, now - 1, now));
        same(false, CommunityPolicy.eligible("112", 100, now + 100, now));
        same("Communauté PhoneZen : au moins 10 signalements", FilterEngine.reason("0612345678", true, false, Set.of(), Set.of(), Set.of(), false, true));
        same(null, FilterEngine.reason("0612345678", true, true, Set.of("+33612345678"), Set.of(), Set.of(), true, true));
        same(null, FilterEngine.reason("0612345678", false, true, Set.of(), Set.of(), Set.of(), true, true));
        // Opt-in foreign blocking: country-code boundaries, formats, exceptions and pause.
        String foreignReason = "Appel hors France : indicatif étranger";
        for (String number : new String[]{"+39 06 1234 5678", "0039 06 1234 5678", "+32470123456", "+441234567890", "+16811234567", "+68612345678", "+59171234567"}) {
            same(foreignReason, FilterEngine.reason(number, true, false, Set.of(), Set.of(), Set.of(), false, false, true));
            same(null, FilterEngine.reason(number, true, false, Set.of(), Set.of(), Set.of(), false, false, false));
            same(null, FilterEngine.reason(number, false, true, Set.of(), Set.of(), Set.of(), true, true, true));
            same(null, FilterEngine.reason(number, true, true, Set.of(FilterEngine.normalize(number)), Set.of(), Set.of(), true, true, true));
        }
        for (String number : new String[]{"0612345678", "+33612345678", "0033612345678", "33612345678",
                "+262692123456", "+262639123456", "+508551234", "+590590123456", "+594594123456", "+596596123456", "+681721234", "+687751234", "+68987123456"}) {
            same(null, FilterEngine.reason(number, true, false, Set.of(), Set.of(), Set.of(), false, false, true));
            same(null, FilterEngine.reason(number.replace("+", "00"), true, false, Set.of(), Set.of(), Set.of(), false, false, true));
        }
        for (String number : new String[]{"", "private", "112", "+39", "390612345678", "+330612345678", "+33123", "++390612345678"})
            same(null, FilterEngine.reason(number, true, false, Set.of(), Set.of(), Set.of(), false, false, true));
        same("Filtre France : 0424", FilterEngine.reason("0424119501", true, true, Set.of(), Set.of(), Set.of(), false, false, true));
        same("Numéro bloqué manuellement", FilterEngine.reason("+390612345678", true, false, Set.of(), Set.of("+390612345678"), Set.of(), false, false, true));
        same("Communauté PhoneZen : au moins 10 signalements", FilterEngine.reason("+390612345678", true, false, Set.of(), Set.of(), Set.of(), false, true, true));
        System.out.println(checks + " checks passed");
    }
}
