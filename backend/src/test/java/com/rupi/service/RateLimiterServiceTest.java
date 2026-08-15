package com.rupi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.rupi.config.RupiProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redis;

    private RateLimiterService rateLimiterService;

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
                        new BigDecimal("5000.00")),
                new RupiProperties.RateLimit(10, 10));
        rateLimiterService = new RateLimiterService(redis, properties);
    }

    @Test
    void allowsWhenRedisReturnsAllowed() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 9L, 0L));

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(UUID.randomUUID());

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(9L);
    }

    @Test
    void limitsWhenRedisReturnsDenied() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 0L, 150L));

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(UUID.randomUUID());

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(1L);
    }

    @Test
    void failsOpenWhenRedisThrows() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(UUID.randomUUID());

        assertThat(result.allowed()).isTrue();
    }
}
