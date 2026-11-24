package dev.sergiomarin.sitewatch.site;

import java.time.Instant;

/**
 * What the alerting needs to remember about a site between checks.
 *
 * @param status              confirmed status
 * @param consecutiveFailures failed checks in a row
 * @param downSince           when the current outage was confirmed
 * @param sslAlertedExpiry    certificate expiry already warned about, so each certificate warns once
 */
public record MonitorState(SiteStatus status, int consecutiveFailures, Instant downSince, Instant sslAlertedExpiry) {

    public static final MonitorState INITIAL = new MonitorState(SiteStatus.UNKNOWN, 0, null, null);
}
