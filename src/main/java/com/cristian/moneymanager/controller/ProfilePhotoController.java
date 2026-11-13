package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.ProfileDto;
import com.cristian.moneymanager.entity.ProfilePhotoEntity;
import com.cristian.moneymanager.service.ProfilePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class ProfilePhotoController {

    private final ProfilePhotoService profilePhotoService;

    @PutMapping(path = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileDto> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profilePhotoService.upload(file));
    }

    @DeleteMapping("/profile/photo")
    public ResponseEntity<ProfileDto> remove() {
        return ResponseEntity.ok(profilePhotoService.remove());
    }

    /** Public so it works in an <img>; the key is random and changes with every new photo. */
    @GetMapping("/profile-photos/{key}")
    public ResponseEntity<byte[]> photo(@PathVariable String key) {
        ProfilePhotoEntity photo = profilePhotoService.get(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(photo.getData());
    }
}
