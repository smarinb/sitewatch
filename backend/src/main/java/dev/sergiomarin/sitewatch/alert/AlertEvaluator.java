package dev.sergiomarin.sitewatch.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.sergiomarin.sitewatch.check.CheckResult;
import dev.sergiomarin.sitewatch.site.MonitorState;
import dev.sergiomarin.sitewatch.site.Site;
import dev.sergiomarin.sitewatch.site.SiteStatus;

/**
 * Turns a check result into the site's next alert state and the alerts to send.
 * Pure apart from reading the site, so the rules are easy to test:
 * <ul>
 * <li>DOWN after {@code failureThreshold} failed checks in a row, sent once per outage;</li>
 * <li>RECOVERED on the first successful check after a confirmed outage;</li>
 * <li>SSL_EXPIRING once per certificate when it expires within {@code sslWarning}.</li>
 * </ul>
 */
@Component
public class AlertEvaluator {

    private final int failureThreshold;
    private final Duration sslWarning;

    @Autowired
    public AlertEvaluator(AlertProperties properties) {
        this(properties.failureThreshold(), properties.sslWarning());
    }

    AlertEvaluator(int failureThreshold, Duration sslWarning) {
        this.failureThreshold = failureThreshold;
        this.sslWarning = sslWarning;
    }

    public Evaluation evaluate(Site site, CheckResult result, Instant now) {
        MonitorState state = site.monitorState();
        List<AlertEvent> events = new ArrayList<>();
        long ownerId = site.getOwner().getId();

        SiteStatus status = state.status();
        int failures = state.consecutiveFailures();
        Instant downSince = state.downSince();
        if (result.up()) {
            if (status == SiteStatus.DOWN) {
                Duration downtime = downSince == null ? null : Duration.between(downSince, now);
                events.add(new AlertEvent(AlertEvent.Type.RECOVERED, ownerId, site.getName(), site.getUrl(), now,
                        null, downtime, null));
            }
            status = SiteStatus.UP;
            failures = 0;
            downSince = null;
        } else {
            failures++;
            if (status != SiteStatus.DOWN && failures >= failureThreshold) {
                status = SiteStatus.DOWN;
                downSince = now;
                events.add(new AlertEvent(AlertEvent.Type.DOWN, ownerId, site.getName(), site.getUrl(), now,
                        result.error(), null, null));
            }
        }

        Instant sslAlerted = state.sslAlertedExpiry();
        Instant expiry = result.sslExpiresAt();
        if (expiry != null && !expiry.equals(sslAlerted) && expiry.isBefore(now.plus(sslWarning))) {
            sslAlerted = expiry;
            events.add(new AlertEvent(AlertEvent.Type.SSL_EXPIRING, ownerId, site.getName(), site.getUrl(), now,
                    null, null, expiry));
        }

        return new Evaluation(new MonitorState(status, failures, downSince, sslAlerted), List.copyOf(events));
    }

    public record Evaluation(MonitorState state, List<AlertEvent> events) {
    }
}
