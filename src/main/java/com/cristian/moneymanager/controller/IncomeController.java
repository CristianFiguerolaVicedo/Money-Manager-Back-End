package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.IncomeDto;
import com.cristian.moneymanager.service.IncomeService;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    @PostMapping
    public ResponseEntity<IncomeDto> addIncome(@RequestBody IncomeDto dto) {
        IncomeDto savedIncome = incomeService.addIncome(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedIncome);
    }

    /** Incomes of one month; ?month=yyyy-MM, defaults to the current month. */
    @GetMapping
    public ResponseEntity<List<IncomeDto>> getIncomes(@RequestParam(required = false) String month) {
        List<IncomeDto> list = incomeService.getIncomesForMonth(MonthParam.parseOrCurrent(month));
        return ResponseEntity.ok(list);
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<IncomeDto> updateIncome(@PathVariable Long id, @RequestBody IncomeDto dto) {
        return ResponseEntity.ok(incomeService.updateIncome(id, dto));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable Long id) {
        incomeService.deleteIncome(id);
        return ResponseEntity.noContent().build();
    }
}
