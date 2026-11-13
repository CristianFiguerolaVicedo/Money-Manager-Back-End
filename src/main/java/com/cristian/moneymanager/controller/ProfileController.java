package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.AccountDtos;
import com.cristian.moneymanager.dto.AuthDto;
import com.cristian.moneymanager.dto.PasswordResetDto;
import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.security.SessionCookie;
import com.cristian.moneymanager.service.AccountService;
import com.cristian.moneymanager.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.cristian.moneymanager.service.PasswordResetService;
import com.cristian.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PasswordResetService passwordResetService;
    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;
    private final SessionCookie sessionCookie;

    @PostMapping(path = "/register")
    public ResponseEntity<ProfileDto> registerProfile(@RequestBody ProfileDto profileDto) {
        ProfileDto registeredProfile = profileService.registerProfile(profileDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredProfile);
    }

    /** Called by the front-end /activate page with the token from the email. */
    @GetMapping(path = "/activate")
    public ResponseEntity<Map<String, Object>> activateProfile(@RequestParam(required = false) String token) {
        return switch (profileService.activateProfile(token)) {
            case ACTIVATED -> ResponseEntity.ok(Map.of("message", "¡Cuenta activada! Ya puedes iniciar sesión."));
            case EXPIRED -> ResponseEntity.status(HttpStatus.GONE).body(Map.of("code", "ACTIVATION_EXPIRED",
                    "message", "El enlace de activación ha caducado. Pide uno nuevo."));
            case INVALID -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "ACTIVATION_INVALID",
                    "message", "El enlace de activación no es válido o ya se ha usado."));
        };
    }

    /** Returns a 15-minute access token and sets the 30-day refresh cookie. */
    @PostMapping(path = "/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDto authDto,
                                                     HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> session = profileService.authenticateAndGenerateToken(authDto);
        ProfileEntity profile = profileService.getProfileByEmail(authDto.getEmail().trim());
        sessionCookie.set(request, response, refreshTokenService.issue(profile));
        return ResponseEntity.ok(session);
    }

    /**
     * Uses the refresh cookie (no Authorization header needed): rotates it and returns a new access token.
     * The web calls it on load and whenever a request gets a 401, so the session renews silently.
     */
    @PostMapping(path = "/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            RefreshTokenService.Issued issued = refreshTokenService.rotate(sessionCookie.read(request));
            sessionCookie.set(request, response, issued.rawToken());
            return ResponseEntity.ok(profileService.sessionFor(issued.profile()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            sessionCookie.clear(request, response);
            throw e;
        }
    }

    /** Ends the session in this browser (revokes the refresh cookie). */
    @PostMapping(path = "/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.revoke(sessionCookie.read(request));
        sessionCookie.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    /** Always answers the same, whether or not the email exists or is already active. */
    @PostMapping(path = "/resend-activation")
    public ResponseEntity<Map<String, Object>> resendActivation(@RequestBody PasswordResetDto dto) {
        profileService.resendActivation(dto.getEmail());
        return ResponseEntity.ok(Map.of("message",
                "Si tu cuenta está pendiente de activar, te hemos enviado un enlace nuevo."));
    }

    /** Always answers the same, whether or not the email exists. */
    @PostMapping(path = "/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody PasswordResetDto dto) {
        passwordResetService.requestReset(dto.getEmail());
        return ResponseEntity.ok(Map.of("message",
                "Si existe una cuenta con ese email, te hemos enviado un enlace para restablecer la contraseña."));
    }

    @PostMapping(path = "/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody PasswordResetDto dto) {
        passwordResetService.resetPassword(dto.getToken(), dto.getPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Ya puedes iniciar sesión."));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileDto> getPublicProfile() {
        ProfileDto profileDto = profileService.getPublicProfile(null);
        return ResponseEntity.ok(profileDto);
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileDto> updateProfile(@RequestBody AccountDtos.UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateProfile(request));
    }

    @PutMapping("/profile/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody AccountDtos.ChangePasswordRequest request,
                                                              HttpServletRequest httpRequest, HttpServletResponse response) {
        // Every other session is closed; this browser gets a fresh one so the user stays logged in
        ProfileEntity profile = accountService.changePassword(request);
        sessionCookie.set(httpRequest, response, refreshTokenService.issue(profile));
        Map<String, Object> session = new java.util.HashMap<>(profileService.sessionFor(profile));
        session.put("message", "Contraseña actualizada. Se ha cerrado la sesión en los demás dispositivos.");
        return ResponseEntity.ok(session);
    }

    @PutMapping("/profile/email-preferences")
    public ResponseEntity<ProfileDto> updateEmailPreferences(@RequestBody AccountDtos.EmailPreferencesRequest request) {
        return ResponseEntity.ok(accountService.updateEmailPreferences(request));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestBody(required = false) AccountDtos.UnsubscribeRequest request) {
        accountService.unsubscribe(request == null ? null : request.token());
        return ResponseEntity.ok(Map.of("message", "Ya no recibirás más emails programados"));
    }

    @DeleteMapping("/profile")
    public ResponseEntity<Void> deleteAccount(@RequestBody(required = false) AccountDtos.DeleteAccountRequest request,
                                              HttpServletRequest httpRequest, HttpServletResponse response) {
        accountService.deleteAccount(request);
        sessionCookie.clear(httpRequest, response);
        return ResponseEntity.noContent().build();
    }
}
