package com.rupi.service;

import com.rupi.config.RupiProperties;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redis;
    private final RupiProperties properties;
    private final DefaultRedisScript<List> script;

    public RateLimiterService(StringRedisTemplate redis, RupiProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(List.class);
        this.script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("redis/token_bucket.lua")));
    }

    public RateLimitResult tryConsume(UUID userId) {
        try {
            int capacity = properties.rateLimit().capacity();
            int refillPerSecond = properties.rateLimit().refillPerSecond();
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                    script,
                    Collections.singletonList("rate:" + userId),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    "1");
            if (result == null || result.size() < 3) {
                return RateLimitResult.allowed(capacity);
            }
            boolean allowed = result.get(0) != null && result.get(0) == 1L;
            long remaining = result.get(1) == null ? 0L : result.get(1);
            long retryAfterMs = result.get(2) == null ? 0L : result.get(2);
            if (allowed) {
                return RateLimitResult.allowed(remaining);
            }
            long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999) / 1000);
            return RateLimitResult.limited(remaining, retryAfterSeconds);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable; allowing request", ex);
            return RateLimitResult.allowed(properties.rateLimit().capacity());
        }
    }

    public record RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {
        public static RateLimitResult allowed(long remaining) {
            return new RateLimitResult(true, remaining, 0);
        }

        public static RateLimitResult limited(long remaining, long retryAfterSeconds) {
            return new RateLimitResult(false, remaining, retryAfterSeconds);
        }
    }
}
