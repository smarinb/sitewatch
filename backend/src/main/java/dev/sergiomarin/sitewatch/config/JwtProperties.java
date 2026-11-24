package dev.sergiomarin.sitewatch.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The secret is a Base64-encoded HMAC key of at least 256 bits
 * (generate one with {@code openssl rand -base64 32}).
 */
@Validated
@ConfigurationProperties("sitewatch.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl, @NotBlank String issuer) {
}
