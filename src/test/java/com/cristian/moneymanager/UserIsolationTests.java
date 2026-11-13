package com.cristian.moneymanager;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.util.JwtUtil;
import com.jayway.jsonpath.JsonPath;
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
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit: "authorization between users (IDOR)". Ana creates one of everything; Bea, logged in with her own
 * account, tries to read, change and delete each of Ana's records by id. Every attempt must fail and Ana's
 * data must be untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class UserIsolationTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private String ana;
    private String bea;
    private final String today = LocalDate.now().minusDays(1).toString();

    @BeforeEach
    void createTwoUsers() {
        ana = tokenFor("Ana");
        bea = tokenFor("Bea");
    }

    private String tokenFor(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        profileRepository.save(ProfileEntity.builder().fullName(name).email(email)
                .password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        return jwtUtil.generateToken(email);
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(as(token, request).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    /** Bea's request must be refused (400, 403 or 404 — never 2xx or 5xx). */
    private void refused(MockHttpServletRequestBuilder request, String json) throws Exception {
        int code = mockMvc.perform(as(bea, request).content(json)).andReturn().getResponse().getStatus();
        assertThat(code).as(request.toString()).isBetween(400, 404);
    }

    @Test
    void oneUserCannotReadChangeOrDeleteAnotherUsersData() throws Exception {
        // --- Ana's data
        long salary = id(body(ana, post("/categories"), "{\"name\":\"Nómina\",\"type\":\"income\"}"), "$.id");
        long food = id(body(ana, post("/categories"), "{\"name\":\"Comida\",\"type\":\"expense\"}"), "$.id");
        long income = id(body(ana, post("/incomes"),
                "{\"name\":\"Sueldo\",\"amount\":1500,\"date\":\"" + today + "\",\"categoryId\":" + salary + "}"), "$.id");
        long expense = id(body(ana, post("/expenses"),
                "{\"name\":\"Súper\",\"amount\":42.5,\"date\":\"" + today + "\",\"categoryId\":" + food + "}"), "$.id");
        body(ana, put("/budgets/" + food), "{\"amount\":200}");
        long goal = id(body(ana, post("/goals"), "{\"name\":\"Viaje\",\"targetAmount\":1000}"), "$.id");
        long contribution = id(body(ana, post("/goals/" + goal + "/contributions"), "{\"amount\":100}"), "$.contributions[0].id");
        long rule = id(body(ana, post("/recurring"), "{\"type\":\"expense\",\"name\":\"Gimnasio\",\"amount\":30,\"categoryId\":" + food
                + ",\"frequency\":\"monthly\",\"startDate\":\"" + LocalDate.now().plusMonths(1) + "\"}"), "$.id");
        long attachment = id(mockMvc.perform(multipart("/attachments")
                        .file(new MockMultipartFile("file", "ticket.png", "image/png", PNG))
                        .param("type", "expense").param("transactionId", String.valueOf(expense))
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // --- Bea's lists never include Ana's records
        mockMvc.perform(as(bea, get("/categories"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/incomes"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/expenses"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/goals"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/recurring")).param("type", "expense")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/budgets"))).andExpect(jsonPath("$[?(@.categoryId == " + food + ")]").isEmpty());
        for (String type : new String[]{"income", "expense"}) {
            mockMvc.perform(as(bea, post("/filter")).content("{\"type\":\"" + type + "\",\"page\":0}"))
                    .andExpect(jsonPath("$.totalItems").value(0));
        }

        // --- Bea cannot read, change or delete them by id
        refused(put("/categories/" + food), "{\"name\":\"Mía\",\"type\":\"expense\"}");
        refused(delete("/categories/" + food), "");
        refused(put("/incomes/" + income), "{\"name\":\"X\",\"amount\":1,\"date\":\"" + today + "\",\"categoryId\":" + salary + "}");
        refused(delete("/incomes/" + income), "");
        refused(put("/expenses/" + expense), "{\"name\":\"X\",\"amount\":1,\"date\":\"" + today + "\",\"categoryId\":" + food + "}");
        refused(delete("/expenses/" + expense), "");
        refused(put("/budgets/" + food), "{\"amount\":1}");
        refused(delete("/budgets/" + food), "");
        refused(put("/goals/" + goal), "{\"name\":\"X\",\"targetAmount\":1}");
        refused(post("/goals/" + goal + "/contributions"), "{\"amount\":5}");
        refused(delete("/goals/" + goal + "/contributions/" + contribution), "");
        refused(delete("/goals/" + goal), "");
        refused(put("/recurring/" + rule), "{\"type\":\"expense\",\"name\":\"X\",\"amount\":1,\"categoryId\":" + food
                + ",\"frequency\":\"monthly\",\"startDate\":\"" + today + "\"}");
        refused(delete("/recurring/" + rule), "");
        refused(get("/attachments").param("type", "expense").param("transactionId", String.valueOf(expense)), "");
        refused(get("/attachments/" + attachment), "");
        refused(delete("/attachments/" + attachment), "");

        // --- …nor attach her own records to Ana's categories or movements
        refused(post("/incomes"), "{\"name\":\"X\",\"amount\":1,\"date\":\"" + today + "\",\"categoryId\":" + salary + "}");
        refused(post("/expenses"), "{\"name\":\"X\",\"amount\":1,\"date\":\"" + today + "\",\"categoryId\":" + food + "}");
        refused(post("/recurring"), "{\"type\":\"expense\",\"name\":\"X\",\"amount\":1,\"categoryId\":" + food
                + ",\"frequency\":\"monthly\",\"startDate\":\"" + today + "\"}");
        int upload = mockMvc.perform(multipart("/attachments")
                        .file(new MockMultipartFile("file", "x.png", "image/png", PNG))
                        .param("type", "expense").param("transactionId", String.valueOf(expense))
                        .header("Authorization", "Bearer " + bea))
                .andReturn().getResponse().getStatus();
        assertThat(upload).isBetween(400, 404);

        // --- Ana's data is exactly as she left it
        mockMvc.perform(as(ana, get("/incomes"))).andExpect(jsonPath("$[0].name").value("Sueldo"));
        mockMvc.perform(as(ana, get("/expenses"))).andExpect(jsonPath("$[0].name").value("Súper"))
                .andExpect(jsonPath("$[0].attachmentCount").value(1));
        mockMvc.perform(as(ana, get("/categories"))).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(as(ana, get("/goals"))).andExpect(jsonPath("$[0].savedAmount").value(100.0));
        mockMvc.perform(as(ana, get("/recurring")).param("type", "expense")).andExpect(jsonPath("$[0].name").value("Gimnasio"));
        mockMvc.perform(as(ana, get("/budgets"))).andExpect(jsonPath("$[?(@.categoryId == " + food + ")].amount").value(contains(200.0)));
        mockMvc.perform(as(ana, get("/attachments/" + attachment))).andExpect(status().isOk());
    }

    @Test
    void amountsAreRoundedAndLimitedOnTheServer() throws Exception {
        long food = id(body(ana, post("/categories"), "{\"name\":\"Comida\",\"type\":\"expense\"}"), "$.id");
        String base = ",\"date\":\"" + today + "\",\"categoryId\":" + food + "}";

        mockMvc.perform(as(ana, post("/expenses")).content("{\"name\":\"Pan\",\"amount\":1500.456" + base))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.amount").value(1500.46));
        mockMvc.perform(as(ana, post("/expenses")).content("{\"name\":\"Yate\",\"amount\":1000000000" + base))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(ana, post("/expenses")).content("{\"name\":\"Nada\",\"amount\":0.001" + base))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(ana, post("/expenses")).content("{\"name\":\"" + "x".repeat(256) + "\",\"amount\":1" + base))
                .andExpect(status().isBadRequest());
    }
}
