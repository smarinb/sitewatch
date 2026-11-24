package dev.sergiomarin.sitewatch.alert;

import dev.sergiomarin.sitewatch.user.AppUser;

/** A way to reach a user: email, Telegram… */
public interface AlertChannel {

    /** Short name used in logs and in the test endpoint's response. */
    String name();

    /** Whether the server is configured for this channel at all. */
    boolean available();

    /** Whether this user wants alerts through this channel. */
    boolean enabledFor(AppUser user);

    void send(AppUser user, AlertMessage message) throws AlertDeliveryException;
}
