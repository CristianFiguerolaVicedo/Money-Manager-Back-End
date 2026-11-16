package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a search can filter by, already validated. {@code keyword} has its LIKE wildcards escaped,
 * {@code categoryIds} includes the subcategories of the chosen category and is empty when not filtering.
 */
public record SearchCriteria(
        Long profileId,
        LocalDate startDate,
        LocalDate endDate,
        String keyword,
        List<Long> categoryIds,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
    public boolean anyCategory() {
        return categoryIds.isEmpty();
    }

    /** IN () is not valid SQL, so an unused list gets a dummy id. */
    public List<Long> categoryIdsForQuery() {
        return categoryIds.isEmpty() ? List.of(-1L) : categoryIds;
    }
}
