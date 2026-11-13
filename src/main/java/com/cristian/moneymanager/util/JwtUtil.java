package com.cristian.moneymanager.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${auth.token.jwtSecret:}")
    private String jwtSecret;

    @Value("${auth.token.expirationInMils}")
    private int expirationTime;

    private Key signingKey;

    @PostConstruct
    void initSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            // No secret configured: use a random key so the app still starts (handy locally).
            // Tokens then stop working whenever the app restarts, so set JWT_SECRET in production.
            signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            log.warn("JWT_SECRET is not set: using a random signing key. Users will be logged out on every restart. "
                    + "Generate one with `openssl rand -base64 64` and set JWT_SECRET.");
            return;
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64 (generate it with `openssl rand -base64 64`)", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET is too short: it must decode to at least "
                    + MIN_SECRET_BYTES + " bytes (generate it with `openssl rand -base64 64`)");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(jwt)
                .getBody()
                .getSubject();
    }

    /** When the token was issued (second precision), or null if it cannot be read. */
    public Date extractIssuedAt(String jwt) {
        try {
            return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(jwt).getBody().getIssuedAt();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateToken(String jwt, UserDetails userDetails) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            String username = claims.getSubject();

            return (username.equals(userDetails.getUsername()) && !claims.getExpiration().before(new Date()));

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
