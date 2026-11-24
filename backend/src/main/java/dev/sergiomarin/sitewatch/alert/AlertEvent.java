package dev.sergiomarin.sitewatch.alert;

import java.time.Duration;
import java.time.Instant;

/** Something worth telling the site's owner about. */
public record AlertEvent(Type type, long ownerId, String siteName, String url, Instant at, String error,
        Duration downtime, Instant sslExpiresAt) {

    public enum Type {
        DOWN, RECOVERED, SSL_EXPIRING
    }
}
