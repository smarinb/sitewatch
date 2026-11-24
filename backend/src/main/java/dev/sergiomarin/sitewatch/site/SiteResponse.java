package dev.sergiomarin.sitewatch.site;

import java.time.Instant;

public record SiteResponse(Long id, String name, String url, int checkIntervalMinutes, boolean active,
        SiteStatus status, Instant lastCheckedAt, Instant createdAt) {

    static SiteResponse from(Site site) {
        return new SiteResponse(site.getId(), site.getName(), site.getUrl(), site.getCheckIntervalMinutes(),
                site.isActive(), site.getStatus(), site.getLastCheckedAt(), site.getCreatedAt());
    }
}
