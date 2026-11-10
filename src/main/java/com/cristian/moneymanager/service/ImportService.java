package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ImportDtos.ImportRequest;
import com.cristian.moneymanager.dto.ImportDtos.ImportResult;
import com.cristian.moneymanager.dto.ImportDtos.ImportRow;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports incomes or expenses from rows the browser read from a CSV (F12).
 * All rows are checked first: either everything valid is imported, or nothing.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    static final int MAX_ROWS = 2000;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    private final ProfileService profileService;
    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional
    public ImportResult importRows(ImportRequest request) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw badRequest("No hay filas para importar");
        }
        String type = request.type() == null ? "" : request.type().trim().toLowerCase(Locale.ROOT);
        if (!type.equals("income") && !type.equals("expense")) {
            throw badRequest("Tipo no válido, debe ser 'income' o 'expense'");
        }
        if (request.rows().size() > MAX_ROWS) {
            throw badRequest("Como mucho se pueden importar " + MAX_ROWS + " filas de una vez");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        LocalDate latest = LocalDate.now(MonthParam.APP_ZONE).plusDays(1);

        // 1. Validate every row
        List<String> errors = new ArrayList<>();
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 0; i < request.rows().size(); i++) {
            ImportRow row = request.rows().get(i);
            String error = rowError(row, latest);
            if (error != null) {
                errors.add("Fila " + (i + 1) + ": " + error);
            } else {
                rows.add(new ImportRow(row.date(), row.name().trim(), row.amount().setScale(2, RoundingMode.HALF_UP),
                        row.category().trim(), row.note() == null || row.note().isBlank() ? null : row.note().trim()));
            }
        }

        // 2. Categories: reuse by name, or create the missing ones if allowed
        Map<String, CategoryEntity> categories = new HashMap<>();
        Map<String, String> otherTypeNames = new HashMap<>();
        for (CategoryEntity category : categoryRepository.findByProfileId(profile.getId())) {
            String key = key(category.getName());
            if (type.equalsIgnoreCase(category.getType())) {
                categories.put(key, category);
            } else {
                otherTypeNames.put(key, category.getName());
            }
        }
        List<String> missing = rows.stream().map(ImportRow::category).filter(c -> !categories.containsKey(key(c)))
                .map(String::trim).distinct().toList();
        Set<String> missingKeys = new HashSet<>();
        for (String name : missing) {
            if (!missingKeys.add(key(name))) {
                continue;
            }
            if (otherTypeNames.containsKey(key(name))) {
                errors.add("La categoría «" + name + "» ya existe como categoría de " + ("income".equals(type) ? "gastos" : "ingresos"));
            } else if (!request.createMissingCategories()) {
                errors.add("La categoría «" + name + "» no existe");
            } else if (name.length() > 100) {
                errors.add("El nombre de categoría «" + name.substring(0, 20) + "…» es demasiado largo");
            }
        }
        if (!errors.isEmpty()) {
            String shown = String.join(" · ", errors.subList(0, Math.min(3, errors.size())));
            throw badRequest(errors.size() > 3 ? shown + " · y " + (errors.size() - 3) + " errores más" : shown);
        }

        List<String> created = new ArrayList<>();
        for (String name : missing) {
            if (!categories.containsKey(key(name))) {
                categories.put(key(name), categoryRepository.save(CategoryEntity.builder().name(name).type(type).profile(profile).build()));
                created.add(name);
            }
        }

        // 3. Skip rows already present (same date, name and amount) if asked
        Set<String> existing = new HashSet<>();
        if (request.skipDuplicates()) {
            LocalDate from = rows.stream().map(ImportRow::date).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate to = rows.stream().map(ImportRow::date).max(Comparator.naturalOrder()).orElseThrow();
            if ("income".equals(type)) {
                incomeRepository.findByProfileIdAndDateBetween(profile.getId(), from, to)
                        .forEach(e -> existing.add(duplicateKey(e.getDate(), e.getName(), e.getAmount())));
            } else {
                expenseRepository.findByProfileIdAndDateBetween(profile.getId(), from, to)
                        .forEach(e -> existing.add(duplicateKey(e.getDate(), e.getName(), e.getAmount())));
            }
        }

        // 4. Save
        int skipped = 0;
        List<IncomeEntity> incomes = new ArrayList<>();
        List<ExpenseEntity> expenses = new ArrayList<>();
        for (ImportRow row : rows) {
            String dup = duplicateKey(row.date(), row.name(), row.amount());
            if (request.skipDuplicates() && !existing.add(dup)) {
                skipped++;
                continue;
            }
            CategoryEntity category = categories.get(key(row.category()));
            if ("income".equals(type)) {
                incomes.add(IncomeEntity.builder().name(row.name()).amount(row.amount()).date(row.date())
                        .note(row.note()).category(category).profile(profile).build());
            } else {
                expenses.add(ExpenseEntity.builder().name(row.name()).amount(row.amount()).date(row.date())
                        .note(row.note()).category(category).profile(profile).build());
            }
        }
        incomeRepository.saveAll(incomes);
        expenseRepository.saveAll(expenses);
        return new ImportResult(incomes.size() + expenses.size(), skipped, created);
    }

    static String rowError(ImportRow row, LocalDate latest) {
        if (row == null) {
            return "fila vacía";
        }
        if (row.date() == null) {
            return "falta la fecha";
        }
        if (row.date().isAfter(latest)) {
            return "la fecha no puede ser futura";
        }
        if (row.name() == null || row.name().isBlank()) {
            return "falta el concepto";
        }
        if (row.name().trim().length() > 255) {
            return "el concepto es demasiado largo";
        }
        if (row.amount() == null || row.amount().signum() <= 0) {
            return "el importe debe ser mayor que 0";
        }
        if (row.amount().compareTo(MAX_AMOUNT) > 0) {
            return "el importe es demasiado grande";
        }
        if (row.category() == null || row.category().isBlank()) {
            return "falta la categoría";
        }
        if (row.note() != null && row.note().trim().length() > 500) {
            return "la nota pasa de 500 caracteres";
        }
        return null;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static String duplicateKey(LocalDate date, String name, BigDecimal amount) {
        return date + "|" + key(name) + "|" + (amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
