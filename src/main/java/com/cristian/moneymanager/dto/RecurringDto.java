package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A recurring income/expense rule as the front-end sees it (requests use the same shape; read-only fields are ignored). */
public record RecurringDto(
        Long id,
        String type,
        String name,
        String icon,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        String frequency,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextDate,
        Boolean active
) {
}
