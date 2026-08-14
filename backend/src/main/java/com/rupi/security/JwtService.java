package com.rupi.security;

import com.rupi.config.RupiProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(RupiProperties properties) {
        byte[] secretBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("rupi.jwt.secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = properties.jwt().expirationMs();
    }

    public String createToken(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            String username = claims.get("username", String.class);
            if (username == null || username.isBlank()) {
                throw new JwtException("Missing username claim");
            }
            return new AuthenticatedUser(userId, username);
        } catch (IllegalArgumentException | JwtException ex) {
            throw new JwtException("Invalid JWT", ex);
        }
    }
}
