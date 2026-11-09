package com.cristian.moneymanager.service;

import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.email.EmailTemplate;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.security.RateLimiter;
import com.cristian.moneymanager.util.InputRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * "Forgot password" flow: emails a single-use link valid for one hour.
 * Only a SHA-256 hash of the token is stored, so a database leak does not expose usable links.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_VALIDITY = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfileRepository profileRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    @Value("${money.manager.frontendUrl}")
    private String frontendUrl;

    /**
     * Sends a reset link if the email belongs to an account. Always behaves the same from the
     * outside, so the endpoint cannot be used to find out which emails are registered.
     */
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }

        String normalized = email.trim();
        // Shares the per-address budget with activation emails, so nobody can flood someone's inbox
        if (!rateLimiter.tryAcquire("mail:" + normalized.toLowerCase(), ProfileService.MAX_EMAILS_PER_ADDRESS, Duration.ofHours(1))) {
            return;
        }
        profileRepository.findByEmail(normalized).ifPresent(profile -> {
            String token = newToken();
            profile.setPasswordResetTokenHash(hash(token));
            profile.setPasswordResetExpiresAt(LocalDateTime.now().plus(TOKEN_VALIDITY));
            profileRepository.save(profile);

            try {
                emailService.send(profile.getEmail(), buildEmail(profile, token));
            } catch (RuntimeException e) {
                log.error("Could not send password reset email to {}: {}", profile.getEmail(), e.getMessage());
            }
        });
    }

    /** Sets a new password using a valid, unexpired token, then invalidates the token. */
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el enlace de recuperación");
        }
        InputRules.validatePassword(newPassword);

        ProfileEntity profile = profileRepository.findByPasswordResetTokenHash(hash(token.trim()))
                .filter(p -> p.getPasswordResetExpiresAt() != null && p.getPasswordResetExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El enlace no es válido o ha caducado. Pide uno nuevo."));

        profile.setPassword(passwordEncoder.encode(newPassword));
        profile.setPasswordResetTokenHash(null);
        profile.setPasswordResetExpiresAt(null);
        // Receiving the email proves the address is theirs, so an unactivated account becomes active
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            profile.setIsActive(true);
            profile.setActivationToken(null);
        }
        profileRepository.save(profile);
    }

    private EmailContent buildEmail(ProfileEntity profile, String token) {
        return EmailTemplate.builder("Restablece tu contraseña")
                .preheader("Elige una contraseña nueva para tu cuenta de Money Manager")
                .greeting(profile.getFullName())
                .paragraph("Hemos recibido una solicitud para restablecer la contraseña de tu cuenta de Money Manager.")
                .button("Elegir una contraseña nueva", frontendUrl + "/reset-password?token=" + token)
                .note("El enlace caduca en 1 hora y solo se puede usar una vez.")
                .note("Si no lo has pedido tú, ignora este email: tu contraseña no cambiará.")
                .build();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
