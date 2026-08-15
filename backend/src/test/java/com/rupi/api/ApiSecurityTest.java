package com.rupi.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rupi.api.dto.AccountResponse;
import com.rupi.config.SecurityConfig;
import com.rupi.security.AuthenticatedUser;
import com.rupi.security.JsonAccessDeniedHandler;
import com.rupi.security.JsonAuthenticationEntryPoint;
import com.rupi.security.JwtAuthenticationFilter;
import com.rupi.security.JwtService;
import com.rupi.service.AuthService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {StatusController.class, AccountController.class})
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    JsonAuthenticationEntryPoint.class,
    JsonAccessDeniedHandler.class,
    ApiSecurityTest.AuthServiceTestConfig.class
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
            "rupi.demo.max-balance=5000.00"
        })
class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Test
    void publicStatusDoesNotNeedToken() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void accountsMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void transactionsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"toAccountId\":\"00000000-0000-0000-0000-000000000001\",\"amount\":\"10.00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void demoFaucetWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/demo/faucet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accountsMeWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accountsMeWithValidTokenReturnsAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "alice");

        when(authService.currentAccount(any(AuthenticatedUser.class)))
                .thenReturn(new AccountResponse(
                        accountId, userId, "alice", new BigDecimal("1000.00"), true));

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.balance").value("1000.00"))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));
    }

    @TestConfiguration
    static class AuthServiceTestConfig {
        @Bean
        AuthService authService() {
            return Mockito.mock(AuthService.class);
        }
    }
}
