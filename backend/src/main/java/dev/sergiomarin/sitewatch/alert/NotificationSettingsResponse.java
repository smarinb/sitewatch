package dev.sergiomarin.sitewatch.alert;

/** What the panel shows: the saved settings plus whether the account address has been confirmed. */
public record NotificationSettingsResponse(boolean emailAlerts, String telegramChatId, boolean emailVerified) {
}
