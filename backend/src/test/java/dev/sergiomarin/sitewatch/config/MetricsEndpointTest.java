package dev.sergiomarin.sitewatch.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import dev.sergiomarin.sitewatch.IntegrationTest;

/** Alloy scrapes the metrics inside the compose network without a token; the proxy hides them from the internet. */
// Spring Boot turns metrics export off in tests; this test needs it on.
@TestPropertySource(properties = "management.prometheus.metrics.export.enabled=true")
class MetricsEndpointTest extends IntegrationTest {

    @Test
    void prometheusEndpointIsReadableWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void otherActuatorEndpointsStayClosed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
