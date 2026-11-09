package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import com.cristian.moneymanager.util.MonthParam;

import java.io.IOException;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/email")
public class EmailController {
    private final ExcelService excelService;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final EmailService emailService;
    private final ProfileService profileService;

    @GetMapping("/income-excel")
    public ResponseEntity<Void> emailIncomeExcel(@RequestParam(required = false) String month) throws IOException {
        YearMonth target = MonthParam.parseOrCurrent(month);
        ProfileEntity profile = profileService.getCurrentProfile();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        excelService.writeIncomesToExcel(baos, incomeService.getIncomesForMonth(target));
        emailService.sendEmailWithAttachment(profile.getEmail(), "Informe de ingresos (" + target + ")",
                "Te adjuntamos tu informe de ingresos de " + target + ".", baos.toByteArray(), "income-" + target + ".xlsx");
        return ResponseEntity.ok(null);
    }

    @GetMapping("/expense-excel")
    public ResponseEntity<Void> emailExpenseExcel(@RequestParam(required = false) String month) throws IOException {
        YearMonth target = MonthParam.parseOrCurrent(month);
        ProfileEntity profile = profileService.getCurrentProfile();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        excelService.writeExpensesToExcel(baos, expenseService.getExpensesForMonth(target));
        emailService.sendEmailWithAttachment(profile.getEmail(), "Informe de gastos (" + target + ")",
                "Te adjuntamos tu informe de gastos de " + target + ".", baos.toByteArray(), "expense-" + target + ".xlsx");
        return ResponseEntity.ok(null);
    }
}
