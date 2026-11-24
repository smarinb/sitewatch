package dev.sergiomarin.sitewatch.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import dev.sergiomarin.sitewatch.alert.AlertEvaluator.Evaluation;
import dev.sergiomarin.sitewatch.check.CheckResult;
import dev.sergiomarin.sitewatch.site.Site;
import dev.sergiomarin.sitewatch.site.SiteStatus;
import dev.sergiomarin.sitewatch.user.AppUser;

class AlertEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

    private final AlertEvaluator evaluator = new AlertEvaluator(2, Duration.ofDays(14));
    private Site site;

    @BeforeEach
    void setUp() {
        AppUser owner = new AppUser("owner@example.com", "hash");
        ReflectionTestUtils.setField(owner, "id", 7L);
        site = new Site(owner, "Shop", "https://shop.example.com", 5, true);
    }

    @Test
    void singleFailureDoesNotAlert() {
        assertThat(check(up(), T0).events()).isEmpty();
        Evaluation first = check(down("HTTP 502"), T0.plusSeconds(300));

        assertThat(first.events()).isEmpty();
        assertThat(site.getStatus()).isEqualTo(SiteStatus.UP);
        assertThat(check(up(), T0.plusSeconds(600)).events()).isEmpty();
    }

    @Test
    void confirmedOutageAlertsOnceAndRecoveryReportsDowntime() {
        check(up(), T0);
        check(down("Connection refused"), T0.plusSeconds(300));

        List<AlertEvent> down = check(down("Connection refused"), T0.plusSeconds(600)).events();
        assertThat(down).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(AlertEvent.Type.DOWN);
            assertThat(e.ownerId()).isEqualTo(7L);
            assertThat(e.error()).isEqualTo("Connection refused");
        });
        assertThat(site.getStatus()).isEqualTo(SiteStatus.DOWN);

        // Still down: no repeated alerts.
        assertThat(check(down("Connection refused"), T0.plusSeconds(900)).events()).isEmpty();

        List<AlertEvent> recovered = check(up(), T0.plusSeconds(600 + 3600)).events();
        assertThat(recovered).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(AlertEvent.Type.RECOVERED);
            assertThat(e.downtime()).isEqualTo(Duration.ofHours(1));
        });
        assertThat(site.getStatus()).isEqualTo(SiteStatus.UP);
    }

    @Test
    void siteThatStartsDownAlertsAfterThreshold() {
        assertThat(check(down("DNS lookup failed"), T0).events()).isEmpty();
        assertThat(site.getStatus()).isEqualTo(SiteStatus.UNKNOWN);
        assertThat(check(down("DNS lookup failed"), T0.plusSeconds(300)).events())
                .extracting(AlertEvent::type).containsExactly(AlertEvent.Type.DOWN);
    }

    @Test
    void sslWarningIsSentOncePerCertificate() {
        Instant expiry = T0.plus(Duration.ofDays(10));

        assertThat(check(upWithCert(expiry), T0).events())
                .extracting(AlertEvent::type).containsExactly(AlertEvent.Type.SSL_EXPIRING);
        assertThat(check(upWithCert(expiry), T0.plusSeconds(300)).events()).isEmpty();

        // Renewed, but the new certificate is short-lived too: that one gets its own warning.
        Instant renewed = T0.plus(Duration.ofDays(12));
        assertThat(check(upWithCert(renewed), T0.plusSeconds(600)).events())
                .singleElement().satisfies(e -> assertThat(e.sslExpiresAt()).isEqualTo(renewed));
    }

    @Test
    void healthyCertificateDoesNotWarn() {
        assertThat(check(upWithCert(T0.plus(Duration.ofDays(60))), T0).events()).isEmpty();
    }

    @Test
    void changingTheUrlResetsTheAlertState() {
        check(down("x"), T0);
        check(down("x"), T0.plusSeconds(300));
        assertThat(site.getStatus()).isEqualTo(SiteStatus.DOWN);

        site.update("Shop", "https://new.example.com", 5, true);

        assertThat(site.getStatus()).isEqualTo(SiteStatus.UNKNOWN);
        assertThat(site.getLastCheckedAt()).isNull();
        // Its first success is not a "recovery" of the old URL.
        assertThat(check(up(), T0.plusSeconds(600)).events()).isEmpty();
    }

    private Evaluation check(CheckResult result, Instant at) {
        Evaluation evaluation = evaluator.evaluate(site, result, at);
        site.applyCheck(at, evaluation.state());
        return evaluation;
    }

    private static CheckResult up() {
        return new CheckResult(true, 200, 100, null, null, null);
    }

    private static CheckResult upWithCert(Instant expiry) {
        return new CheckResult(true, 200, 100, expiry, null, null);
    }

    private static CheckResult down(String error) {
        return new CheckResult(false, null, null, null, null, error);
    }
}
