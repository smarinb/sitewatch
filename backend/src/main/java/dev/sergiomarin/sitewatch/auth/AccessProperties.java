package dev.sergiomarin.sitewatch.auth;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Who may use this deployment and how much.
 *
 * @param registrationEnabled whether anyone can create an account (turn off for a public demo)
 * @param demoEmail           account the "Try the demo" button signs in as; blank turns the button off. Its
 *                            tokens are read-only
 * @param maxSitesPerUser     sites one user may monitor
 * @param loginMaxFailures    failed sign-ins for one email before it is locked
 * @param loginLockout        how long the lock lasts, and the window in which failures are counted
 */
@Validated
@ConfigurationProperties("sitewatch.access")
public record AccessProperties(
        boolean registrationEnabled,
        String demoEmail,
        @Min(1) int maxSitesPerUser,
        @Min(1) int loginMaxFailures,
        @NotNull Duration loginLockout) {

    public boolean demoConfigured() {
        return demoEmail != null && !demoEmail.isBlank();
    }

    public boolean isDemo(String email) {
        return demoConfigured() && demoEmail.trim().equalsIgnoreCase(email);
    }
}
