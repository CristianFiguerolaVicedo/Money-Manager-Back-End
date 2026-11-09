package com.cristian.moneymanager.util;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthParamTest {

    @Test
    void parsesYearMonth() {
        assertThat(MonthParam.parseOrCurrent("2026-10")).isEqualTo(YearMonth.of(2026, 10));
        assertThat(MonthParam.parseOrCurrent(" 2025-01 ")).isEqualTo(YearMonth.of(2025, 1));
    }

    @Test
    void defaultsToCurrentMonthInMadrid() {
        assertThat(MonthParam.parseOrCurrent(null)).isEqualTo(YearMonth.now(MonthParam.APP_ZONE));
        assertThat(MonthParam.parseOrCurrent("")).isEqualTo(YearMonth.now(MonthParam.APP_ZONE));
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> MonthParam.parseOrCurrent("2026-13")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> MonthParam.parseOrCurrent("octubre")).isInstanceOf(ResponseStatusException.class);
    }
}
