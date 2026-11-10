package com.cristian.moneymanager.service;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV import (F12), notes and attachments (F15), end to end against PostgreSQL (runs in CI). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class ImportAndAttachmentFlowTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

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
                .fullName("Ana").email(email).password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        token = jwtUtil.generateToken(email);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long expenses() {
        return expenseRepository.findByProfileIdOrderByDateDesc(profile.getId()).size();
    }

    @Test
    void importsRowsCreatingCategoriesAndSkippingDuplicates() throws Exception {
        String rows = "[{\"date\":\"2026-10-01\",\"name\":\"Mercadona\",\"amount\":45.2,\"category\":\"Comida\"},"
                + "{\"date\":\"2026-10-02\",\"name\":\"Cine\",\"amount\":9,\"category\":\"Ocio\",\"note\":\"Con Marta\"},"
                + "{\"date\":\"2026-10-02\",\"name\":\"cine\",\"amount\":9.00,\"category\":\"ocio\"}]";

        // Missing categories without permission to create them: nothing is imported
        mockMvc.perform(auth(post("/import")).content("{\"type\":\"expense\",\"rows\":" + rows + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no existe")));
        assertThat(expenses()).isZero();

        mockMvc.perform(auth(post("/import")).content("{\"type\":\"expense\",\"createMissingCategories\":true,\"skipDuplicates\":true,\"rows\":" + rows + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skippedDuplicates").value(1))
                .andExpect(jsonPath("$.createdCategories.length()").value(2));
        assertThat(categoryRepository.findByTypeIgnoreCaseAndProfileId("expense", profile.getId())).hasSize(2);

        // Importing the same file again only skips
        mockMvc.perform(auth(post("/import")).content("{\"type\":\"expense\",\"createMissingCategories\":true,\"skipDuplicates\":true,\"rows\":" + rows + "}"))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skippedDuplicates").value(3));
        assertThat(expenses()).isEqualTo(2);
        assertThat(expenseRepository.findByProfileIdOrderByDateDesc(profile.getId()))
                .anySatisfy(e -> assertThat(e.getNote()).isEqualTo("Con Marta"));
    }

    @Test
    void reportsInvalidRowsAndImportsNothing() throws Exception {
        String rows = "[{\"date\":\"2026-10-01\",\"name\":\"Ok\",\"amount\":5,\"category\":\"Comida\"},"
                + "{\"date\":\"2026-10-01\",\"name\":\"\",\"amount\":5,\"category\":\"Comida\"},"
                + "{\"date\":\"" + LocalDate.now().plusDays(10) + "\",\"name\":\"Futuro\",\"amount\":5,\"category\":\"Comida\"},"
                + "{\"date\":\"2026-10-01\",\"name\":\"Cero\",\"amount\":0,\"category\":\"Comida\"}]";
        mockMvc.perform(auth(post("/import")).content("{\"type\":\"expense\",\"createMissingCategories\":true,\"rows\":" + rows + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Fila 2")));
        assertThat(expenses()).isZero();
        assertThat(categoryRepository.findByProfileId(profile.getId())).isEmpty();
    }

    @Test
    void notesAndAttachmentsOnAnExpense() throws Exception {
        String category = mockMvc.perform(auth(post("/categories")).content("{\"name\":\"Comida\",\"type\":\"expense\"}"))
                .andReturn().getResponse().getContentAsString();
        Long categoryId = Long.valueOf(category.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String expense = mockMvc.perform(auth(post("/expenses")).content("{\"name\":\"Cena\",\"amount\":30,\"date\":\"2026-10-03\",\"categoryId\":" + categoryId + ",\"note\":\" Cumple de Ana \"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.note").value("Cumple de Ana"))
                .andReturn().getResponse().getContentAsString();
        Long expenseId = Long.valueOf(expense.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        // Upload: real PNG accepted, HTML disguised as image refused
        String uploaded = mockMvc.perform(multipart("/attachments").file(new MockMultipartFile("file", "ticket.png", "image/png", PNG))
                        .param("type", "expense").param("transactionId", expenseId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        Long attachmentId = Long.valueOf(uploaded.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        mockMvc.perform(multipart("/attachments").file(new MockMultipartFile("file", "x.png", "image/png", "<html>hola</html> padding!".getBytes()))
                        .param("type", "expense").param("transactionId", expenseId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(auth(get("/expenses")).param("month", "2026-10"))
                .andExpect(jsonPath("$[0].attachmentCount").value(1));
        mockMvc.perform(auth(get("/attachments/" + attachmentId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));

        // Another user cannot see it
        String otherEmail = "test-" + UUID.randomUUID() + "@example.com";
        profileRepository.save(ProfileEntity.builder().fullName("Otro").email(otherEmail).password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        String ownerToken = token;
        token = jwtUtil.generateToken(otherEmail);
        mockMvc.perform(auth(get("/attachments/" + attachmentId))).andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/attachments")).param("type", "expense").param("transactionId", expenseId.toString())).andExpect(status().isNotFound());
        token = ownerToken;

        // Editing keeps or clears the note; deleting the expense removes its files
        mockMvc.perform(auth(put("/expenses/" + expenseId)).content("{\"name\":\"Cena\",\"amount\":30,\"date\":\"2026-10-03\",\"categoryId\":" + categoryId + ",\"note\":\"\"}"))
                .andExpect(jsonPath("$.note").doesNotExist());
        mockMvc.perform(auth(delete("/expenses/" + expenseId))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(auth(get("/attachments/" + attachmentId))).andExpect(status().isNotFound());
    }
}
