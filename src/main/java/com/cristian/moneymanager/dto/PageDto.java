package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.util.List;

/** One page of search results plus totals of the whole search. */
public record PageDto<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        BigDecimal totalAmount
) {
}
