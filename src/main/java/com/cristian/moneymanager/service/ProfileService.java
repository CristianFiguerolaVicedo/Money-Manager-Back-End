package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.AuthDto;
import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.security.RateLimiter;
import com.cristian.moneymanager.util.InputRules;
import com.cristian.moneymanager.util.JwtUtil;
import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.email.EmailTemplate;
import com.cristian.moneymanager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RateLimiter rateLimiter;

    private static final Duration ACTIVATION_VALIDITY = Duration.ofHours(24);
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_LOCK_WINDOW = Duration.ofMinutes(15);
    public static final int MAX_EMAILS_PER_ADDRESS = 3;

    @Value("${money.manager.frontendUrl}")
    private String frontendUrl;

    public ProfileDto registerProfile(ProfileDto profileDto) {
        profileDto.setFullName(InputRules.validateFullName(profileDto.getFullName()));
        profileDto.setEmail(InputRules.validateEmail(profileDto.getEmail()));
        InputRules.validatePassword(profileDto.getPassword());
        profileDto.setId(null); // never let the client choose (or overwrite) an id

        ProfileEntity existing = profileRepository.findByEmail(profileDto.getEmail()).orElse(null);
        ProfileEntity profile;
        if (existing == null) {
            profile = toEntity(profileDto);
        } else if (Boolean.TRUE.equals(existing.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con este email");
        } else {
            // Registered before but never activated (e.g. the activation email was lost):
            // refresh the details and send a new activation link instead of blocking the email forever.
            ProfileEntity fresh = toEntity(profileDto);
            existing.setFullName(fresh.getFullName());
            existing.setPassword(fresh.getPassword());
            existing.setProfileImageUrl(fresh.getProfileImageUrl());
            profile = existing;
        }

        assignActivationToken(profile);
        profile = profileRepository.save(profile);
        sendActivationEmail(profile);
        return toDto(profile);
    }

    private void sendActivationEmail(ProfileEntity profile) {
        try {
            emailService.send(profile.getEmail(), buildActivationEmail(profile));
        } catch (RuntimeException e) {
            // The account is saved; registering again (or "resend") sends a new link.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Tu cuenta se ha creado, pero no hemos podido enviar el email de activación. Vuelve a intentarlo en unos minutos.", e);
        }
    }

    private EmailContent buildActivationEmail(ProfileEntity profile) {
        String link = frontendUrl + "/activate?token=" + profile.getActivationToken();
        return EmailTemplate.builder("Activa tu cuenta de Money Manager")
                .preheader("Confirma tu email para empezar a usar Money Manager")
                .greeting(profile.getFullName())
                .paragraph("Gracias por registrarte en Money Manager. Solo falta un paso: confirma que este email es tuyo para activar tu cuenta.")
                .button("Activar mi cuenta", link)
                .note("El enlace caduca en " + ACTIVATION_VALIDITY.toHours() + " horas y solo se puede usar una vez.")
                .note("Si no has creado esta cuenta, ignora este email.")
                .build();
    }

    /** Gives the profile a fresh single-use activation token. */
    private void assignActivationToken(ProfileEntity profile) {
        profile.setActivationToken(UUID.randomUUID().toString());
        profile.setActivationTokenExpiresAt(LocalDateTime.now().plus(ACTIVATION_VALIDITY));
    }

    public enum ActivationResult { ACTIVATED, EXPIRED, INVALID }

    public ActivationResult activateProfile(String activationToken) {
        if (activationToken == null || activationToken.isBlank()) {
            return ActivationResult.INVALID;
        }
        return profileRepository.findByActivationToken(activationToken.trim())
                .map(profile -> {
                    LocalDateTime expiresAt = profile.getActivationTokenExpiresAt();
                    if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
                        return ActivationResult.EXPIRED;
                    }
                    profile.setIsActive(true);
                    profile.setActivationToken(null); // single-use token
                    profile.setActivationTokenExpiresAt(null);
                    profileRepository.save(profile);
                    return ActivationResult.ACTIVATED;
                })
                .orElse(ActivationResult.INVALID);
    }

    /**
     * Sends a new activation link to an existing, not yet activated account.
     * Does nothing otherwise, and the caller always answers the same, so it cannot be used
     * to find out which emails are registered.
     */
    public void resendActivation(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }
        String normalized = email.trim();
        // At most a few emails per address per hour, so nobody can flood someone else's inbox
        if (!rateLimiter.tryAcquire("mail:" + normalized.toLowerCase(), MAX_EMAILS_PER_ADDRESS, Duration.ofHours(1))) {
            return;
        }
        profileRepository.findByEmail(normalized)
                .filter(profile -> !Boolean.TRUE.equals(profile.getIsActive()))
                .ifPresent(profile -> {
                    assignActivationToken(profile);
                    profileRepository.save(profile);
                    try {
                        emailService.send(profile.getEmail(), buildActivationEmail(profile));
                    } catch (RuntimeException e) {
                        log.error("Could not resend activation email to {}: {}", profile.getEmail(), e.getMessage());
                    }
                });
    }

    public boolean isAccountActive(String email) {
        return profileRepository.findByEmail(email)
                .map(ProfileEntity::getIsActive)
                .orElse(false);
    }

    public ProfileEntity getProfileByEmail(String email) {
        return profileRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Profile not found with email: " + email));
    }

    public ProfileEntity getCurrentProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Profile with email " + authentication.getName() + " not found"));
    }

    public ProfileDto getPublicProfile(String email) {
        ProfileEntity currentUser = null;

        if (email == null) {
            currentUser = getCurrentProfile();
        } else {
            currentUser = profileRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Profile with email " + email + " not found"));
        }

        return toDto(currentUser);
    }

    public ProfileEntity toEntity(ProfileDto profileDto) {
        return ProfileEntity.builder()
                .id(profileDto.getId())
                .fullName(profileDto.getFullName())
                .email(profileDto.getEmail())
                .password(passwordEncoder.encode(profileDto.getPassword()))
                .profileImageUrl(profileDto.getProfileImageUrl())
                .createdAt(profileDto.getCreatedAt())
                .updatedAt(profileDto.getUpdatedAt())
                .build();
    }

    public ProfileDto toDto(ProfileEntity profileEntity) {
        return ProfileDto.builder()
                .id(profileEntity.getId())
                .fullName(profileEntity.getFullName())
                .email(profileEntity.getEmail())
                .profileImageUrl(profileEntity.getProfileImageUrl())
                .createdAt(profileEntity.getCreatedAt())
                .updatedAt(profileEntity.getUpdatedAt())
                .emailDailyReminder(Boolean.TRUE.equals(profileEntity.getEmailDailyReminder()))
                .emailDailySummary(Boolean.TRUE.equals(profileEntity.getEmailDailySummary()))
                .emailMonthlyReport(Boolean.TRUE.equals(profileEntity.getEmailMonthlyReport()))
                .build();
    }

    /** New token for the logged-in user (the old one keeps working until it expires). */
    /** New short-lived access token plus the user, after a refresh token was accepted. */
    public Map<String, Object> sessionFor(ProfileEntity profile) {
        return Map.of(
                "token", jwtUtil.generateToken(profile.getEmail()),
                "user", toDto(profile)
        );
    }

    /**
     * Checks the password first and only then whether the account is active, so the
     * "not activated" answer is never given for a wrong password or an unknown email.
     */
    public Map<String, Object> authenticateAndGenerateToken(AuthDto authDto) {
        String email = authDto.getEmail() == null ? "" : authDto.getEmail().trim();
        String failuresKey = "login-fail:" + email.toLowerCase();
        if (rateLimiter.isLimited(failuresKey, MAX_LOGIN_FAILURES, LOGIN_LOCK_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Demasiados intentos fallidos con esta cuenta. Espera unos minutos o restablece tu contraseña.");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, authDto.getPassword()));
        } catch (AuthenticationException e) {
            rateLimiter.record(failuresKey);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email o contraseña incorrectos");
        }
        rateLimiter.reset(failuresKey);

        if (!isAccountActive(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE",
                    "Tu cuenta no está activada. Revisa tu email o pide un enlace nuevo.");
        }

        String token = jwtUtil.generateToken(email);
        return Map.of(
                "token", token,
                "user", getPublicProfile(email)
        );
    }
}
