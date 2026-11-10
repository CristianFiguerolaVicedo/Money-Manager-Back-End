package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_profiles")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    @Column(unique = true)
    private String email;
    private String password;
    private String profileImageUrl;
    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private Boolean isActive;
    private String activationToken;
    /** When the activation link stops working (null for links sent before expiry existed). */
    private LocalDateTime activationTokenExpiresAt;
    /** SHA-256 (hex) of the password reset token; the token itself is only ever in the email. */
    private String passwordResetTokenHash;
    private LocalDateTime passwordResetExpiresAt;

    /** Scheduled emails the user opted in to (all off by default). */
    private Boolean emailDailyReminder;
    private Boolean emailDailySummary;
    private Boolean emailMonthlyReport;
    /** Random token for the "unsubscribe" link in scheduled emails (works without logging in). */
    private String emailUnsubscribeToken;

    @PrePersist
    public void prePersist() {
        if (this.isActive == null) {
            isActive = false;
        }
        if (emailDailyReminder == null) {
            emailDailyReminder = false;
        }
        if (emailDailySummary == null) {
            emailDailySummary = false;
        }
        if (emailMonthlyReport == null) {
            emailMonthlyReport = false;
        }
    }
}
