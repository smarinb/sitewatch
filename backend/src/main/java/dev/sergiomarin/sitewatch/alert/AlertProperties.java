package dev.sergiomarin.sitewatch.alert;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param failureThreshold failed checks in a row before a site is declared down
 * @param sslWarning       warn when the certificate expires sooner than this
 * @param mailFrom         sender of alert emails
 * @param telegram         Telegram bot settings; alerts via Telegram are off without a token
 */
@Validated
@ConfigurationProperties("sitewatch.alerts")
public record AlertProperties(
        @Min(1) int failureThreshold,
        @NotNull Duration sslWarning,
        @NotBlank String mailFrom,
        @NotNull Telegram telegram) {

    public record Telegram(String botToken, @NotBlank String apiUrl) {

        public boolean configured() {
            return botToken != null && !botToken.isBlank();
        }
    }
}
