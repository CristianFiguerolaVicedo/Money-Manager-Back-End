package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A refresh token (only its hash). See RefreshTokenService. */
@Entity
@Table(name = "tbl_refresh_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Set when a rotation replaced this token. Reusing it after the grace period means it was copied. */
    private LocalDateTime replacedAt;

    /** Set on logout, password change or detected theft. The token is never accepted again. */
    private LocalDateTime revokedAt;
}
