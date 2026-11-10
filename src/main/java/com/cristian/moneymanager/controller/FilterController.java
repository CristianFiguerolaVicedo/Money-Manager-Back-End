package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.dto.FilterDto;
import com.cristian.moneymanager.dto.IncomeDto;
import com.cristian.moneymanager.service.ExpenseService;
import com.cristian.moneymanager.service.IncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/filter")
public class FilterController {

    private static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    // Whitelist of sortable fields (request value -> entity property)
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "date", "date",
            "amount", "amount",
            "name", "name",
            "category", "category.name"
    );

    private final IncomeService incomeService;
    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<?> filterTransactions(@RequestBody FilterDto filterDto) {
        // LocalDate.MIN is out of range for MySQL/PostgreSQL, so use a sane lower bound instead
        LocalDate startDate = filterDto.getStartDate() != null ? filterDto.getStartDate() : EARLIEST_DATE;
        // Allow one extra day because the client's local date may be ahead of the server (UTC)
        LocalDate endDate = filterDto.getEndDate() != null ? filterDto.getEndDate() : LocalDate.now().plusDays(1);
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La fecha de inicio debe ser anterior a la de fin"));
        }
        String keyword = filterDto.getKeyword() != null ? filterDto.getKeyword().trim() : "";
        String sortField = SORT_FIELDS.getOrDefault(
                filterDto.getSortField() != null ? filterDto.getSortField().toLowerCase() : "date", "date");
        Sort.Direction direction = "desc".equalsIgnoreCase(filterDto.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortField).and(Sort.by(direction, "id"));

        if (filterDto.getPage() != null) {
            int page = Math.max(0, filterDto.getPage());
            int size = Math.min(MAX_PAGE_SIZE, Math.max(1, filterDto.getSize() != null ? filterDto.getSize() : DEFAULT_PAGE_SIZE));
            PageRequest pageable = PageRequest.of(page, size, sort);
            if ("income".equalsIgnoreCase(filterDto.getType())) {
                return ResponseEntity.ok(incomeService.filterIncomesPage(startDate, endDate, keyword, pageable));
            } else if ("expense".equalsIgnoreCase(filterDto.getType())) {
                return ResponseEntity.ok(expenseService.filterExpensesPage(startDate, endDate, keyword, pageable));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo no válido, debe ser 'income' o 'expense'"));
        }

        if ("income".equalsIgnoreCase(filterDto.getType())) {
            List<IncomeDto> incomes = incomeService.filterIncomes(startDate, endDate, keyword, sort);
            return ResponseEntity.ok(incomes);
        } else if ("expense".equalsIgnoreCase(filterDto.getType())){
            List<ExpenseDto> expenses = expenseService.filterExpenses(startDate, endDate, keyword, sort);
            return ResponseEntity.ok(expenses);
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo no válido, debe ser 'income' o 'expense'"));
        }
    }
}
