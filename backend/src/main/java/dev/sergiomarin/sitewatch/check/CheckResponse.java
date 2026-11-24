package dev.sergiomarin.sitewatch.check;

import java.time.Instant;

public record CheckResponse(Instant checkedAt, boolean up, Integer httpStatus, Integer responseTimeMs,
        Instant sslExpiresAt, String wpVersion, String error) {

    static CheckResponse from(SiteCheck check) {
        return new CheckResponse(check.getCheckedAt(), check.isUp(), check.getHttpStatus(),
                check.getResponseTimeMs(), check.getSslExpiresAt(), check.getWpVersion(), check.getError());
    }
}
