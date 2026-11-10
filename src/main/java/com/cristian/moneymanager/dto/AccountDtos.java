package com.cristian.moneymanager.dto;

/** Request bodies of the "my account" endpoints (profile page). */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record UpdateProfileRequest(String fullName, String profileImageUrl) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record DeleteAccountRequest(String password) {
    }

    public record EmailPreferencesRequest(Boolean dailyReminder, Boolean dailySummary, Boolean monthlyReport) {
    }

    public record UnsubscribeRequest(String token) {
    }
}
