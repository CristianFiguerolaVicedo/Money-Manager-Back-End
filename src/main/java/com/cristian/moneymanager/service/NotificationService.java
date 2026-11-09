package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.email.EmailTemplate;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ExpenseService expenseService;
    private final ProfileRepository profileRepository;
    private final EmailService emailService;

    @Value("${money.manager.frontendUrl}")
    private String frontendUrl;

    //@Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid") // Send every minute to test
    //@Scheduled(cron = "0 0 9 * * *", zone = "Europe/Madrid") //Send every day at 9 AM
    public void sendDailyIncomeExpenseReminder() {
        log.info("Job started: sendDailyIncomeExpenseReminder()");
        List<ProfileEntity> profiles = profileRepository.findByIsActiveTrue();
        for (ProfileEntity profile : profiles) {
            EmailContent email = EmailTemplate.builder("Recordatorio: añade tus ingresos y gastos de hoy")
                    .preheader("Un minuto al día mantiene tus cuentas al día")
                    .greeting(profile.getFullName())
                    .paragraph("Te recordamos que añadas en Money Manager los ingresos y gastos que hayas tenido hoy.")
                    .button("Ir a Money Manager", frontendUrl)
                    .build();

            sendSafely(profile.getEmail(), email);
        }
        log.info("Job completed: sendDailyIncomeExpenseReminder()");
    }

    //@Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid") // Send every minute to test
    //@Scheduled(cron = "0 0 21 * * *", zone = "Europe/Madrid") //Send every day at 9 PM
    public void sendDailyExpenseSummary() {
        log.info("Job started: sendDailyExpenseSummary()");
        List<ProfileEntity> profiles = profileRepository.findByIsActiveTrue();
        for (ProfileEntity profile : profiles) {
            List<ExpenseDto> expenses = expenseService.getExpensesForUserOnDate(profile.getId(), LocalDate.now());
            if (!expenses.isEmpty()) {
                String cell = "border-bottom:1px solid #e5e7eb;padding:10px 8px;font-size:14px;color:#1f2937;";
                String head = "border-bottom:2px solid #e5e7eb;padding:10px 8px;font-size:13px;color:#6b7280;text-align:left;";
                StringBuilder table = new StringBuilder()
                        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;font-family:Arial,sans-serif;\">")
                        .append("<tr><th style=\"").append(head).append("\">Concepto</th><th style=\"").append(head)
                        .append("\">Categoría</th><th style=\"").append(head).append("text-align:right;\">Importe</th></tr>");
                StringBuilder text = new StringBuilder();
                java.math.BigDecimal total = java.math.BigDecimal.ZERO;
                for (ExpenseDto expenseDto : expenses) {
                    String name = String.valueOf(expenseDto.getName());
                    String category = expenseDto.getCategoryId() != null ? expenseDto.getCategoryName() : "-";
                    table.append("<tr><td style=\"").append(cell).append("\">").append(HtmlUtils.htmlEscape(name)).append("</td>")
                            .append("<td style=\"").append(cell).append("\">").append(HtmlUtils.htmlEscape(category)).append("</td>")
                            .append("<td style=\"").append(cell).append("text-align:right;\">").append(formatEuros(expenseDto.getAmount())).append("</td></tr>");
                    text.append("- ").append(name).append(" (").append(category).append("): ").append(formatEuros(expenseDto.getAmount())).append("\n");
                    if (expenseDto.getAmount() != null) {
                        total = total.add(expenseDto.getAmount());
                    }
                }
                table.append("<tr><td colspan=\"2\" style=\"padding:10px 8px;font-size:14px;font-weight:bold;\">Total</td>")
                        .append("<td style=\"padding:10px 8px;font-size:14px;font-weight:bold;text-align:right;\">").append(formatEuros(total)).append("</td></tr></table>");
                text.append("Total: ").append(formatEuros(total));

                EmailContent email = EmailTemplate.builder("Tu resumen de gastos de hoy")
                        .preheader("Hoy has gastado " + formatEuros(total))
                        .greeting(profile.getFullName())
                        .paragraph("Estos son los gastos que has registrado hoy:")
                        .rawBlock(table.toString(), text.toString())
                        .button("Ver mis gastos", frontendUrl + "/expense")
                        .build();

                sendSafely(profile.getEmail(), email);
            }
        }
        log.info("Job completed: sendDailyExpenseSummary()");
    }

    // Only active accounts get emails, and one failing address must not abort the whole job
    // Only active accounts get emails, and one failing address must not abort the whole job
    private void sendSafely(String to, EmailContent email) {
        try {
            emailService.send(to, email);
        } catch (Exception e) {
            log.error("Failed to send '{}' to {}: {}", email.subject(), to, e.getMessage());
        }
    }

    private static String formatEuros(java.math.BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        java.text.NumberFormat euros = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.of("es", "ES"));
        return euros.format(amount);
    }
}
