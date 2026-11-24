package dev.sergiomarin.sitewatch.alert;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.sergiomarin.sitewatch.user.AppUser;

/**
 * Sends through the Telegram Bot API. The user must have started a chat with
 * the bot, and gives us that chat's id. The bot token is part of the request
 * URL, so it is scrubbed from every error message before it can reach a log.
 */
@Component
class TelegramChannel implements AlertChannel {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AlertProperties.Telegram telegram;
    private final RestClient client;

    TelegramChannel(AlertProperties properties) {
        this.telegram = properties.telegram();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().baseUrl(telegram.apiUrl()).requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean available() {
        return telegram.configured();
    }

    @Override
    public boolean enabledFor(AppUser user) {
        return user.getTelegramChatId() != null;
    }

    @Override
    public void send(AppUser user, AlertMessage message) throws AlertDeliveryException {
        Map<String, Object> body = Map.of(
                "chat_id", user.getTelegramChatId(),
                "text", message.subject() + "\n\n" + message.body(),
                "disable_web_page_preview", true);
        try {
            client.post().uri("/bot{token}/sendMessage", telegram.botToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AlertDeliveryException("Telegram delivery failed: " + scrub(e.getMessage()), null);
        }
    }

    private String scrub(String text) {
        return text == null ? null : text.replace(telegram.botToken(), "<token>");
    }
}
