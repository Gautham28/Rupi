package com.rupi.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rupi")
public record RupiProperties(Jwt jwt, Cors cors, Demo demo, RateLimit rateLimit) {

    public record Jwt(String secret, long expirationMs) {}

    public record Cors(String allowedOrigins) {}

    public record Demo(
            boolean enabled,
            BigDecimal signupCredits,
            BigDecimal faucetAmount,
            int faucetCooldownHours,
            BigDecimal maxBalance) {}

    public record RateLimit(int capacity, int refillPerSecond) {}
}
