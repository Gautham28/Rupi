package com.rupi.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rupi.config.SecurityConfig;
import com.rupi.security.JsonAccessDeniedHandler;
import com.rupi.security.JsonAuthenticationEntryPoint;
import com.rupi.security.JwtAuthenticationFilter;
import com.rupi.security.JwtService;
import com.rupi.security.RateLimitFilter;
import com.rupi.service.RateLimiterService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StatusController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    JsonAuthenticationEntryPoint.class,
    JsonAccessDeniedHandler.class,
    RateLimitFilter.class,
    StatusControllerTest.RateLimitTestConfig.class
})
@TestPropertySource(
        properties = {
            "rupi.jwt.secret=change-me-dev-only-use-at-least-32-chars!!",
            "rupi.jwt.expiration-ms=86400000",
            "rupi.cors.allowed-origins=http://localhost:5173",
            "rupi.demo.enabled=true",
            "rupi.demo.signup-credits=1000.00",
            "rupi.demo.faucet-amount=500.00",
            "rupi.demo.faucet-cooldown-hours=24",
            "rupi.demo.max-balance=5000.00",
            "rupi.rate-limit.capacity=10",
            "rupi.rate-limit.refill-per-second=10"
        })
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("rupi"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @TestConfiguration
    static class RateLimitTestConfig {
        @Bean
        RateLimiterService rateLimiterService() {
            RateLimiterService mock = Mockito.mock(RateLimiterService.class);
            when(mock.tryConsume(any(UUID.class)))
                    .thenReturn(RateLimiterService.RateLimitResult.allowed(10));
            return mock;
        }
    }
}
