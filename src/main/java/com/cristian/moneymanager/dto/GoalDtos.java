package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Savings goals (F14). */
public final class GoalDtos {

    private GoalDtos() {
    }

    /**
     * A goal with its progress. {@code monthlyNeeded} is how much to save each month (from this month on)
     * to reach the target by the deadline; null without deadline or once reached.
     */
    public record GoalDto(Long id, String name, String icon, BigDecimal targetAmount, BigDecimal savedAmount,
                          BigDecimal remainingAmount, Integer percent, LocalDate deadline, Integer monthsLeft,
                          BigDecimal monthlyNeeded, boolean achieved, List<ContributionDto> contributions) {
    }

    public record ContributionDto(Long id, BigDecimal amount, LocalDate date, String note) {
    }

    public record GoalRequest(String name, String icon, BigDecimal targetAmount, LocalDate deadline) {
    }

    public record ContributionRequest(BigDecimal amount, LocalDate date, String note) {
    }
}
