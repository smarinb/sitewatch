package dev.sergiomarin.sitewatch.verification;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl        where the panel lives; the link in the email points to it (a comma separated list is
 *                       accepted and the first entry used, so it can follow the CORS setting)
 * @param ttl            how long a link works
 * @param resendInterval minimum time between two emails to the same user
 */
@Validated
@ConfigurationProperties("sitewatch.verification")
public record VerificationProperties(
        @NotBlank String baseUrl,
        @NotNull Duration ttl,
        @NotNull Duration resendInterval) {

    String panelUrl() {
        String first = baseUrl.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }
}
