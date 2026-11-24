package dev.sergiomarin.sitewatch.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

import dev.sergiomarin.sitewatch.IntegrationTest;
import dev.sergiomarin.sitewatch.check.CheckRunner;
import dev.sergiomarin.sitewatch.testsupport.TestServer;

/** Checks → alert state → email, end to end, with only the SMTP server mocked. */
class AlertFlowTest extends IntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    CheckRunner runner;

    @Autowired
    JdbcTemplate jdbc;

    private final AtomicInteger siteStatus = new AtomicInteger(200);
    private TestServer server;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("UPDATE site SET active = false");
        server = TestServer.http().route("/", exchange -> TestServer.respond(exchange, siteStatus.get(), "page",
                Map.of()));
        token = registerUser();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void outageAndRecoveryAreEmailedOnce() throws Exception {
        long id = createSite(server.url("/"));
        runDue(id);                         // up
        siteStatus.set(503);
        runDue(id);                         // first failure: not confirmed yet
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        runDue(id);                         // second failure: DOWN
        runDue(id);                         // still down: no repeat
        List<SimpleMailMessage> sent = sentMails(1);
        assertThat(sent.getFirst().getSubject()).contains("DOWN").contains("Monitored");
        assertThat(sent.getFirst().getText()).contains("HTTP 503");
        assertThat(sent.getFirst().getTo()).singleElement().asString().endsWith("@example.com");

        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.lastCheckedAt").exists());

        clearInvocations(mailSender);
        siteStatus.set(200);
        runDue(id);
        assertThat(sentMails(1).getFirst().getSubject()).contains("RECOVERED");
    }

    @Test
    void usersWhoDisableEmailGetNoAlerts() throws Exception {
        mvc.perform(put("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailAlerts\": false}"))
                .andExpect(status().isOk());
        long id = createSite(server.url("/"));
        siteStatus.set(500);

        runDue(id);
        runDue(id);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void failingSmtpDoesNotBreakChecks() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        long id = createSite(server.url("/"));
        siteStatus.set(500);

        runDue(id);
        runDue(id);

        mvc.perform(get("/api/sites/{id}/checks", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void notificationSettingsCanBeReadUpdatedAndTested() throws Exception {
        mvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAlerts").value(true))
                .andExpect(jsonPath("$.telegramChatId").doesNotExist());

        mvc.perform(put("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAlerts\": true, \"telegramChatId\": \"@not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.telegramChatId").exists());

        mvc.perform(put("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAlerts\": true, \"telegramChatId\": \" 123456789 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramChatId").value("123456789"));

        // No bot token in the test profile: Telegram reports it instead of failing.
        mvc.perform(post("/api/me/notifications/test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sent"))
                .andExpect(jsonPath("$.telegram").value("not configured on the server"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }


    /** Makes the site due and runs one round of checks. */
    private void runDue(long siteId) {
        jdbc.update("UPDATE site SET last_checked_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), siteId);
        assertThat(runner.runDueChecks()).isEqualTo(1);
    }

    private List<SimpleMailMessage> sentMails(int expected) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private long createSite(String url) throws Exception {
        String body = mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Monitored\", \"url\": \"%s\"}".formatted(url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
