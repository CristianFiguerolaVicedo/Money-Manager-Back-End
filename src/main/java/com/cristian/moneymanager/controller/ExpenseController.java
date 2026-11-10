package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.service.RecurringService;
import com.cristian.moneymanager.service.ExpenseService;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final RecurringService recurringService;

    @PostMapping
    public ResponseEntity<ExpenseDto> addExpense(@RequestBody ExpenseDto dto) {
        ExpenseDto savedExpense = expenseService.addExpense(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedExpense);
    }

    /** Expenses of one month; ?month=yyyy-MM, defaults to the current month. */
    @GetMapping
    public ResponseEntity<List<ExpenseDto>> getExpenses(@RequestParam(required = false) String month) {
        recurringService.catchUpCurrentUser();
        List<ExpenseDto> list = expenseService.getExpensesForMonth(MonthParam.parseOrCurrent(month));
        return ResponseEntity.ok(list);
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ExpenseDto> updateExpense(@PathVariable Long id, @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.updateExpense(id, dto));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
