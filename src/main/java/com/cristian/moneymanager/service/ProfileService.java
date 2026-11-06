package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.AuthDto;
import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${app.activation.url}")
    private String activationURL;

    public ProfileDto registerProfile(ProfileDto profileDto) {
        if (profileDto.getFullName() == null || profileDto.getFullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        if (profileDto.getEmail() == null || profileDto.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (profileDto.getPassword() == null || profileDto.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        profileDto.setEmail(profileDto.getEmail().trim());
        profileDto.setId(null); // never let the client choose (or overwrite) an id

        ProfileEntity existing = profileRepository.findByEmail(profileDto.getEmail()).orElse(null);
        ProfileEntity profile;
        if (existing == null) {
            profile = toEntity(profileDto);
        } else if (Boolean.TRUE.equals(existing.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        } else {
            // Registered before but never activated (e.g. the activation email was lost):
            // refresh the details and send a new activation link instead of blocking the email forever.
            ProfileEntity fresh = toEntity(profileDto);
            existing.setFullName(fresh.getFullName());
            existing.setPassword(fresh.getPassword());
            existing.setProfileImageUrl(fresh.getProfileImageUrl());
            profile = existing;
        }

        profile.setActivationToken(UUID.randomUUID().toString());
        profile = profileRepository.save(profile);
        sendActivationEmail(profile);
        return toDto(profile);
    }

    private void sendActivationEmail(ProfileEntity profile) {
        String activationLink = activationURL + "/api/v1.0/activate?token=" + profile.getActivationToken();
        String subject = "Activate your Money Manager account";
        String body = "Click on the following link to activate your account: " + activationLink;
        try {
            emailService.sendEmail(profile.getEmail(), subject, body);
        } catch (RuntimeException e) {
            // The account is saved; registering again with the same email re-sends the link.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Your account was created but the activation email could not be sent. Please try registering again in a few minutes.", e);
        }
    }

    public boolean activateProfile(String activationToken) {
        return profileRepository.findByActivationToken(activationToken)
                .map(profile -> {
                    profile.setIsActive(true);
                    profile.setActivationToken(null); // single-use token
                    profileRepository.save(profile);
                    return true;
                })
                .orElse(false);
    }

    public boolean isAccountActive(String email) {
        return profileRepository.findByEmail(email)
                .map(ProfileEntity::getIsActive)
                .orElse(false);
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

        return ProfileDto.builder()
                .id(currentUser.getId())
                .fullName(currentUser.getFullName())
                .email(currentUser.getEmail())
                .profileImageUrl(currentUser.getProfileImageUrl())
                .createdAt(currentUser.getCreatedAt())
                .updatedAt(currentUser.getUpdatedAt())
                .build();
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
                .build();
    }

    public Map<String, Object> authenticateAndGenerateToken(AuthDto authDto) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authDto.getEmail(), authDto.getPassword()));
            String token = jwtUtil.generateToken(authDto.getEmail());
            return Map.of(
                    "token", token,
                    "user", getPublicProfile(authDto.getEmail())
            );
        } catch (Exception e) {
            throw new RuntimeException("Invalid email or password");
        }
    }
}
