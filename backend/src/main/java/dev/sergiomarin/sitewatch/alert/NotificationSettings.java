package dev.sergiomarin.sitewatch.alert;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Where a user wants alerts. {@code telegramChatId} is the numeric id of the
 * user's chat with the bot (negative for groups); {@code null} turns Telegram off.
 */
public record NotificationSettings(
        @NotNull Boolean emailAlerts,
        @Pattern(regexp = "-?\\d{1,20}", message = "must be a numeric Telegram chat id") String telegramChatId) {

    public NotificationSettings {
        if (telegramChatId != null && telegramChatId.isBlank()) {
            telegramChatId = null;
        }
        telegramChatId = telegramChatId == null ? null : telegramChatId.trim();
    }
}
