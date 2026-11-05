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
            String body = "Hi " + HtmlUtils.htmlEscape(String.valueOf(profile.getFullName())) + ",<br><br>"
                    + "This is a friendly reminder to add your incomes and expenses for today in Money Manager.<br><br>"
                    + "<a href='" + frontendUrl + "' style='display:inline-block;padding:10px 20px;background-color:#4CAF50;color:#fff;text-decoration:none;border-radius:5px;font-weight:bold;'>Go to Money Manager</a>"
                    + "<br><br>Best regards, <br>Money Manager Team";

            sendSafely(profile.getEmail(), "Daily reminder: Add your income and expenses", body);
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
                table.append("<tr style='background-color:#f2f2f2;'><th style='border:1px solid #ddd;padding:8px;'>S.No</th><th style='border:1px solid #ddd;padding:8px;'>Name</th><th style='border:1px solid #ddd;padding:8px;'>Amount</th><th style='border:1px solid #ddd;padding:8px;'>Category</th></tr>");
                int i = 1;
                for (ExpenseDto expenseDto : expenses) {
                    table.append("<tr>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(i++).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(HtmlUtils.htmlEscape(String.valueOf(expenseDto.getName()))).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(expenseDto.getAmount()).append("</td>");
                    table.append("<td style='border:1px solid #ddd;padding:8px;'>").append(HtmlUtils.htmlEscape(expenseDto.getCategoryId() != null ? expenseDto.getCategoryName() : "N/A")).append("</td>");
                    table.append("</tr>");
                }
                table.append("</table>");
                String body = "Hi " + HtmlUtils.htmlEscape(String.valueOf(profile.getFullName())) + ",<br><br> Here is a summary of your today's expenses: <br><br>"
                        + table + "<br><br>"
                        + "Best regards <br><br> Money Manager Team";

                sendSafely(profile.getEmail(), "Your daily expense summary", body);
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
}
