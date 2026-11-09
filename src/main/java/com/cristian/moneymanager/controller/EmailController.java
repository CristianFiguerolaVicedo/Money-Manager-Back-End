package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.email.EmailTemplate;
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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
        EmailContent email = EmailTemplate.builder("Tu informe de ingresos de " + monthName(target))
                .preheader("Te adjuntamos el Excel con tus ingresos")
                .greeting(profile.getFullName())
                .paragraph("Te adjuntamos en Excel tus ingresos de " + monthName(target) + ".")
                .build();
        emailService.sendWithAttachment(profile.getEmail(), email, baos.toByteArray(), "ingresos-" + target + ".xlsx");
        return ResponseEntity.ok(null);
    }

    @GetMapping("/expense-excel")
    public ResponseEntity<Void> emailExpenseExcel(@RequestParam(required = false) String month) throws IOException {
        YearMonth target = MonthParam.parseOrCurrent(month);
        ProfileEntity profile = profileService.getCurrentProfile();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        excelService.writeExpensesToExcel(baos, expenseService.getExpensesForMonth(target));
        EmailContent email = EmailTemplate.builder("Tu informe de gastos de " + monthName(target))
                .preheader("Te adjuntamos el Excel con tus gastos")
                .greeting(profile.getFullName())
                .paragraph("Te adjuntamos en Excel tus gastos de " + monthName(target) + ".")
                .build();
        emailService.sendWithAttachment(profile.getEmail(), email, baos.toByteArray(), "gastos-" + target + ".xlsx");
        return ResponseEntity.ok(null);
    }

    /** 2026-10 -> "octubre de 2026" */
    private static String monthName(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.of("es", "ES")));
    }
}
