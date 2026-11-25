package dev.sergiomarin.sitewatch.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.JsonPath;

import dev.sergiomarin.sitewatch.IntegrationTest;
import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;

/** How the public demo is locked down: no sign-up, a read-only demo account, per-user limits, login lockout. */
@TestPropertySource(properties = {
        "sitewatch.access.registration-enabled=false",
        "sitewatch.access.demo-email=demo@example.com",
        "sitewatch.access.max-sites-per-user=2",
})
class PublicDemoAccessTest extends IntegrationTest {

    private static final String DEMO_EMAIL = "demo@example.com";

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void createDemoAccount() {
        if (users.findByEmail(DEMO_EMAIL).isEmpty()) {
            users.save(new AppUser(DEMO_EMAIL, passwordEncoder.encode(PASSWORD)));
        }
    }

    @Test
    void signUpIsClosed() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Registration is closed on this public demo"));
    }

    @Test
    void theLoginPageCanAskWhatIsAvailableWithoutSigningIn() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(false))
                .andExpect(jsonPath("$.demoAvailable").value(true));
    }

    @Test
    void theDemoSignsInWithoutAPasswordAndCanOnlyRead() throws Exception {
        String token = demoToken();

        mvc.perform(get("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\", \"url\": \"https://example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("The demo account is read-only"));
        mvc.perform(put("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAlerts\": false, \"telegramChatId\": null}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/notifications/test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDemoAccountIsReadOnlyEvenWithItsPassword() throws Exception {
        String token = login(DEMO_EMAIL);

        mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\", \"url\": \"https://example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDemoVisitorCanStillSignInAsSomeoneElse() throws Exception {
        String email = newUser();

        mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, bearer(demoToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void regularUsersAreNotReadOnlyButAreCappedAtTwoSites() throws Exception {
        String token = login(newUser());

        createSite(token, "https://one.example.com").andExpect(status().isCreated());
        createSite(token, "https://two.example.com").andExpect(status().isCreated());
        createSite(token, "https://three.example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can monitor at most 2 sites"));
    }

    @Test
    void checksCannotRunMoreOftenThanEveryFiveMinutes() throws Exception {
        String token = login(newUser());

        mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\", \"url\": \"https://example.com\", \"checkIntervalMinutes\": 4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.checkIntervalMinutes").exists());
    }

    @Test
    void repeatedWrongPasswordsLockOnlyThatEmail() throws Exception {
        String email = newUser();
        String other = newUser();

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(email, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(other, PASSWORD)))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private String newUser() {
        String email = uniqueEmail();
        users.save(new AppUser(email, passwordEncoder.encode(PASSWORD)));
        return email;
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String demoToken() throws Exception {
        String body = mvc.perform(post("/api/auth/demo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions createSite(String token, String url) throws Exception {
        return mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Site\", \"url\": \"%s\"}".formatted(url)));
    }
}
