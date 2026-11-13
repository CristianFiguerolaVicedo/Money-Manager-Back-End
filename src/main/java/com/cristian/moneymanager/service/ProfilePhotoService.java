package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.entity.ProfilePhotoEntity;
import com.cristian.moneymanager.repository.ProfilePhotoRepository;
import com.cristian.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Profile photos stored by the backend instead of an unsigned Cloudinary upload from the browser (audit).
 * Only JPEG, PNG and WebP up to 1 MB, checked by their first bytes.
 */
@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

    static final int MAX_BYTES = 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final ProfilePhotoRepository profilePhotoRepository;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Transactional
    public ProfileDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Elige una imagen");
        }
        if (file.getSize() > MAX_BYTES) {
            throw badRequest("La foto no puede ocupar más de 1 MB");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw badRequest("No se ha podido leer la imagen");
        }
        String contentType = AttachmentService.detectType(data);
        if (contentType == null || !contentType.startsWith("image/")) {
            throw badRequest("La foto debe ser una imagen JPG, PNG o WebP");
        }

        ProfileEntity profile = profileService.getCurrentProfile();
        ProfilePhotoEntity photo = profilePhotoRepository.findById(profile.getId())
                .orElseGet(() -> ProfilePhotoEntity.builder().profileId(profile.getId()).build());
        photo.setPhotoKey(newKey());
        photo.setContentType(contentType);
        photo.setData(data);
        profilePhotoRepository.save(photo);

        profile.setProfileImageUrl(contextPath + "/profile-photos/" + photo.getPhotoKey());
        return profileService.toDto(profileRepository.save(profile));
    }

    @Transactional
    public ProfileDto remove() {
        ProfileEntity profile = profileService.getCurrentProfile();
        profilePhotoRepository.deleteById(profile.getId());
        profile.setProfileImageUrl(null);
        return profileService.toDto(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public ProfilePhotoEntity get(String key) {
        if (key == null || key.length() > 64) {
            throw notFound();
        }
        return profilePhotoRepository.findByPhotoKey(key).orElseThrow(ProfilePhotoService::notFound);
    }

    private static String newKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada");
    }
}
