package dev.sergiomarin.sitewatch.auth;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for register and login. The 72-character cap matches BCrypt,
 * which ignores anything beyond 72 bytes.
 */
public record AuthRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** Emails are stored trimmed and lower-cased, so lookups are case-insensitive. */
    public AuthRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
