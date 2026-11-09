package com.cristian.moneymanager;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile page endpoints and dashboard charts data, end to end against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class AccountFlowTests {

    private static final String PASSWORD = "abcdefg1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private ProfileEntity profile;
    private String token;

    @BeforeEach
    void createActiveUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        profile = profileRepository.save(ProfileEntity.builder()
                .fullName("Ana")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .isActive(true)
                .build());
        token = jwtUtil.generateToken(email);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void updatesNameAndPhoto() throws Exception {
        mockMvc.perform(auth(put("/profile"))
                        .content("{\"fullName\":\"  Ana López \",\"profileImageUrl\":\"https://res.cloudinary.com/x/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana López"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://res.cloudinary.com/x/a.png"));

        mockMvc.perform(auth(put("/profile")).content("{\"fullName\":\"Ana\",\"profileImageUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(put("/profile")).content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changesPasswordOnlyWithTheCurrentOne() throws Exception {
        mockMvc.perform(auth(put("/profile/password"))
                        .content("{\"currentPassword\":\"wrong-pass1\",\"newPassword\":\"nuevaClave9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        mockMvc.perform(auth(put("/profile/password"))
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(auth(put("/profile/password"))
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"nuevaClave9\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + profile.getEmail() + "\",\"password\":\"nuevaClave9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void deletesTheAccountAndItsData() throws Exception {
        String categoryJson = mockMvc.perform(auth(post("/categories"))
                        .content("{\"name\":\"Comida\",\"type\":\"expense\",\"icon\":\"🍕\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        Long categoryId = Long.valueOf(categoryJson.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(auth(post("/expenses"))
                        .content("{\"name\":\"Cena\",\"amount\":25.5,\"date\":\"" + LocalDate.now() + "\",\"categoryId\":" + categoryId + "}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(auth(delete("/profile")).content("{\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isBadRequest());
        assertThat(profileRepository.findById(profile.getId())).isPresent();

        mockMvc.perform(auth(delete("/profile")).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(profileRepository.findById(profile.getId())).isEmpty();
        assertThat(categoryRepository.findById(categoryId)).isEmpty();
        assertThat(expenseRepository.findByProfileIdOrderByDateDesc(profile.getId())).isEmpty();
    }

    @Test
    void dashboardIncludesChartData() throws Exception {
        String categoryJson = mockMvc.perform(auth(post("/categories"))
                        .content("{\"name\":\"Ocio\",\"type\":\"expense\",\"icon\":\"🎬\"}"))
                .andReturn().getResponse().getContentAsString();
        Long categoryId = Long.valueOf(categoryJson.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(auth(post("/expenses"))
                .content("{\"name\":\"Cine\",\"amount\":12,\"date\":\"" + LocalDate.now() + "\",\"categoryId\":" + categoryId + "}"));

        mockMvc.perform(auth(get("/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySummary.length()").value(6))
                .andExpect(jsonPath("$.monthlySummary[5].expense").value(12.0))
                .andExpect(jsonPath("$.expenseByCategory[0].categoryName").value("Ocio"))
                .andExpect(jsonPath("$.expenseByCategory[0].amount").value(12.0));
    }
}
