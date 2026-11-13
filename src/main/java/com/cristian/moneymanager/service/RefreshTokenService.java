package com.cristian.moneymanager.service;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.entity.RefreshTokenEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Long-lived refresh tokens (audit: "session of 1 h without renewal").
 *
 * - The raw token only travels in an HttpOnly cookie; the database keeps its SHA-256.
 * - Every use rotates it (old one revoked, new one issued in the same family).
 * - Presenting a token that was already rotated means it was copied: the whole family is revoked.
 *   A short grace period covers two tabs refreshing at the same moment.
 * - Password change/reset and account deletion revoke everything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public static final Duration LIFETIME = Duration.ofDays(30);
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final ProfileRepository profileRepository;

    public record Issued(String rawToken, ProfileEntity profile) {
    }

    @Transactional
    public String issue(ProfileEntity profile) {
        return create(profile, UUID.randomUUID().toString());
    }

    /** Validates and rotates a refresh token. Throws 401 when it cannot be used. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Issued rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw unauthorized();
        }
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::unauthorized);
        LocalDateTime now = now();
        ProfileEntity profile = token.getProfile();

        if (token.getRevokedAt() != null) {
            boolean concurrentRefresh = token.getRevokedAt().isAfter(now.minus(ROTATION_GRACE));
            if (!concurrentRefresh) {
                log.warn("Reused refresh token for profile {}: revoking its session family", profile.getId());
                refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
                throw unauthorized();
            }
        } else if (token.getExpiresAt().isBefore(now)) {
            throw unauthorized();
        }
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            throw unauthorized();
        }

        if (token.getRevokedAt() == null) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        }
        return new Issued(create(profile, token.getFamilyId()), profile);
    }

    /** Logout of this browser: revokes the presented token's family. Never fails. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), now()));
    }

    /** Logs the user out everywhere: refresh tokens revoked and access tokens issued until now rejected. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAll(ProfileEntity profile) {
        LocalDateTime now = now();
        refreshTokenRepository.revokeAllForProfile(profile.getId(), now);
        profile.setTokensValidAfter(now);
        profileRepository.save(profile);
    }

    private String create(ProfileEntity profile, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .profile(profile)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .expiresAt(now().plus(LIFETIME))
                .build());
        return raw;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDateTime now() {
        // Second precision, the same as the JWT "iat" claim it is compared with
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ha caducado. Vuelve a iniciar sesión.");
    }
}
