package dev.sergiomarin.sitewatch.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessProperties access;

    public AuthController(AuthService authService, AccessProperties access) {
        this.authService = authService;
        this.access = access;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    /** What the login page needs to know before showing its options. Public. */
    @GetMapping("/config")
    public AuthConfig config() {
        return new AuthConfig(access.registrationEnabled(), authService.demoAvailable());
    }

    @PostMapping("/demo")
    public TokenResponse demo() {
        return authService.demo();
    }

    public record AuthConfig(boolean registrationOpen, boolean demoAvailable) {
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(CurrentUser.id(jwt), jwt.getClaimAsString("email"));
    }

    public record MeResponse(Long id, String email) {
    }
}
