// SPDX-License-Identifier: GPL-3.0-only
package fr.bonobo.stopdemarchage;

import java.time.OffsetDateTime;

final class CommunityPolicy {
    static final long THRESHOLD = 10;
    static long expiry(String iso) {
        return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
    }
    static boolean eligible(String number, long reports, long expires, long now) {
        return FilterEngine.fullNumber(FilterEngine.normalize(number)) && reports >= THRESHOLD && expires > now;
    }
}
