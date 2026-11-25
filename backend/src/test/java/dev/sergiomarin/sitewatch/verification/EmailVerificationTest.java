package dev.sergiomarin.sitewatch.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/** Registration → email with a link → confirmed address, and what an unconfirmed address may not do. */
class EmailVerificationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registrationEmailsALinkAndTheAccountStartsUnverified() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        SimpleMailMessage mail = lastMail();
        assertThat(mail.getTo()).containsExactly(email);
        assertThat(mail.getSubject()).contains("Confirm your email");
        assertThat(linkToken(mail)).hasSize(43);
        mvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void followingTheLinkVerifiesTheAddressAndWorksOnlyOnce() throws Exception {
        String token = register(uniqueEmail());
        String link = linkToken(lastMail());

        verifyLink(link).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.emailVerified").value(true));

        verifyLink(link).andExpect(status().isBadRequest());
    }

    @Test
    void unknownAndExpiredLinksAreRejected() throws Exception {
        verifyLink("not-a-real-token").andExpect(status().isBadRequest());

        register(uniqueEmail());
        String link = linkToken(lastMail());
        jdbc.update("UPDATE email_verification SET expires_at = now() - interval '1 minute'");
        verifyLink(link).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This verification link is invalid or has expired"));
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() throws Exception {
        register(uniqueEmail());
        String link = linkToken(lastMail());

        Integer plain = jdbc.queryForObject("SELECT count(*) FROM email_verification WHERE token_hash = ?",
                Integer.class, link);
        assertThat(plain).isZero();
    }

    @Test
    void resendIsRateLimitedThenReplacesTheOldLink() throws Exception {
        String token = register(uniqueEmail());
        String first = linkToken(lastMail());

        mvc.perform(post("/api/me/email-verification").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isTooManyRequests());

        jdbc.update("UPDATE email_verification SET created_at = now() - interval '2 minutes'");
        clearInvocations(mailSender);
        mvc.perform(post("/api/me/email-verification").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        String second = linkToken(lastMail());

        assertThat(second).isNotEqualTo(first);
        verifyLink(first).andExpect(status().isBadRequest());
        verifyLink(second).andExpect(status().isNoContent());
    }

    @Test
    void resendIsRefusedOnceTheAddressIsVerified() throws Exception {
        String token = register(uniqueEmail());
        verifyLink(linkToken(lastMail())).andExpect(status().isNoContent());

        mvc.perform(post("/api/me/email-verification").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void resendNeedsALogin() throws Exception {
        mvc.perform(post("/api/me/email-verification")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedAddressGetsNoAlertEmail() throws Exception {
        String token = register(uniqueEmail());
        clearInvocations(mailSender);

        mvc.perform(post("/api/me/notifications/test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("email address not verified"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void aVerifiedAddressGetsAlertEmails() throws Exception {
        String token = register(uniqueEmail());
        verifyLink(linkToken(lastMail())).andExpect(status().isNoContent());
        clearInvocations(mailSender);

        mvc.perform(post("/api/me/notifications/test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.email").value("sent"));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void aMailFailureDoesNotBreakRegistration() throws Exception {
        doThrow(new MailSendException("SES is in sandbox")).when(mailSender).send(any(SimpleMailMessage.class));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isCreated());
    }

    // ---- helpers ----

    /** Registers a user with a fresh address and returns its access token. */
    private String register(String email) throws Exception {
        clearInvocations(mailSender);
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private SimpleMailMessage lastMail() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    private static String linkToken(SimpleMailMessage mail) {
        Matcher matcher = LINK.matcher(mail.getText());
        assertThat(matcher.find()).as("a verification link in the email").isTrue();
        return matcher.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions verifyLink(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }
}
