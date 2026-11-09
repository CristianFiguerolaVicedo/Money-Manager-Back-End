package com.cristian.moneymanager.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);

    private static JwtUtil jwtUtil(String secret, int expirationMillis) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "jwtSecret", secret);
        ReflectionTestUtils.setField(util, "expirationTime", expirationMillis);
        util.initSigningKey();
        return util;
    }

    private static UserDetails user(String email) {
        return new User(email, "x", List.of());
    }

    @Test
    void generatedTokenIsValidForItsUser() {
        JwtUtil util = jwtUtil(SECRET, 60_000);
        String token = util.generateToken("ana@example.com");

        assertThat(util.extractUsername(token)).isEqualTo("ana@example.com");
        assertThat(util.validateToken(token, user("ana@example.com"))).isTrue();
        assertThat(util.validateToken(token, user("otro@example.com"))).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil util = jwtUtil(SECRET, -1_000);
        String token = util.generateToken("ana@example.com");
        assertThat(util.validateToken(token, user("ana@example.com"))).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = jwtUtil(Base64.getEncoder().encodeToString("x".repeat(48).getBytes()), 60_000)
                .generateToken("ana@example.com");
        assertThat(jwtUtil(SECRET, 60_000).validateToken(foreign, user("ana@example.com"))).isFalse();
    }

    @Test
    void worksWithoutSecretUsingRandomKey() {
        JwtUtil util = jwtUtil("", 60_000);
        assertThat(util.validateToken(util.generateToken("ana@example.com"), user("ana@example.com"))).isTrue();
    }

    @Test
    void rejectsShortOrInvalidSecrets() {
        assertThatThrownBy(() -> jwtUtil(Base64.getEncoder().encodeToString(new byte[16]), 60_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("too short");
        assertThatThrownBy(() -> jwtUtil("not base64 !!", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
