package dev.sergiomarin.sitewatch.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.sergiomarin.sitewatch.IntegrationTest;
import dev.sergiomarin.sitewatch.testsupport.TestServer;

class CheckRunnerTest extends IntegrationTest {

    @Autowired
    CheckRunner runner;

    @Autowired
    JdbcTemplate jdbc;

    private TestServer server;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // Sites from other tests share the database; park them so only this test's sites are due.
        jdbc.update("UPDATE site SET active = false");
        server = TestServer.http()
                .page("/", 200, "<meta name=\"generator\" content=\"WordPress 6.7.1\">")
                .page("/down", 500, "boom");
        token = registerUser();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void checksDueSitesRecordsHistoryAndWaitsForTheInterval() throws Exception {
        long up = createSite("Up", server.url("/"));
        long down = createSite("Down", server.url("/down"));

        assertThat(runner.runDueChecks()).isEqualTo(2);
        // Just checked: not due again until their interval elapses.
        assertThat(runner.runDueChecks()).isZero();

        mvc.perform(get("/api/sites/{id}/checks", up).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].up").value(true))
                .andExpect(jsonPath("$[0].httpStatus").value(200))
                .andExpect(jsonPath("$[0].responseTimeMs").isNumber())
                .andExpect(jsonPath("$[0].wpVersion").value("6.7.1"))
                .andExpect(jsonPath("$[0].checkedAt").exists());
        mvc.perform(get("/api/sites/{id}/checks", down).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].up").value(false))
                .andExpect(jsonPath("$[0].error").value("HTTP 500"));

        // Once the interval has passed the site is due again, and history is newest first.
        jdbc.update("UPDATE site SET last_checked_at = now() - interval '6 minutes' WHERE id = ?", up);
        assertThat(runner.runDueChecks()).isEqualTo(1);
        mvc.perform(get("/api/sites/{id}/checks?limit=1", up).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/sites/{id}/checks", up).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void latestReturnsOneCheckPerOwnSiteAndSinceFiltersHistory() throws Exception {
        long up = createSite("Up", server.url("/"));
        long down = createSite("Down", server.url("/down"));
        runner.runDueChecks();
        jdbc.update("UPDATE site SET last_checked_at = now() - interval '6 minutes' WHERE id IN (?, ?)", up, down);
        runner.runDueChecks();
        // An older check that must never be reported as the latest.
        jdbc.update("INSERT INTO site_check (site_id, checked_at, up) VALUES (?, now() - interval '3 hours', false)",
                up);

        mvc.perform(get("/api/checks/latest").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.siteId == %d)].up".formatted(up)).value(true))
                .andExpect(jsonPath("$[?(@.siteId == %d)].up".formatted(down)).value(false))
                .andExpect(jsonPath("$[?(@.siteId == %d)].wpVersion".formatted(up)).value("6.7.1"));

        // Another user sees none of them.
        mvc.perform(get("/api/checks/latest").header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(jsonPath("$.length()").value(0));

        String lastHour = java.time.Instant.now().minusSeconds(3600).toString();
        mvc.perform(get("/api/sites/{id}/checks", up).param("since", lastHour)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/sites/{id}/checks", up).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void inactiveSitesAreNotChecked() throws Exception {
        long id = createSite("Paused", server.url("/"));
        mvc.perform(put("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Paused\", \"url\": \"%s\", \"active\": false}".formatted(server.url("/"))))
                .andExpect(status().isOk());

        assertThat(runner.runDueChecks()).isZero();
    }

    @Test
    void historyIsPrivateAndLimitIsValidated() throws Exception {
        long id = createSite("Mine", server.url("/"));
        runner.runDueChecks();

        mvc.perform(get("/api/sites/{id}/checks", id).header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/sites/{id}/checks?limit=0", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sites/{id}/checks?limit=501", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private long createSite(String name, String url) throws Exception {
        String body = mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"url\": \"%s\"}".formatted(name, url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
