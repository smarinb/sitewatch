package dev.sergiomarin.sitewatch.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** Helpers to read the authenticated user from the validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
