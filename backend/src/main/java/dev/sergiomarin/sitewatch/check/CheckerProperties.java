package dev.sergiomarin.sitewatch.check;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the in-process site checker.
 *
 * @param enabled               run the scheduler (tests turn it off and drive checks directly)
 * @param tick                  how often due sites are looked up
 * @param batchSize             most sites checked per tick
 * @param maxConcurrent         most checks in flight at once
 * @param connectTimeout        TCP + TLS connect timeout
 * @param responseTimeout       socket read timeout
 * @param maxBodyBytes          how much of a page is read (enough for the WordPress meta tag)
 * @param userAgent             sent with every request
 * @param allowPrivateAddresses lets checks reach loopback/private/link-local addresses; only for tests
 * @param retention             how long check history is kept
 */
@Validated
@ConfigurationProperties("sitewatch.checker")
public record CheckerProperties(
        boolean enabled,
        @NotNull Duration tick,
        @Min(1) int batchSize,
        @Min(1) int maxConcurrent,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @NotNull DataSize maxBodyBytes,
        @NotBlank String userAgent,
        boolean allowPrivateAddresses,
        @NotNull Duration retention) {
}
