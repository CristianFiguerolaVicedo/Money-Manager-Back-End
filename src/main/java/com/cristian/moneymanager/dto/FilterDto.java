package com.cristian.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
public class FilterDto {

    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String keyword;
    private String sortField;
    private String sortOrder; //asc or desc
    /** Optional pagination (0-based). Without page the whole list is returned, as before. */
    private Integer page;
    private Integer size;
}
