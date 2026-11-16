package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of a search over incomes and expenses together ("Todos"). */
public record SearchItemDto(
        String type,
        Long id,
        String name,
        String icon,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        LocalDate date,
        String note,
        Long recurringId,
        Integer attachmentCount
) {
    public static SearchItemDto of(IncomeDto i) {
        return new SearchItemDto("income", i.getId(), i.getName(), i.getIcon(), i.getCategoryId(), i.getCategoryName(),
                i.getAmount(), i.getDate(), i.getNote(), i.getRecurringId(), i.getAttachmentCount());
    }

    public static SearchItemDto of(ExpenseDto e) {
        return new SearchItemDto("expense", e.getId(), e.getName(), e.getIcon(), e.getCategoryId(), e.getCategoryName(),
                e.getAmount(), e.getDate(), e.getNote(), e.getRecurringId(), e.getAttachmentCount());
    }
}
