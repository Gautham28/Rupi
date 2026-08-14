package com.rupi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyCache {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCache.class);
    private static final String COMPLETED = "COMPLETED";
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final StringRedisTemplate redis;

    public IdempotencyCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String fingerprint(UUID userId, String route, String canonicalBody) {
        String raw = userId + "|" + route + "|" + canonicalBody;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public CachedLookup lookup(UUID userId, UUID key) {
        try {
            String value = redis.opsForValue().get(redisKey(userId, key));
            if (value == null) {
                return CachedLookup.miss();
            }
            int split = value.indexOf('|');
            if (split < 0) {
                return CachedLookup.miss();
            }
            String state = value.substring(0, split);
            String payload = value.substring(split + 1);
            if (IN_PROGRESS.equals(state)) {
                return CachedLookup.inProgress();
            }
            if (COMPLETED.equals(state)) {
                return CachedLookup.completed(payload);
            }
            return CachedLookup.miss();
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency lookup failed; continuing with database", ex);
            return CachedLookup.miss();
        }
    }

    public boolean tryBegin(UUID userId, UUID key) {
        try {
            Boolean ok = redis.opsForValue()
                    .setIfAbsent(redisKey(userId, key), IN_PROGRESS + "|", java.time.Duration.ofSeconds(30));
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency begin failed; continuing with database", ex);
            return true;
        }
    }

    public void complete(UUID userId, UUID key, String responseJson) {
        try {
            redis.opsForValue()
                    .set(
                            redisKey(userId, key),
                            COMPLETED + "|" + responseJson,
                            java.time.Duration.ofHours(24));
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency complete failed; database remains source of truth", ex);
        }
    }

    public void clear(UUID userId, UUID key) {
        try {
            redis.delete(redisKey(userId, key));
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency clear failed", ex);
        }
    }

    private static String redisKey(UUID userId, UUID key) {
        return "idempotency:" + userId + ":" + key;
    }

    public record CachedLookup(Kind kind, String responseJson) {
        public enum Kind {
            MISS,
            IN_PROGRESS,
            COMPLETED
        }

        static CachedLookup miss() {
            return new CachedLookup(Kind.MISS, null);
        }

        static CachedLookup inProgress() {
            return new CachedLookup(Kind.IN_PROGRESS, null);
        }

        static CachedLookup completed(String responseJson) {
            return new CachedLookup(Kind.COMPLETED, responseJson);
        }
    }
}
