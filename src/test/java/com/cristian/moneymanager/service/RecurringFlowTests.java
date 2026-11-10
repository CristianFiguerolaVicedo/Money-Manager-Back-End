package com.cristian.moneymanager.service;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.util.JwtUtil;
import com.cristian.moneymanager.util.MonthParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
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

/** Recurring incomes/expenses (F9), end to end against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.jobs.secret=test-jobs-secret")
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class RecurringFlowTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private IncomeRepository incomeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private ProfileEntity profile;
    private String token;
    private final LocalDate today = LocalDate.now(MonthParam.APP_ZONE);

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

    private Long createCategory(String name, String type) throws Exception {
        String json = mockMvc.perform(auth(post("/categories")).content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private Long createRule(String body) throws Exception {
        String json = mockMvc.perform(auth(post("/recurring")).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long expenses() {
        return expenseRepository.findByProfileIdOrderByDateDesc(profile.getId()).size();
    }

    @Test
    void createsPastOccurrencesRightAwayAndNeverTwice() throws Exception {
        Long rent = createCategory("Alquiler", "expense");
        LocalDate start = today.minusMonths(2);
        createRule("{\"type\":\"expense\",\"name\":\"Alquiler\",\"amount\":700,\"categoryId\":" + rent
                + ",\"frequency\":\"monthly\",\"startDate\":\"" + start + "\"}");

        assertThat(expenses()).isEqualTo(3);
        assertThat(expenseRepository.findByProfileIdOrderByDateDesc(profile.getId()))
                .allSatisfy(e -> assertThat(e.getRecurringId()).isNotNull());

        // Opening the app again creates nothing new
        mockMvc.perform(auth(get("/dashboard"))).andExpect(status().isOk());
        mockMvc.perform(auth(get("/expenses"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recurringId").isNumber());
        assertThat(expenses()).isEqualTo(3);

        mockMvc.perform(auth(get("/recurring")).param("type", "expense"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nextDate").value(start.plusMonths(3).toString()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void futureRuleCreatesNothingYet() throws Exception {
        Long salary = createCategory("Nómina", "income");
        createRule("{\"type\":\"income\",\"name\":\"Nómina\",\"amount\":1800,\"categoryId\":" + salary
                + ",\"frequency\":\"monthly\",\"startDate\":\"" + today.plusDays(3) + "\"}");
        assertThat(incomeRepository.findByProfileIdOrderByDateDesc(profile.getId())).isEmpty();
    }

    @Test
    void endDateStopsTheRule() throws Exception {
        Long gym = createCategory("Gimnasio", "expense");
        createRule("{\"type\":\"expense\",\"name\":\"Gimnasio\",\"amount\":30,\"categoryId\":" + gym
                + ",\"frequency\":\"weekly\",\"startDate\":\"" + today.minusWeeks(4) + "\",\"endDate\":\"" + today.minusWeeks(2) + "\"}");
        assertThat(expenses()).isEqualTo(3);
        mockMvc.perform(auth(get("/recurring"))).andExpect(jsonPath("$[0].nextDate").doesNotExist());
    }

    @Test
    void pausingAndResumingDoesNotBackfill() throws Exception {
        Long gym = createCategory("Gimnasio", "expense");
        Long id = createRule("{\"type\":\"expense\",\"name\":\"Gimnasio\",\"amount\":30,\"categoryId\":" + gym
                + ",\"frequency\":\"weekly\",\"startDate\":\"" + today.plusDays(1) + "\"}");

        mockMvc.perform(auth(put("/recurring/" + id)).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(auth(put("/recurring/" + id)).content("{\"active\":true,\"amount\":35}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.amount").value(35.0));
        assertThat(expenses()).isZero();
    }

    @Test
    void validatesAndDeletingKeepsMovements() throws Exception {
        Long food = createCategory("Comida", "expense");
        Long salary = createCategory("Nómina", "income");
        String base = ",\"amount\":10,\"frequency\":\"monthly\",\"startDate\":\"" + today + "\"}";

        mockMvc.perform(auth(post("/recurring")).content("{\"type\":\"expense\",\"name\":\"X\",\"categoryId\":" + salary + base))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(post("/recurring")).content("{\"type\":\"expense\",\"name\":\"X\",\"categoryId\":" + food
                        + ",\"amount\":10,\"frequency\":\"daily\",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(post("/recurring")).content("{\"type\":\"expense\",\"name\":\"X\",\"categoryId\":" + food
                        + ",\"amount\":10,\"frequency\":\"monthly\",\"startDate\":\"" + today.minusYears(2) + "\"}"))
                .andExpect(status().isBadRequest());

        Long id = createRule("{\"type\":\"expense\",\"name\":\"Netflix\",\"categoryId\":" + food + base);
        assertThat(expenses()).isEqualTo(1);

        // The category is in use by the rule too
        mockMvc.perform(auth(delete("/categories/" + food))).andExpect(status().isConflict());

        mockMvc.perform(auth(delete("/recurring/" + id))).andExpect(status().isNoContent());
        assertThat(expenses()).isEqualTo(1);
        assertThat(expenseRepository.findByProfileIdOrderByDateDesc(profile.getId()).get(0).getRecurringId()).isNull();
    }

    @Test
    void recurringJobNeedsTheSecret() throws Exception {
        mockMvc.perform(post("/internal/jobs/recurring")).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/jobs/recurring").header("X-Jobs-Secret", "test-jobs-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("recurring"));
    }
}
