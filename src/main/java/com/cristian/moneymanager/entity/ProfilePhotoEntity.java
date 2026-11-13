package com.cristian.moneymanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** A user's profile photo, kept apart from the profile so the bytes are only read when the photo is served. */
@Entity
@Table(name = "tbl_profile_photos")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfilePhotoEntity {

    @Id
    private Long profileId;

    @Column(nullable = false, unique = true, length = 64)
    private String photoKey;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
