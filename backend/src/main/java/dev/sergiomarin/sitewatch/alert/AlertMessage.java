package dev.sergiomarin.sitewatch.alert;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Plain-text alert, shared by every channel. */
public record AlertMessage(String subject, String body) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    static AlertMessage of(AlertEvent event) {
        String site = event.siteName() + " (" + event.url() + ")";
        return switch (event.type()) {
            case DOWN -> new AlertMessage("🔴 DOWN: " + event.siteName(),
                    site + " is down since " + DATE.format(event.at()) + ".\nReason: " + event.error());
            case RECOVERED -> new AlertMessage("🟢 RECOVERED: " + event.siteName(),
                    site + " is back up" + (event.downtime() == null ? "."
                            : " after " + humanize(event.downtime()) + " of downtime."));
            case SSL_EXPIRING -> new AlertMessage("🟠 SSL expiring: " + event.siteName(),
                    "The SSL certificate of " + site + " expires on " + DATE.format(event.sslExpiresAt()) + " ("
                            + daysLeft(event) + ").\nRenew it before visitors start seeing security warnings.");
        };
    }

    static AlertMessage test() {
        return new AlertMessage("Sitewatch test alert",
                "This is a test. If you can read it, Sitewatch alerts reach you on this channel.");
    }

    private static String daysLeft(AlertEvent event) {
        long days = ChronoUnit.DAYS.between(event.at(), event.sslExpiresAt());
        return days <= 0 ? "less than a day left" : days == 1 ? "1 day left" : days + " days left";
    }

    static String humanize(Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours < 24) {
            return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
        }
        return (hours / 24) + " d " + (hours % 24) + " h";
    }
}
