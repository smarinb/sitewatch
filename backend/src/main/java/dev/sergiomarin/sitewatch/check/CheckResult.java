package dev.sergiomarin.sitewatch.check;

import java.time.Instant;

/**
 * Outcome of one check.
 *
 * @param up             the site answered with a status below 400
 * @param httpStatus     final status after redirects, {@code null} if no response arrived
 * @param responseTimeMs time until the response headers arrived
 * @param sslExpiresAt   expiry of the certificate served over HTTPS
 * @param wpVersion      WordPress version, if the site exposes it
 * @param error          why the site is down
 */
public record CheckResult(boolean up, Integer httpStatus, Integer responseTimeMs, Instant sslExpiresAt,
        String wpVersion, String error) {

    static final int MAX_ERROR_LENGTH = 500;

    static CheckResult failure(String error) {
        return new CheckResult(false, null, null, null, null, truncate(error));
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
