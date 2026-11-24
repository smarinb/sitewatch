package dev.sergiomarin.sitewatch.check;

import java.time.Instant;

/** The newest check of one site, for dashboards that list many sites at once. */
public record LatestCheckResponse(Long siteId, Instant checkedAt, boolean up, Integer httpStatus,
        Integer responseTimeMs, Instant sslExpiresAt, String wpVersion, String error) {

    static LatestCheckResponse from(SiteCheck check) {
        return new LatestCheckResponse(check.getSiteId(), check.getCheckedAt(), check.isUp(), check.getHttpStatus(),
                check.getResponseTimeMs(), check.getSslExpiresAt(), check.getWpVersion(), check.getError());
    }
}
