package dev.sergiomarin.sitewatch.auth;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.sergiomarin.sitewatch.common.ApiException;
import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("timing-attack-mitigation");
    }

    @Transactional
    public TokenResponse register(AuthRequest request) {
        String email = request.email();
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent registration with the same email.
            throw emailTaken();
        }
        return tokens.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(AuthRequest request) {
        Optional<AppUser> user = users.findByEmail(request.email());
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        return tokens.issue(user.get());
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("Email is already registered");
    }
}
