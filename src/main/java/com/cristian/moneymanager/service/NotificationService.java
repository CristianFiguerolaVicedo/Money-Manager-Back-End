package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
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
            String body = "Hola, " + HtmlUtils.htmlEscape(String.valueOf(profile.getFullName())) + ":<br><br>"
                    + "Te recordamos que añadas tus ingresos y gastos de hoy en Money Manager.<br><br>"
                    + "<a href='" + frontendUrl + "' style='display:inline-block;padding:10px 20px;background-color:#4CAF50;color:#fff;text-decoration:none;border-radius:5px;font-weight:bold;'>Ir a Money Manager</a>"
                    + "<br><br>Un saludo,<br>El equipo de Money Manager";

            sendSafely(profile.getEmail(), "Recordatorio: añade tus ingresos y gastos de hoy", body);
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
                StringBuilder table = new StringBuilder();
                table.append("<table style='border-collapse:collapse;width:100%;'>");
                table.append("<tr style='background-color:#f2f2f2;'><th style='border:1px solid #ddd;padding:8px;'>Nº</th><th style='border:1px solid #ddd;padding:8px;'>Nombre</th><th style='border:1px solid #ddd;padding:8px;'>Importe</th><th style='border:1px solid #ddd;padding:8px;'>Categoría</th></tr>");
                int i = 1;
                for (ExpenseDto expenseDto : expenses) {
                    table.append("<tr>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(i++).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(HtmlUtils.htmlEscape(String.valueOf(expenseDto.getName()))).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(formatEuros(expenseDto.getAmount())).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(HtmlUtils.htmlEscape(expenseDto.getCategoryId() != null ? expenseDto.getCategoryName() : "N/A")).append("</td>");
                    table.append("</tr>");
                }
                table.append("</table>");
                String body = "Hola, " + HtmlUtils.htmlEscape(String.valueOf(profile.getFullName())) + ":<br><br>Este es el resumen de tus gastos de hoy:<br><br>"
                        + table + "<br><br>"
                        + "Un saludo,<br>El equipo de Money Manager";

                sendSafely(profile.getEmail(), "Tu resumen de gastos de hoy", body);
            }
        }
        log.info("Job completed: sendDailyExpenseSummary()");
    }

    // Only active accounts get emails, and one failing address must not abort the whole job
    private void sendSafely(String to, String subject, String body) {
        try {
            emailService.sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send '{}' to {}: {}", subject, to, e.getMessage());
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
