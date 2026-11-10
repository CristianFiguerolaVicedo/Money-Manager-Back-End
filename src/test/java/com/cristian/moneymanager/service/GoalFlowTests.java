package com.cristian.moneymanager.service;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.GoalRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Savings goals (F14), end to end against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class GoalFlowTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private ProfileEntity profile;
    private String token;

    @BeforeEach
    void createActiveUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        profile = profileRepository.save(ProfileEntity.builder()
                .fullName("Ana").email(email).password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        token = jwtUtil.generateToken(email);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private Long createGoal(String body) throws Exception {
        String json = mockMvc.perform(auth(post("/goals")).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    @Test
    void contributionsAndWithdrawalsUpdateProgress() throws Exception {
        Long id = createGoal("{\"name\":\"  Viaje a Japón \",\"targetAmount\":2000}");

        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":500,\"note\":\"Paga extra\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Viaje a Japón"))
                .andExpect(jsonPath("$.savedAmount").value(500.0))
                .andExpect(jsonPath("$.percent").value(25))
                .andExpect(jsonPath("$.contributions[0].note").value("Paga extra"));

        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":-100}"))
                .andExpect(jsonPath("$.savedAmount").value(400.0));

        // Cannot take out more than what was saved
        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":-1000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());

        String goal = mockMvc.perform(auth(get("/goals"))).andExpect(jsonPath("$[0].contributions.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Long deposit = Long.valueOf(goal.replaceAll(".*\"contributions\":\\[\\{\"id\":\\d+.*?\\{\"id\":(\\d+).*", "$1"));

        // Deleting the 500 deposit would leave -100 saved
        mockMvc.perform(auth(delete("/goals/" + id + "/contributions/" + deposit))).andExpect(status().isBadRequest());
    }

    @Test
    void editValidateAndDelete() throws Exception {
        mockMvc.perform(auth(post("/goals")).content("{\"name\":\"\",\"targetAmount\":100}")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(post("/goals")).content("{\"name\":\"Coche\",\"targetAmount\":0}")).andExpect(status().isBadRequest());

        Long id = createGoal("{\"name\":\"Coche\",\"targetAmount\":6000}");
        mockMvc.perform(auth(put("/goals/" + id)).content("{\"name\":\"Coche nuevo\",\"targetAmount\":8000,\"deadline\":\"2030-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(8000.0))
                .andExpect(jsonPath("$.deadline").value("2030-01-01"))
                .andExpect(jsonPath("$.monthlyNeeded").isNumber());

        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":50}")).andExpect(status().isOk());
        mockMvc.perform(auth(delete("/goals/" + id))).andExpect(status().isNoContent());
        assertThat(goalRepository.findByProfileIdOrderByCreatedAtAsc(profile.getId())).isEmpty();
    }

    @Test
    void otherUsersCannotSeeOrTouchAGoal() throws Exception {
        Long id = createGoal("{\"name\":\"Privado\",\"targetAmount\":100}");
        String otherEmail = "test-" + UUID.randomUUID() + "@example.com";
        profileRepository.save(ProfileEntity.builder().fullName("Otro").email(otherEmail)
                .password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        token = jwtUtil.generateToken(otherEmail);

        mockMvc.perform(auth(get("/goals"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(auth(post("/goals/" + id + "/contributions")).content("{\"amount\":5}")).andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/goals/" + id))).andExpect(status().isNotFound());
    }
}
