package com.cristian.moneymanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileDto {

    private Long id;
    private String fullName;
    private String email;
    private String password;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean emailDailyReminder;
    private Boolean emailDailySummary;
    private Boolean emailMonthlyReport;

    /** Signup only: the "I accept the privacy policy and terms" checkbox. Never returned. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Boolean acceptTerms;
    private LocalDateTime termsAcceptedAt;
    private String termsVersion;
}
