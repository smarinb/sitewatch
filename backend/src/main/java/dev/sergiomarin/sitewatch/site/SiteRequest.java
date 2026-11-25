package dev.sergiomarin.sitewatch.site;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.sergiomarin.sitewatch.common.HttpUrl;

/**
 * Body for creating or replacing a site. Optional fields fall back to their
 * defaults: a 5-minute interval and an active site.
 */
public record SiteRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        @Min(5) @Max(1440) Integer checkIntervalMinutes,
        Boolean active) {

    static final int DEFAULT_INTERVAL_MINUTES = 5;

    int intervalOrDefault() {
        return checkIntervalMinutes != null ? checkIntervalMinutes : DEFAULT_INTERVAL_MINUTES;
    }

    boolean activeOrDefault() {
        return active == null || active;
    }
}
