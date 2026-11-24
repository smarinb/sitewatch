package dev.sergiomarin.sitewatch.site;

import java.time.Instant;

public record SiteResponse(Long id, String name, String url, int checkIntervalMinutes, boolean active,
        Instant createdAt) {

    static SiteResponse from(Site site) {
        return new SiteResponse(site.getId(), site.getName(), site.getUrl(), site.getCheckIntervalMinutes(),
                site.isActive(), site.getCreatedAt());
    }
}
