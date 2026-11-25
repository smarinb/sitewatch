package dev.sergiomarin.sitewatch.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.sergiomarin.sitewatch.common.ApiException;

/**
 * Slows down password guessing: after too many failed sign-ins for one email, that email is locked for a while.
 * Kept in memory on purpose (one server, and a restart only forgives the attacker, never locks anyone out).
 * The key is the email and not the address, because behind the proxy every request looks like it comes from it.
 * The cost is that someone can lock a known email for the lockout period; they still cannot get in.
 */
@Component
class LoginAttemptGuard {

    /** Bounds the memory an attacker can make this use by trying many different emails. */
    private static final int MAX_TRACKED = 10_000;

    private record State(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final AccessProperties properties;
    private final Clock clock;

    @Autowired
    LoginAttemptGuard(AccessProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptGuard(AccessProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Throws 429 while the email is locked. Call before checking the password. */
    void check(String email) {
        State state = states.get(email);
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(clock.instant())) {
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Try again in a few minutes.");
        }
    }

    void failed(String email) {
        Instant now = clock.instant();
        if (states.size() >= MAX_TRACKED) {
            states.values().removeIf(s -> isOver(s, now));
            if (states.size() >= MAX_TRACKED && !states.containsKey(email)) {
                return;
            }
        }
        states.compute(email, (key, current) -> {
            State state = current == null || isOver(current, now) ? new State(0, now, null) : current;
            if (state.lockedUntil() != null) {
                return state;
            }
            int failures = state.failures() + 1;
            return failures >= properties.loginMaxFailures()
                    ? new State(0, now, now.plus(properties.loginLockout()))
                    : new State(failures, state.windowStart(), null);
        });
    }

    void succeeded(String email) {
        states.remove(email);
    }

    /** A lock that has run out, or failures older than the window, no longer count. */
    private boolean isOver(State state, Instant now) {
        return state.lockedUntil() != null
                ? !state.lockedUntil().isAfter(now)
                : !state.windowStart().plus(properties.loginLockout()).isAfter(now);
    }
}
