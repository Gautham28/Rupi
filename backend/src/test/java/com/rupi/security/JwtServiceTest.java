package com.rupi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rupi.config.RupiProperties;
import io.jsonwebtoken.JwtException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        RupiProperties properties = new RupiProperties(
                new RupiProperties.Jwt("change-me-dev-only-use-at-least-32-chars!!", 86_400_000L),
                new RupiProperties.Cors("http://localhost:5173"),
                new RupiProperties.Demo(
                        true,
                        new BigDecimal("1000.00"),
                        new BigDecimal("500.00"),
                        24,
                        new BigDecimal("5000.00")));
        jwtService = new JwtService(properties);
    }

    @Test
    void roundTripsUserClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "alice");

        AuthenticatedUser user = jwtService.parseToken(token);

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.username()).isEqualTo("alice");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createToken(UUID.randomUUID(), "alice") + "x";

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
    }
}
