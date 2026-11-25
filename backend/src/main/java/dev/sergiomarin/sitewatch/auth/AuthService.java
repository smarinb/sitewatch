package dev.sergiomarin.sitewatch.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.sergiomarin.sitewatch.common.ApiException;
import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;
import dev.sergiomarin.sitewatch.verification.EmailVerificationService;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final EmailVerificationService verification;
    private final AccessProperties access;
    private final LoginAttemptGuard loginGuard;
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
            EmailVerificationService verification, AccessProperties access, LoginAttemptGuard loginGuard) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.verification = verification;
        this.access = access;
        this.loginGuard = loginGuard;
        this.dummyHash = passwordEncoder.encode("timing-attack-mitigation");
    }

    @Transactional
    public TokenResponse register(AuthRequest request) {
        if (!access.registrationEnabled()) {
            throw ApiException.forbidden("Registration is closed on this public demo");
        }
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
        verification.request(user);
        return tokens.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(AuthRequest request) {
        loginGuard.check(request.email());
        Optional<AppUser> user = users.findByEmail(request.email());
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            loginGuard.failed(request.email());
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        loginGuard.succeeded(request.email());
        return tokens.issue(user.get());
    }

    /** Whether the "Try the demo" button can work: configured, and the account exists. */
    @Transactional(readOnly = true)
    public boolean demoAvailable() {
        return access.demoConfigured() && users.existsByEmail(access.demoEmail().trim().toLowerCase(Locale.ROOT));
    }

    /** Signs in as the demo account without a password. Its tokens are read-only (see ReadOnlyFilter). */
    @Transactional(readOnly = true)
    public TokenResponse demo() {
        if (!access.demoConfigured()) {
            throw ApiException.notFound("There is no demo account");
        }
        return users.findByEmail(access.demoEmail().trim().toLowerCase(Locale.ROOT))
                .map(tokens::issue)
                .orElseThrow(() -> ApiException.notFound("There is no demo account"));
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("Email is already registered");
    }
}
