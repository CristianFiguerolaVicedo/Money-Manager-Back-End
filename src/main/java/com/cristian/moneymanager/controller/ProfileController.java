package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.AuthDto;
import com.cristian.moneymanager.dto.PasswordResetDto;
import com.cristian.moneymanager.dto.ProfileDto;
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

    @PostMapping(path = "/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDto authDto) {
        return ResponseEntity.ok(profileService.authenticateAndGenerateToken(authDto));
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
}
