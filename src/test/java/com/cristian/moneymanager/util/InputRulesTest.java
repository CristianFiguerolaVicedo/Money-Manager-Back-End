package com.cristian.moneymanager.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg1", "Contraseña123", "12345678a", "ñandú2024"})
    void acceptsValidPasswords(String password) {
        assertThatCode(() -> InputRules.validatePassword(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc12", "abcdefgh", "12345678"})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> InputRules.validatePassword(password))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsPasswordsLongerThan72Bytes() {
        // 36 two-byte characters + a digit = 73 bytes, although only 37 characters
        String password = "ñ".repeat(36) + "1";
        assertThatThrownBy(() -> InputRules.validatePassword(password))
                .hasMessageContaining("demasiado larga");
        assertThatCode(() -> InputRules.validatePassword("a".repeat(71) + "1")).doesNotThrowAnyException();
    }

    @Test
    void trimsAndAcceptsValidEmail() {
        assertThat(InputRules.validateEmail("  ana@example.com ")).isEqualTo("ana@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ana", "ana@", "ana@example", "ana@example.c", "a na@example.com"})
    void rejectsInvalidEmails(String email) {
        assertThatThrownBy(() -> InputRules.validateEmail(email)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validatesFullName() {
        assertThat(InputRules.validateFullName("  Ana López ")).isEqualTo("Ana López");
        assertThatThrownBy(() -> InputRules.validateFullName(" ")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> InputRules.validateFullName("x".repeat(101))).isInstanceOf(ResponseStatusException.class);
    }
}
