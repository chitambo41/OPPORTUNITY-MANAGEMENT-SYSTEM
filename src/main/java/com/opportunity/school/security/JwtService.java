package com.opportunity.school.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 needs at least 32 bytes (256 bits).
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getEmail())
                .claim("uid", principal.getId())
                .claim("role", principal.getRole())
                .claim("teacherId", principal.getTeacherId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long uid = claims.get("uid", Long.class);
            String role = claims.get("role", String.class);
            Long teacherId = claims.get("teacherId", Long.class);
            if (uid == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new UserPrincipal(uid, claims.getSubject(), role, teacherId));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
