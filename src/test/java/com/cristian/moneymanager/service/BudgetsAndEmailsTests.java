package com.cristian.moneymanager.service;

import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.BudgetRepository;
import com.cristian.moneymanager.repository.JobRunRepository;
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
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Budgets (F8) and scheduled emails (F10), end to end against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.jobs.secret=test-jobs-secret")
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class BudgetsAndEmailsTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private JobRunRepository jobRunRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ScheduledEmailService scheduledEmailService;

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

    private Long createCategory(String name, String type) throws Exception {
        String json = mockMvc.perform(auth(post("/categories")).content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void addExpense(Long categoryId, String amount) throws Exception {
        LocalDate today = LocalDate.now(MonthParam.APP_ZONE);
        mockMvc.perform(auth(post("/expenses")).content("{\"name\":\"Gasto\",\"amount\":" + amount + ",\"date\":\"" + today + "\",\"categoryId\":" + categoryId + "}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void budgetShowsWhatWasSpentThisMonth() throws Exception {
        Long food = createCategory("Comida", "expense");
        Long fun = createCategory("Ocio", "expense");
        Long salary = createCategory("Nómina", "income");
        addExpense(food, "85");

        mockMvc.perform(auth(put("/budgets/" + food)).content("{\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.spent").value(85.0))
                .andExpect(jsonPath("$.remaining").value(15.0))
                .andExpect(jsonPath("$.percent").value(85))
                .andExpect(jsonPath("$.status").value("warning"));

        addExpense(food, "20");
        mockMvc.perform(auth(get("/budgets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(food.intValue()))
                .andExpect(jsonPath("$[0].status").value("over"))
                .andExpect(jsonPath("$[0].percent").value(105))
                .andExpect(jsonPath("$[1].categoryId").value(fun.intValue()))
                .andExpect(jsonPath("$[1].status").value("none"));

        mockMvc.perform(auth(put("/budgets/" + salary)).content("{\"amount\":100}")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(put("/budgets/" + fun)).content("{\"amount\":0}")).andExpect(status().isBadRequest());

        // Deleting the category also deletes its budget
        mockMvc.perform(auth(delete("/categories/" + food)).param("reassignTo", String.valueOf(fun))).andExpect(status().is2xxSuccessful());
        assertThat(budgetRepository.findByProfileId(profile.getId())).isEmpty();
    }

    @Test
    void budgetCanBeRemoved() throws Exception {
        Long food = createCategory("Comida", "expense");
        mockMvc.perform(auth(put("/budgets/" + food)).content("{\"amount\":50}")).andExpect(status().isOk());
        mockMvc.perform(auth(delete("/budgets/" + food))).andExpect(status().isNoContent());
        mockMvc.perform(auth(delete("/budgets/" + food))).andExpect(status().isNotFound());
    }

    @Test
    void emailPreferencesAndUnsubscribe() throws Exception {
        mockMvc.perform(auth(get("/profile")))
                .andExpect(jsonPath("$.emailDailySummary").value(false));

        mockMvc.perform(auth(put("/profile/email-preferences")).content("{\"dailySummary\":true,\"monthlyReport\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailDailyReminder").value(false))
                .andExpect(jsonPath("$.emailDailySummary").value(true))
                .andExpect(jsonPath("$.emailMonthlyReport").value(true));

        // A scheduled email creates the unsubscribe token and links to it
        Long food = createCategory("Comida", "expense");
        addExpense(food, "12.5");
        ProfileEntity saved = profileRepository.findById(profile.getId()).orElseThrow();
        EmailContent summary = scheduledEmailService.dailySummary(saved, LocalDate.now(MonthParam.APP_ZONE));
        assertThat(summary).isNotNull();
        assertThat(summary.html()).contains("12,50").contains("/unsubscribe?token=");
        assertThat(summary.unsubscribeUrl()).contains("/unsubscribe?token=");

        String unsubscribeToken = profileRepository.findById(profile.getId()).orElseThrow().getEmailUnsubscribeToken();
        mockMvc.perform(post("/unsubscribe").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/unsubscribe").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + unsubscribeToken + "\"}"))
                .andExpect(status().isOk());

        ProfileEntity after = profileRepository.findById(profile.getId()).orElseThrow();
        assertThat(after.getEmailDailySummary()).isFalse();
        assertThat(after.getEmailMonthlyReport()).isFalse();
    }

    @Test
    void reminderOnlyWhenNothingWasAddedToday() throws Exception {
        LocalDate today = LocalDate.now(MonthParam.APP_ZONE);
        assertThat(scheduledEmailService.dailyReminder(profile, today)).isNotNull();
        addExpense(createCategory("Comida", "expense"), "3");
        assertThat(scheduledEmailService.dailyReminder(profile, today)).isNull();
    }

    @Test
    void monthlyReportSummarisesTheMonth() throws Exception {
        Long food = createCategory("Comida", "expense");
        addExpense(food, "40");
        mockMvc.perform(auth(put("/budgets/" + food)).content("{\"amount\":50}")).andExpect(status().isOk());

        EmailContent report = scheduledEmailService.monthlyReport(profile, YearMonth.now(MonthParam.APP_ZONE));
        assertThat(report.subject()).startsWith("Tu resumen de ");
        assertThat(report.html()).contains("Comida", "40,00", "80%");
        assertThat(scheduledEmailService.monthlyReport(profile, YearMonth.now(MonthParam.APP_ZONE).minusMonths(3))).isNull();
    }

    @Test
    void jobsNeedTheSecretAndRunOncePerDay() throws Exception {
        mockMvc.perform(post("/internal/jobs/daily-summary")).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/jobs/daily-summary").header("X-Jobs-Secret", "wrong")).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/jobs/unknown").header("X-Jobs-Secret", "test-jobs-secret")).andExpect(status().isNotFound());

        jobRunRepository.deleteAll();
        mockMvc.perform(post("/internal/jobs/daily-summary").header("X-Jobs-Secret", "test-jobs-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(false));
        mockMvc.perform(post("/internal/jobs/daily-summary").header("X-Jobs-Secret", "test-jobs-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));
    }
}
