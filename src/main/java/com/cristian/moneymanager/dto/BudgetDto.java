package com.cristian.moneymanager.dto;

import java.math.BigDecimal;

/**
 * A category's monthly budget and how much of it is used in the requested month.
 * {@code amount} is null for expense categories without a budget.
 */
public record BudgetDto(
        Long categoryId,
        String categoryName,
        String icon,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        Integer percent,
        String status
) {
    public record SetBudgetRequest(BigDecimal amount) {
    }
}
