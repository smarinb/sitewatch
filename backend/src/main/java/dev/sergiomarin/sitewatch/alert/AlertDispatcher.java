package dev.sergiomarin.sitewatch.alert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Delivers alerts through every channel the owner enabled. Runs after the
 * check is committed; a failing channel is logged and counted, never retried,
 * and never affects the check or the other channels.
 */
@Service
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final AppUserRepository users;
    private final List<AlertChannel> channels;
    private final MeterRegistry meters;

    public AlertDispatcher(AppUserRepository users, List<AlertChannel> channels, MeterRegistry meters) {
        this.users = users;
        this.channels = channels;
        this.meters = meters;
    }

    public void dispatch(List<AlertEvent> events) {
        for (AlertEvent event : events) {
            users.findById(event.ownerId()).ifPresent(user -> {
                AlertMessage message = AlertMessage.of(event);
                for (AlertChannel channel : channels) {
                    if (channel.available() && channel.enabledFor(user)) {
                        deliver(channel, user, message, event.type().name());
                    }
                }
            });
        }
    }

    /** Sends a test message through each channel and reports what happened, per channel. */
    public Map<String, String> sendTest(AppUser user) {
        Map<String, String> outcome = new LinkedHashMap<>();
        for (AlertChannel channel : channels) {
            if (!channel.available()) {
                outcome.put(channel.name(), "not configured on the server");
            } else if (!channel.enabledFor(user)) {
                outcome.put(channel.name(), channel.disabledReason(user));
            } else {
                outcome.put(channel.name(), deliver(channel, user, AlertMessage.test(), "TEST") ? "sent" : "failed");
            }
        }
        return outcome;
    }

    private boolean deliver(AlertChannel channel, AppUser user, AlertMessage message, String type) {
        try {
            channel.send(user, message);
            meters.counter("sitewatch.alerts", "channel", channel.name(), "type", type, "result", "sent").increment();
            return true;
        } catch (AlertDeliveryException | RuntimeException e) {
            meters.counter("sitewatch.alerts", "channel", channel.name(), "type", type, "result", "failed")
                    .increment();
            log.warn("Could not send {} alert to user {} via {}: {}", type, user.getId(), channel.name(),
                    e.getMessage());
            return false;
        }
    }
}
