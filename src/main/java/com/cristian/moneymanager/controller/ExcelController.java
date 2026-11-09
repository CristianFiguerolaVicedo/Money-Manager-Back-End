package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.service.ExcelService;
import com.cristian.moneymanager.service.ExpenseService;
import com.cristian.moneymanager.service.IncomeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cristian.moneymanager.util.MonthParam;

import java.io.IOException;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/excel")
public class ExcelController {
    private final ExcelService excelService;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;

    @GetMapping("/download/income")
    public void downloadIncomeExcel(@RequestParam(required = false) String month, HttpServletResponse response) throws IOException {
        YearMonth target = MonthParam.parseOrCurrent(month);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=income-" + target + ".xlsx");
        excelService.writeIncomesToExcel(response.getOutputStream(), incomeService.getIncomesForMonth(target));
    }

    @GetMapping("/download/expense")
    public void downloadExpenseExcel(@RequestParam(required = false) String month, HttpServletResponse response) throws IOException {
        YearMonth target = MonthParam.parseOrCurrent(month);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=expense-" + target + ".xlsx");
        excelService.writeExpensesToExcel(response.getOutputStream(), expenseService.getExpensesForMonth(target));
    }
}
