package com.cristian.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FilterDto {

    /** income, expense or all ("Todos") */
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String keyword;
    private String sortField;
    private String sortOrder; //asc or desc
    /** Optional pagination (0-based). Without page the whole list is returned, as before. */
    private Integer page;
    private Integer size;
    /** Optional: only this category and its subcategories. */
    private Long categoryId;
    /** Optional amount range, both inclusive. */
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}
