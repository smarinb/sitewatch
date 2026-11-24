package dev.sergiomarin.sitewatch.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AlertMessageTest {

    private static final Instant AT = Instant.parse("2026-11-24T12:00:00Z");

    @Test
    void downMessageNamesSiteAndReason() {
        AlertMessage message = AlertMessage.of(new AlertEvent(AlertEvent.Type.DOWN, 1, "Shop",
                "https://shop.example.com", AT, "HTTP 503", null, null));

        assertThat(message.subject()).contains("DOWN").contains("Shop");
        assertThat(message.body()).contains("https://shop.example.com").contains("2026-11-24 12:00 UTC")
                .contains("HTTP 503");
    }

    @Test
    void sslMessageCountsDaysLeft() {
        AlertMessage message = AlertMessage.of(new AlertEvent(AlertEvent.Type.SSL_EXPIRING, 1, "Shop",
                "https://shop.example.com", AT, null, null, AT.plus(Duration.ofDays(9))));

        assertThat(message.body()).contains("2026-12-03").contains("9 days left");
    }

    @Test
    void humanizesDowntime() {
        assertThat(AlertMessage.humanize(Duration.ofSeconds(20))).isEqualTo("1 min");
        assertThat(AlertMessage.humanize(Duration.ofMinutes(45))).isEqualTo("45 min");
        assertThat(AlertMessage.humanize(Duration.ofMinutes(125))).isEqualTo("2 h 5 min");
        assertThat(AlertMessage.humanize(Duration.ofHours(50))).isEqualTo("2 d 2 h");
    }
}
