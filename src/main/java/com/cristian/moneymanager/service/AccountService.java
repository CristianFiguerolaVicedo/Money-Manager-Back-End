package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.AccountDtos.ChangePasswordRequest;
import com.cristian.moneymanager.dto.AccountDtos.DeleteAccountRequest;
import com.cristian.moneymanager.dto.AccountDtos.EmailPreferencesRequest;
import com.cristian.moneymanager.dto.AccountDtos.UpdateProfileRequest;
import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.email.EmailTemplate;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.exception.ApiException;
import com.cristian.moneymanager.repository.AttachmentRepository;
import com.cristian.moneymanager.repository.BudgetRepository;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.GoalContributionRepository;
import com.cristian.moneymanager.repository.GoalRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.repository.RecurringRepository;
import com.cristian.moneymanager.repository.RefreshTokenRepository;
import com.cristian.moneymanager.security.RateLimiter;
import com.cristian.moneymanager.util.InputRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** "My account" actions of the logged-in user: edit profile, change password, delete account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** Wrong current passwords allowed before the action is blocked for a while. */
    private static final int MAX_PASSWORD_FAILURES = 5;
    private static final Duration PASSWORD_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_IMAGE_URL_LENGTH = 255;

    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final AttachmentRepository attachmentRepository;
    private final GoalRepository goalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final RecurringRepository recurringRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final EmailService emailService;

    public ProfileDto updateProfile(UpdateProfileRequest request) {
        if (request == null) {
            throw badRequest("Faltan los datos del perfil");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        profile.setFullName(InputRules.validateFullName(request.fullName()));
        profile.setProfileImageUrl(validateImageUrl(request.profileImageUrl()));
        return profileService.toDto(profileRepository.save(profile));
    }

    public ProfileEntity changePassword(ChangePasswordRequest request) {
        if (request == null) {
            throw badRequest("Faltan las contraseñas");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        checkPassword(profile, request.currentPassword());

        InputRules.validatePassword(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), profile.getPassword())) {
            throw badRequest("La nueva contraseña debe ser distinta de la actual");
        }
        profile.setPassword(passwordEncoder.encode(request.newPassword()));
        // A pending reset link must not be usable to undo this change
        profile.setPasswordResetTokenHash(null);
        profile.setPasswordResetExpiresAt(null);
        profileRepository.save(profile);
        // Closes every session (refresh tokens and the access tokens issued so far)
        refreshTokenService.revokeAll(profile);

        notifyPasswordChanged(profile);
        return profile;
    }

    /** Turns the optional scheduled emails on or off (fields left null keep their value). */
    public ProfileDto updateEmailPreferences(EmailPreferencesRequest request) {
        if (request == null) {
            throw badRequest("Faltan las preferencias");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        if (request.dailyReminder() != null) {
            profile.setEmailDailyReminder(request.dailyReminder());
        }
        if (request.dailySummary() != null) {
            profile.setEmailDailySummary(request.dailySummary());
        }
        if (request.monthlyReport() != null) {
            profile.setEmailMonthlyReport(request.monthlyReport());
        }
        return profileService.toDto(profileRepository.save(profile));
    }

    /** One-click unsubscribe from the link in scheduled emails (no login): turns every optional email off. */
    public void unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            throw badRequest("El enlace no es válido");
        }
        ProfileEntity profile = profileRepository.findByEmailUnsubscribeToken(token.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El enlace no es válido o ya no existe"));
        profile.setEmailDailyReminder(false);
        profile.setEmailDailySummary(false);
        profile.setEmailMonthlyReport(false);
        profileRepository.save(profile);
    }

    /** Deletes the user and everything they own (incomes, expenses, categories). */
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        ProfileEntity profile = profileService.getCurrentProfile();
        checkPassword(profile, request == null ? null : request.password());

        Long profileId = profile.getId();
        refreshTokenRepository.deleteByProfileId(profileId);
        attachmentRepository.deleteByProfileId(profileId);
        goalContributionRepository.deleteByProfileId(profileId);
        goalRepository.deleteByProfileId(profileId);
        budgetRepository.deleteByProfileId(profileId);
        recurringRepository.deleteByProfileId(profileId);
        incomeRepository.deleteByProfileId(profileId);
        expenseRepository.deleteByProfileId(profileId);
        categoryRepository.deleteByProfileId(profileId);
        profileRepository.delete(profile);
        log.info("Deleted account {}", profileId);
    }

    /** Throws 400 WRONG_PASSWORD (not 401, which would log the user out) and limits guessing. */
    private void checkPassword(ProfileEntity profile, String password) {
        String failuresKey = "account-password-fail:" + profile.getId();
        if (rateLimiter.isLimited(failuresKey, MAX_PASSWORD_FAILURES, PASSWORD_FAILURE_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Demasiados intentos con una contraseña incorrecta. Espera unos minutos.");
        }
        if (password == null || password.isEmpty() || !passwordEncoder.matches(password, profile.getPassword())) {
            rateLimiter.record(failuresKey);
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "La contraseña actual no es correcta");
        }
        rateLimiter.reset(failuresKey);
    }

    static String validateImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://") || trimmed.length() > MAX_IMAGE_URL_LENGTH || trimmed.matches(".*\\s.*")) {
            throw badRequest("La URL de la foto no es válida");
        }
        return trimmed;
    }

    private void notifyPasswordChanged(ProfileEntity profile) {
        try {
            emailService.send(profile.getEmail(), EmailTemplate.builder("Tu contraseña ha cambiado")
                    .preheader("Se ha cambiado la contraseña de tu cuenta de Money Manager")
                    .greeting(profile.getFullName())
                    .paragraph("Te confirmamos que la contraseña de tu cuenta de Money Manager se acaba de cambiar.")
                    .paragraph("Si no has sido tú, restablece tu contraseña cuanto antes desde la pantalla de inicio de sesión.")
                    .build());
        } catch (RuntimeException e) {
            // The change is already saved; the email is only a courtesy
            log.error("Could not send password-changed email to {}: {}", profile.getEmail(), e.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
