package com.trustsign.auth;

import com.trustsign.common.Role;
import com.trustsign.config.TrustSignProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Crée et vérifie les jetons JWT (HMAC-SHA256). */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(TrustSignProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("trustdoc.jwt.secret doit contenir au moins 32 caractères.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = props.jwt().ttlMinutes();
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.username())
            .claim("name", user.displayName())
            .claim("role", user.role().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    public Optional<AppUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AppUser(c.getSubject(), c.get("name", String.class), Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
