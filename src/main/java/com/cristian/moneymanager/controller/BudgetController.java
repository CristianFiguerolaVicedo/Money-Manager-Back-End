package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.BudgetDto;
import com.cristian.moneymanager.service.BudgetService;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    /** Expense categories with their budget and what was spent in the month (?month=YYYY-MM, default current). */
    @GetMapping
    public ResponseEntity<List<BudgetDto>> getBudgets(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(budgetService.getBudgets(MonthParam.parseOrCurrent(month)));
    }

    /** Creates or changes the monthly budget of an expense category. */
    @PutMapping("/{categoryId}")
    public ResponseEntity<BudgetDto> setBudget(@PathVariable Long categoryId,
                                               @RequestBody BudgetDto.SetBudgetRequest request,
                                               @RequestParam(required = false) String month) {
        return ResponseEntity.ok(budgetService.setBudget(categoryId, request, MonthParam.parseOrCurrent(month)));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long categoryId) {
        budgetService.deleteBudget(categoryId);
        return ResponseEntity.noContent().build();
    }
}
