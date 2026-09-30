package dev.zynema.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the correlation id contract:
 *  - an inbound X-Correlation-Id is reused (propagation), and
 *  - a missing one is generated (entry point).
 */
class CorrelationIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
            .addFilters(new CorrelationIdFilter())
            .build();
    }

    @Test
    void reusesInboundCorrelationId() throws Exception {
        mockMvc.perform(get("/ping").header(CorrelationIdFilter.HEADER, "corr-abc-123"))
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-abc-123"));
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        String returned = mockMvc.perform(get("/ping"))
            .andExpect(status().isOk())
            .andExpect(header().exists(CorrelationIdFilter.HEADER))
            .andReturn()
            .getResponse()
            .getHeader(CorrelationIdFilter.HEADER);

        assertThat(returned).isNotBlank();
    }

    @RestController
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }
}
