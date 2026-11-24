package dev.sergiomarin.sitewatch.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.sergiomarin.sitewatch.testsupport.TestServer;
import dev.sergiomarin.sitewatch.user.AppUser;

/** Talks to a fake Bot API on localhost. */
class TelegramChannelTest {

    private static final String TOKEN = "123456:SECRET-token";

    private TestServer api;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        api = TestServer.http().route("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean ok = exchange.getRequestURI().getPath().contains(TOKEN);
            TestServer.respond(exchange, ok ? 200 : 401, ok ? "{\"ok\":true}" : "{\"ok\":false}", Map.of());
        });
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    void sendsPlainTextToTheUsersChat() throws Exception {
        AppUser user = userWithChat("-100200300");

        channel(TOKEN).send(user, new AlertMessage("🔴 DOWN: Shop", "Shop is down"));

        assertThat(path.get()).isEqualTo("/bot" + TOKEN + "/sendMessage");
        assertThat(body.get()).contains("\"chat_id\":\"-100200300\"").contains("DOWN: Shop")
                .contains("Shop is down");
    }

    @Test
    void errorsNeverContainTheBotToken() {
        TelegramChannel channel = channel("999:WRONG-token");

        assertThatThrownBy(() -> channel.send(userWithChat("1"), new AlertMessage("s", "b")))
                .isInstanceOf(AlertDeliveryException.class)
                .hasMessageNotContaining("WRONG-token")
                .hasNoCause();
    }

    @Test
    void availabilityDependsOnTokenAndChatId() {
        assertThat(channel("").available()).isFalse();
        assertThat(channel(TOKEN).available()).isTrue();
        assertThat(channel(TOKEN).enabledFor(userWithChat(null))).isFalse();
        assertThat(channel(TOKEN).enabledFor(userWithChat("42"))).isTrue();
    }

    private TelegramChannel channel(String token) {
        return new TelegramChannel(new AlertProperties(2, Duration.ofDays(14), "a@b.c",
                new AlertProperties.Telegram(token, api.url(""))));
    }

    private static AppUser userWithChat(String chatId) {
        AppUser user = new AppUser("u@example.com", "hash");
        user.updateNotifications(true, chatId);
        return user;
    }
}
