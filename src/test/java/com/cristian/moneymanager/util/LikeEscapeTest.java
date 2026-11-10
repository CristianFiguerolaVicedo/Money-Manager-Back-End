package com.cristian.moneymanager.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeEscapeTest {

    @Test
    void escapesWildcards() {
        assertThat(LikeEscape.escape("50%")).isEqualTo("50\\%");
        assertThat(LikeEscape.escape("a_b")).isEqualTo("a\\_b");
        assertThat(LikeEscape.escape("c:\\x")).isEqualTo("c:\\\\x");
        assertThat(LikeEscape.escape(null)).isEmpty();
        assertThat(LikeEscape.escape("cena")).isEqualTo("cena");
    }
}
