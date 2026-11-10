package com.cristian.moneymanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** CSV import (F12): the browser parses the file and sends clean rows. */
public final class ImportDtos {

    private ImportDtos() {
    }

    public record ImportRow(LocalDate date, String name, BigDecimal amount, String category, String note) {
    }

    public record ImportRequest(String type, List<ImportRow> rows, boolean createMissingCategories, boolean skipDuplicates) {
    }

    public record ImportResult(int created, int skippedDuplicates, List<String> createdCategories) {
    }
}
