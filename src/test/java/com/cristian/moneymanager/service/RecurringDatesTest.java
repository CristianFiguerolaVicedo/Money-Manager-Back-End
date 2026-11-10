package com.cristian.moneymanager.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringDatesTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void monthlyKeepsTheDayOfMonthWhenItExists() {
        LocalDate start = d("2026-01-31");
        assertThat(RecurringService.occurrence(start, "monthly", 1)).isEqualTo(d("2026-02-28"));
        assertThat(RecurringService.occurrence(start, "monthly", 2)).isEqualTo(d("2026-03-31"));
        assertThat(RecurringService.nextAfter(start, "monthly", d("2026-02-28"))).isEqualTo(d("2026-03-31"));
        assertThat(RecurringService.nextAfter(start, "monthly", d("2026-01-31"))).isEqualTo(d("2026-02-28"));
    }

    @Test
    void nextAfterBeforeTheStartIsTheStart() {
        assertThat(RecurringService.nextAfter(d("2026-05-10"), "weekly", d("2026-01-01"))).isEqualTo(d("2026-05-10"));
        assertThat(RecurringService.nextOnOrAfter(d("2026-05-10"), "monthly", d("2026-05-10"))).isEqualTo(d("2026-05-10"));
    }

    @Test
    void weeklyAndYearly() {
        assertThat(RecurringService.nextAfter(d("2026-11-02"), "weekly", d("2026-11-10"))).isEqualTo(d("2026-11-16"));
        assertThat(RecurringService.nextOnOrAfter(d("2026-11-02"), "weekly", d("2026-11-16"))).isEqualTo(d("2026-11-16"));
        assertThat(RecurringService.nextAfter(d("2024-02-29"), "yearly", d("2024-02-29"))).isEqualTo(d("2025-02-28"));
        assertThat(RecurringService.occurrence(d("2024-02-29"), "yearly", 4)).isEqualTo(d("2028-02-29"));
    }
}
