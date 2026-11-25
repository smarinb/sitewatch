package dev.sergiomarin.sitewatch.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.sergiomarin.sitewatch.IntegrationTest;

/** Without the demo settings (local development) sign-up is open and there is no demo account. */
class DefaultAccessTest extends IntegrationTest {

    @Test
    void signUpIsOpenAndThereIsNoDemoByDefault() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(true))
                .andExpect(jsonPath("$.demoAvailable").value(false));
        mvc.perform(post("/api/auth/demo")).andExpect(status().isNotFound());
    }
}
