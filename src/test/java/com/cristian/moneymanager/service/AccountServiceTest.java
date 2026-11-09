package com.cristian.moneymanager.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTest {

    @Test
    void imageUrlMustBeHttps() {
        assertThat(AccountService.validateImageUrl(null)).isNull();
        assertThat(AccountService.validateImageUrl("  ")).isNull();
        assertThat(AccountService.validateImageUrl(" https://res.cloudinary.com/a.png ")).isEqualTo("https://res.cloudinary.com/a.png");

        assertThatThrownBy(() -> AccountService.validateImageUrl("http://example.com/a.png")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AccountService.validateImageUrl("javascript:alert(1)")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AccountService.validateImageUrl("https://x.com/" + "a".repeat(300))).isInstanceOf(ResponseStatusException.class);
    }
}
