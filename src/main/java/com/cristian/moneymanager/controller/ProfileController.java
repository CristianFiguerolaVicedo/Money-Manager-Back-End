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

    @GetMapping(path = "/activate")
    public ResponseEntity<String> activateProfile(@RequestParam String token) {
        boolean isActivated = profileService.activateProfile(token);
        if (isActivated) {
            return ResponseEntity.ok("Cuenta activada correctamente. Ya puedes iniciar sesión.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("El enlace de activación no es válido o ya se ha usado");
        }
    }

    @PostMapping(path = "/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDto authDto) {
        try {
            if (!profileService.isAccountActive(authDto.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message", "Tu cuenta no está activada. Revisa tu email y usa el enlace de activación."
                        ));
            }

            Map<String, Object> response = profileService.authenticateAndGenerateToken(authDto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));
        }
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
