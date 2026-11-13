package com.cristian.moneymanager;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Session renewal with the HttpOnly refresh cookie, rotation, reuse detection and revocation (audit). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class SessionFlowTests {

    private static final String PASSWORD = "abcdefg1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void createActiveUser() {
        email = "test-" + UUID.randomUUID() + "@example.com";
        profileRepository.save(ProfileEntity.builder().fullName("Ana").email(email)
                .password(passwordEncoder.encode(PASSWORD)).isActive(true).build());
    }

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse();
    }

    private static String token(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private static Cookie sessionCookie(MockHttpServletResponse response) {
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("mm_session=").contains("HttpOnly").contains("SameSite=Strict").contains("Secure");
        String value = header.substring("mm_session=".length(), header.indexOf(';'));
        return new Cookie("mm_session", value);
    }

    @Test
    void refreshCookieRenewsTheSessionAndRotates() throws Exception {
        Cookie first = sessionCookie(login());

        MockHttpServletResponse refreshed = mockMvc.perform(post("/refresh-token").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse();
        Cookie second = sessionCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(get("/profile").header("Authorization", "Bearer " + token(refreshed))).andExpect(status().isOk());
        mockMvc.perform(post("/refresh-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/refresh-token").cookie(new Cookie("mm_session", "made-up"))).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() throws Exception {
        Cookie first = sessionCookie(login());
        Cookie second = sessionCookie(mockMvc.perform(post("/refresh-token").cookie(first)).andReturn().getResponse());

        // Pretend the first token was rotated long ago (outside the grace period for two open tabs)
        Long profileId = profileRepository.findByEmail(email).orElseThrow().getId();
        refreshTokenRepository.findAll().stream()
                .filter(t -> t.getReplacedAt() != null && t.getProfile().getId().equals(profileId))
                .forEach(t -> {
                    t.setReplacedAt(LocalDateTime.now().minusMinutes(5));
                    refreshTokenRepository.save(t);
                });

        mockMvc.perform(post("/refresh-token").cookie(first)).andExpect(status().isUnauthorized());
        // The thief's use also killed the legitimate token of the same session
        mockMvc.perform(post("/refresh-token").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheCookie() throws Exception {
        Cookie cookie = sessionCookie(login());
        mockMvc.perform(post("/logout").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/refresh-token").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePasswordClosesOtherSessions() throws Exception {
        MockHttpServletResponse otherDevice = login();
        Cookie otherCookie = sessionCookie(otherDevice);
        String otherToken = token(otherDevice);
        Thread.sleep(1100); // tokens carry second-precision "issued at"

        String current = token(login());
        MockHttpServletResponse changed = mockMvc.perform(put("/profile/password").header("Authorization", "Bearer " + current)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"otraClave99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse();

        // Old access token and old refresh cookie stop working
        mockMvc.perform(get("/profile").header("Authorization", "Bearer " + otherToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/refresh-token").cookie(otherCookie)).andExpect(status().isUnauthorized());
        // This browser keeps a working session
        mockMvc.perform(get("/profile").header("Authorization", "Bearer " + token(changed))).andExpect(status().isOk());
        mockMvc.perform(post("/refresh-token").cookie(sessionCookie(changed))).andExpect(status().isOk());
    }
}
