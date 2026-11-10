package com.cristian.moneymanager;

import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paginated search (T5), against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class FilterPaginationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void createUserWithExpenses() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        ProfileEntity profile = profileRepository.save(ProfileEntity.builder()
                .fullName("Ana").email(email).password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        token = jwtUtil.generateToken(email);
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name("Comida").type("expense").profile(profile).build());

        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 23; i++) {
            expenseRepository.save(ExpenseEntity.builder().name("Gasto " + i).amount(BigDecimal.TEN)
                    .date(start.plusDays(i)).category(category).profile(profile).build());
        }
        expenseRepository.save(ExpenseEntity.builder().name("50% descuento").amount(new BigDecimal("4.50"))
                .date(start).category(category).profile(profile).build());
        expenseRepository.save(ExpenseEntity.builder().name("500 gramos").amount(new BigDecimal("3"))
                .date(start).category(category).profile(profile).build());
    }

    private MockHttpServletRequestBuilder search(String body) {
        return post("/filter").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void returnsPagesWithTotals() throws Exception {
        mockMvc.perform(search("{\"type\":\"expense\",\"page\":0,\"size\":10,\"sortField\":\"date\",\"sortOrder\":\"desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].name").value("Gasto 22"))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalAmount").value(237.5));

        mockMvc.perform(search("{\"type\":\"expense\",\"page\":2,\"size\":10}"))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.page").value(2));

        // Size is capped
        mockMvc.perform(search("{\"type\":\"expense\",\"page\":0,\"size\":5000}"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void percentInTheKeywordIsLiteral() throws Exception {
        mockMvc.perform(search("{\"type\":\"expense\",\"keyword\":\"50%\",\"page\":0}"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalAmount").value(4.5));
    }

    @Test
    void withoutPageReturnsTheWholeList() throws Exception {
        mockMvc.perform(search("{\"type\":\"expense\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25));
    }
}
