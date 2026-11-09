package com.cristian.moneymanager.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Validation rules for account data, shared by registration and password reset. */
public final class InputRules {

    public static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes; longer passwords would be silently truncated. */
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_NAME_LENGTH = 100;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private InputRules() {
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw badRequest("La contraseña es obligatoria");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw badRequest("La contraseña es demasiado larga (máximo " + MAX_PASSWORD_BYTES + " caracteres)");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw badRequest("La contraseña debe incluir al menos una letra y un número");
        }
    }

    /** Returns the trimmed email after checking its format. */
    public static String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw badRequest("El email es obligatorio");
        }
        String trimmed = email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(trimmed).matches()) {
            throw badRequest("El email no tiene un formato válido");
        }
        return trimmed;
    }

    /** Returns the trimmed full name after checking it. */
    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw badRequest("El nombre completo es obligatorio");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw badRequest("El nombre es demasiado largo (máximo " + MAX_NAME_LENGTH + " caracteres)");
        }
        return trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
