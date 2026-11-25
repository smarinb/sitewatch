package dev.sergiomarin.sitewatch.alert;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.sergiomarin.sitewatch.auth.CurrentUser;
import dev.sergiomarin.sitewatch.common.ApiException;
import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;

@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {

    private final AppUserRepository users;
    private final AlertDispatcher dispatcher;

    public NotificationController(AppUserRepository users, AlertDispatcher dispatcher) {
        this.users = users;
        this.dispatcher = dispatcher;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return toSettings(user(jwt));
    }

    @PutMapping
    @Transactional
    public NotificationSettingsResponse update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationSettings settings) {
        AppUser user = user(jwt);
        user.updateNotifications(settings.emailAlerts(), settings.telegramChatId());
        return toSettings(user);
    }

    /** Sends a test alert through every channel; the response says, per channel, whether it went out. */
    @PostMapping("/test")
    public Map<String, String> test(@AuthenticationPrincipal Jwt jwt) {
        return dispatcher.sendTest(user(jwt));
    }

    private AppUser user(Jwt jwt) {
        return users.findById(CurrentUser.id(jwt)).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static NotificationSettingsResponse toSettings(AppUser user) {
        return new NotificationSettingsResponse(user.isEmailAlerts(), user.getTelegramChatId(), user.isEmailVerified());
    }
}
