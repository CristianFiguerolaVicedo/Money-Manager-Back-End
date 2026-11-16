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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit: search with "Todos", category (with its subcategories) and amount range; one level of subcategories. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRESQL_URL", matches = "jdbc:postgresql:.*")
class FilterAndSubcategoryTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private String token;
    private final String day = LocalDate.now().minusDays(1).toString();

    @BeforeEach
    void createUser() {
        String email = "filtros-" + UUID.randomUUID() + "@example.com";
        profileRepository.save(ProfileEntity.builder().fullName("Ana").email(email)
                .password(passwordEncoder.encode("abcdefg1")).isActive(true).build());
        token = jwtUtil.generateToken(email);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long create(String path, String json) throws Exception {
        String body = mockMvc.perform(auth(post(path)).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long category(String name, String type, Long parentId) throws Exception {
        return create("/categories", "{\"name\":\"" + name + "\",\"type\":\"" + type + "\""
                + (parentId != null ? ",\"parentId\":" + parentId : "") + "}");
    }

    private void movement(String path, String name, String amount, long categoryId) throws Exception {
        create(path, "{\"name\":\"" + name + "\",\"amount\":" + amount + ",\"date\":\"" + day + "\",\"categoryId\":" + categoryId + "}");
    }

    private ResultActions search(String json) throws Exception {
        return mockMvc.perform(auth(post("/filter")).content(json));
    }

    @Test
    void filtersByTypeAllCategoryWithSubcategoriesAndAmount() throws Exception {
        long food = category("Comida", "expense", null);
        long supermarket = category("Supermercado", "expense", food);
        long transport = category("Transporte", "expense", null);
        long salary = category("Nómina", "income", null);
        movement("/expenses", "Restaurante", "40", food);
        movement("/expenses", "Mercadona", "60", supermarket);
        movement("/expenses", "Metro", "10", transport);
        movement("/incomes", "Sueldo", "1500", salary);

        // "Todos": incomes and expenses together, with the balance as total
        search("{\"type\":\"all\",\"page\":0,\"sortField\":\"amount\",\"sortOrder\":\"desc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].name").value("Sueldo"))
                .andExpect(jsonPath("$.items[0].type").value("income"))
                .andExpect(jsonPath("$.items[1].type").value("expense"))
                .andExpect(jsonPath("$.totalAmount").value(1390.0));
        search("{\"type\":\"all\",\"page\":1,\"size\":3}")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        // A category includes its subcategories
        search("{\"type\":\"expense\",\"page\":0,\"categoryId\":" + food + "}")
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Restaurante", "Mercadona")))
                .andExpect(jsonPath("$.totalAmount").value(100.0));
        search("{\"type\":\"expense\",\"page\":0,\"categoryId\":" + supermarket + "}")
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Mercadona")));

        // Amount range, inclusive
        search("{\"type\":\"all\",\"page\":0,\"minAmount\":10,\"maxAmount\":40}")
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Restaurante", "Metro")));
        search("{\"type\":\"expense\",\"page\":0,\"minAmount\":50,\"maxAmount\":10}")
                .andExpect(status().isBadRequest());
        search("{\"type\":\"nada\",\"page\":0}").andExpect(status().isBadRequest());
        search("{\"type\":\"expense\",\"page\":0,\"categoryId\":999999999}").andExpect(status().isNotFound());
    }

    @Test
    void subcategoriesHaveOneLevelAndTheSameType() throws Exception {
        long food = category("Comida", "expense", null);
        long supermarket = category("Supermercado", "expense", food);
        long salary = category("Nómina", "income", null);

        mockMvc.perform(auth(get("/categories")))
                .andExpect(jsonPath("$[?(@.name == 'Supermercado')].parentId").value(org.hamcrest.Matchers.contains((int) food)));

        // Wrong type, two levels, itself, or a parent becoming a child
        mockMvc.perform(auth(post("/categories")).content("{\"name\":\"Extra\",\"type\":\"income\",\"parentId\":" + food + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(post("/categories")).content("{\"name\":\"Ofertas\",\"type\":\"expense\",\"parentId\":" + supermarket + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(put("/categories/" + food)).content("{\"name\":\"Comida\",\"type\":\"expense\",\"parentId\":" + food + "}"))
                .andExpect(status().isBadRequest());
        long other = category("Ocio", "expense", null);
        mockMvc.perform(auth(put("/categories/" + food)).content("{\"name\":\"Comida\",\"type\":\"expense\",\"parentId\":" + other + "}"))
                .andExpect(status().isBadRequest());

        // Moving a subcategory to the top level
        mockMvc.perform(auth(put("/categories/" + supermarket)).content("{\"name\":\"Supermercado\",\"type\":\"expense\",\"parentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").doesNotExist());
        mockMvc.perform(auth(put("/categories/" + supermarket)).content("{\"name\":\"Supermercado\",\"type\":\"expense\",\"parentId\":" + food + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").value(food));

        // Deleting the parent keeps the subcategory as a main category
        mockMvc.perform(auth(delete("/categories/" + food))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(auth(get("/categories")))
                .andExpect(jsonPath("$[?(@.name == 'Supermercado')].parentId").value(org.hamcrest.Matchers.contains((Object) null)));
        mockMvc.perform(auth(get("/categories")))
                .andExpect(jsonPath("$[?(@.id == " + salary + ")].name").value(org.hamcrest.Matchers.contains("Nómina")));
    }
}
