package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.GoalDtos.GoalDto;
import com.cristian.moneymanager.entity.GoalEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProgressTest {

    private static GoalEntity goal(String target, LocalDate deadline) {
        return GoalEntity.builder().id(1L).name("Viaje").targetAmount(new BigDecimal(target)).deadline(deadline).build();
    }

    @Test
    void monthlyNeededCountsTheCurrentMonth() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        GoalDto dto = GoalService.progress(goal("1000.00", LocalDate.of(2027, 2, 1)), new BigDecimal("400.00"), List.of(), today);

        assertThat(dto.percent()).isEqualTo(40);
        assertThat(dto.remainingAmount()).isEqualByComparingTo("600");
        assertThat(dto.monthsLeft()).isEqualTo(4); // nov, dic, ene, feb
        assertThat(dto.monthlyNeeded()).isEqualByComparingTo("150.00");
        assertThat(dto.achieved()).isFalse();
    }

    @Test
    void roundsMonthlyAmountUp() {
        GoalDto dto = GoalService.progress(goal("100.00", LocalDate.of(2026, 12, 31)), BigDecimal.ZERO, List.of(), LocalDate.of(2026, 11, 10));
        assertThat(dto.monthlyNeeded()).isEqualByComparingTo("50.00");
        dto = GoalService.progress(goal("100.00", LocalDate.of(2027, 1, 1)), BigDecimal.ZERO, List.of(), LocalDate.of(2026, 11, 10));
        assertThat(dto.monthlyNeeded()).isEqualByComparingTo("33.34");
    }

    @Test
    void achievedAndOverdueGoals() {
        GoalDto achieved = GoalService.progress(goal("100.00", null), new BigDecimal("120.00"), List.of(), LocalDate.of(2026, 11, 10));
        assertThat(achieved.achieved()).isTrue();
        assertThat(achieved.percent()).isEqualTo(120);
        assertThat(achieved.remainingAmount()).isEqualByComparingTo("0");
        assertThat(achieved.monthlyNeeded()).isNull();

        GoalDto overdue = GoalService.progress(goal("100.00", LocalDate.of(2026, 10, 1)), BigDecimal.TEN, List.of(), LocalDate.of(2026, 11, 10));
        assertThat(overdue.monthsLeft()).isZero();
        assertThat(overdue.monthlyNeeded()).isNull();
    }
}
