package com.cristian.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of POST /forgot-password ({email}) and POST /reset-password ({token, password}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetDto {
    private String email;
    private String token;
    private String password;
}
