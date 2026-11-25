package dev.sergiomarin.sitewatch.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.sergiomarin.sitewatch.common.ApiException;

class LoginAttemptGuardTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(
            new AccessProperties(true, "", 10, 3, Duration.ofMinutes(15)), clock);

    @Test
    void locksAnEmailAfterTooManyFailuresAndForgivesItAfterTheLockout() {
        guard.failed("a@example.com");
        guard.failed("a@example.com");
        assertThatCode(() -> guard.check("a@example.com")).doesNotThrowAnyException();

        guard.failed("a@example.com");
        assertThatThrownBy(() -> guard.check("a@example.com")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> guard.check("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        guard.failed("a@example.com");
        guard.failed("a@example.com");
        guard.succeeded("a@example.com");
        guard.failed("a@example.com");
        guard.failed("a@example.com");

        assertThatCode(() -> guard.check("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oldFailuresStopCountingOnceTheWindowHasPassed() {
        guard.failed("a@example.com");
        guard.failed("a@example.com");
        clock.advance(Duration.ofMinutes(16));
        guard.failed("a@example.com");

        assertThatCode(() -> guard.check("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneEmailsFailuresDoNotLockAnother() {
        for (int i = 0; i < 3; i++) {
            guard.failed("a@example.com");
        }

        assertThatCode(() -> guard.check("b@example.com")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
