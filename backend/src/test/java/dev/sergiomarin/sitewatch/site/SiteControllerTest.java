package dev.sergiomarin.sitewatch.site;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.sergiomarin.sitewatch.IntegrationTest;

class SiteControllerTest extends IntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
    }

    @Test
    void createAppliesDefaultsAndReturnsLocation() throws Exception {
        long id = createSite(token, siteJson("My blog", "https://blog.example.com"));

        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My blog"))
                .andExpect(jsonPath("$.url").value("https://blog.example.com"))
                .andExpect(jsonPath("$.checkIntervalMinutes").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = createSite(token, siteJson("Shop", "https://shop.example.com"));

        mvc.perform(get("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(put("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Shop (new)", "url": "https://new.example.com",
                                 "checkIntervalMinutes": 15, "active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shop (new)"))
                .andExpect(jsonPath("$.url").value("https://new.example.com"))
                .andExpect(jsonPath("$.checkIntervalMinutes").value(15))
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(delete("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersOnlySeeAndModifyTheirOwnSites() throws Exception {
        long id = createSite(token, siteJson("Mine", "https://mine.example.com"));
        String intruder = registerUser();

        mvc.perform(get("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(siteJson("Hijacked", "https://evil.example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());

        // The owner's site is untouched.
        mvc.perform(get("/api/sites/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mine"));
    }

    @Test
    void sameUrlIsUniquePerUserButAllowedAcrossUsers() throws Exception {
        String url = "https://shared.example.com";
        createSite(token, siteJson("First", url));

        postSite(token, siteJson("Again", url))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You are already monitoring this URL"));

        createSite(registerUser(), siteJson("Other user", url));
    }

    @Test
    void updateToAnotherOwnUrlConflicts() throws Exception {
        createSite(token, siteJson("A", "https://a.example.com"));
        long b = createSite(token, siteJson("B", "https://b.example.com"));

        mvc.perform(put("/api/sites/{id}", b).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(siteJson("B", "https://a.example.com")))
                .andExpect(status().isConflict());

        // Saving a site with its own unchanged URL is fine.
        mvc.perform(put("/api/sites/{id}", b).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(siteJson("B renamed", "https://b.example.com")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = { "not a url", "ftp://example.com", "example.com", "https://", "javascript:alert(1)",
            "https://user:pass@example.com" })
    void rejectsInvalidUrls(String url) throws Exception {
        postSite(token, siteJson("Bad", url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
    }

    @Test
    void validatesNameAndInterval() throws Exception {
        postSite(token, """
                {"name": " ", "url": "https://example.com", "checkIntervalMinutes": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.checkIntervalMinutes").exists());

        postSite(token, """
                {"name": "Too slow", "url": "https://example.com", "checkIntervalMinutes": 1441}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/sites")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sites").contentType(MediaType.APPLICATION_JSON)
                        .content(siteJson("x", "https://example.com")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postSite(String accessToken, String json) throws Exception {
        return mvc.perform(post("/api/sites").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createSite(String accessToken, String json) throws Exception {
        String body = postSite(accessToken, json)
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String siteJson(String name, String url) {
        return """
                {"name": "%s", "url": "%s"}
                """.formatted(name, url);
    }
}
