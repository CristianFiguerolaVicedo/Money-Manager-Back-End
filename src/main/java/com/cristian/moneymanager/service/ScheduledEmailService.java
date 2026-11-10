package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.BudgetDto;
import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.email.EmailContent;
import com.cristian.moneymanager.email.EmailTemplate;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.JobRunEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.repository.JobRunRepository;
import com.cristian.moneymanager.repository.ProfileRepository;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Optional emails each user can turn on in their profile (F10):
 * - daily-reminder: in the evening, only if nothing was added today
 * - daily-summary: today's expenses, only if there were any
 * - monthly-report: on the 1st, last month's totals, top categories and budgets
 *
 * Jobs are started from outside (see JobsController) and run at most once per day each.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledEmailService {

    public static final String DAILY_REMINDER = "daily-reminder";
    public static final String DAILY_SUMMARY = "daily-summary";
    public static final String MONTHLY_REPORT = "monthly-report";
    public static final List<String> JOBS = List.of(DAILY_REMINDER, DAILY_SUMMARY, MONTHLY_REPORT);

    private static final Locale SPANISH = Locale.of("es", "ES");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfileRepository profileRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final JobRunRepository jobRunRepository;
    private final EmailService emailService;

    @Value("${money.manager.frontendUrl}")
    private String frontendUrl;

    public record JobResult(String job, LocalDate date, boolean skipped, int emailsSent, String reason) {
    }

    /** Runs a job for today (Madrid time) unless it already ran today. */
    public JobResult run(String job) {
        if (!JOBS.contains(job)) {
            throw new IllegalArgumentException("Unknown job " + job);
        }
        LocalDate today = LocalDate.now(MonthParam.APP_ZONE);
        if (MONTHLY_REPORT.equals(job) && today.getDayOfMonth() != 1) {
            return new JobResult(job, today, true, 0, "monthly report only runs on day 1");
        }

        JobRunEntity run;
        try {
            run = jobRunRepository.saveAndFlush(JobRunEntity.builder()
                    .jobName(job).runDate(today).startedAt(LocalDateTime.now(MonthParam.APP_ZONE)).build());
        } catch (DataIntegrityViolationException e) {
            return new JobResult(job, today, true, 0, "already ran today");
        }

        log.info("Job started: {} for {}", job, today);
        int sent = switch (job) {
            case DAILY_REMINDER -> sendToEach(profileRepository.findByIsActiveTrueAndEmailDailyReminderTrue(), p -> dailyReminder(p, today));
            case DAILY_SUMMARY -> sendToEach(profileRepository.findByIsActiveTrueAndEmailDailySummaryTrue(), p -> dailySummary(p, today));
            default -> sendToEach(profileRepository.findByIsActiveTrueAndEmailMonthlyReportTrue(), p -> monthlyReport(p, YearMonth.from(today).minusMonths(1)));
        };
        run.setEmailsSent(sent);
        jobRunRepository.save(run);
        log.info("Job completed: {} ({} emails)", job, sent);
        return new JobResult(job, today, false, sent, null);
    }

    /** Builds (null = nothing to send) and sends one email per profile; one failure never stops the rest. */
    private int sendToEach(List<ProfileEntity> profiles, Function<ProfileEntity, EmailContent> builder) {
        int sent = 0;
        for (ProfileEntity profile : profiles) {
            try {
                EmailContent email = builder.apply(profile);
                if (email != null) {
                    emailService.send(profile.getEmail(), email);
                    sent++;
                }
            } catch (Exception e) {
                log.error("Scheduled email to {} failed: {}", profile.getEmail(), e.getMessage());
            }
        }
        return sent;
    }

    EmailContent dailyReminder(ProfileEntity profile, LocalDate today) {
        if (incomeRepository.existsByProfileIdAndDate(profile.getId(), today)
                || expenseRepository.existsByProfileIdAndDate(profile.getId(), today)) {
            return null; // already up to date
        }
        return EmailTemplate.builder("¿Has tenido gastos o ingresos hoy?")
                .preheader("Un minuto al día mantiene tus cuentas al día")
                .greeting(profile.getFullName())
                .paragraph("Hoy todavía no has apuntado nada en Money Manager. Si has tenido algún gasto o ingreso, añádelo ahora que lo tienes fresco.")
                .button("Añadir un gasto", frontendUrl + "/expense")
                .unsubscribeLink(unsubscribeUrl(profile))
                .build();
    }

    EmailContent dailySummary(ProfileEntity profile, LocalDate today) {
        List<ExpenseDto> expenses = expenseService.getExpensesForUserOnDate(profile.getId(), today);
        if (expenses.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        List<String[]> rows = new ArrayList<>();
        for (ExpenseDto expense : expenses) {
            rows.add(new String[]{String.valueOf(expense.getName()),
                    expense.getCategoryName() != null ? expense.getCategoryName() : "-", euros(expense.getAmount())});
            if (expense.getAmount() != null) {
                total = total.add(expense.getAmount());
            }
        }
        Table table = table(new String[]{"Concepto", "Categoría", "Importe"}, rows, new String[]{"Total", "", euros(total)});

        return EmailTemplate.builder("Tu resumen de gastos de hoy")
                .preheader("Hoy has gastado " + euros(total))
                .greeting(profile.getFullName())
                .paragraph("Estos son los gastos que has apuntado hoy:")
                .rawBlock(table.html(), table.text())
                .button("Ver mis gastos", frontendUrl + "/expense")
                .unsubscribeLink(unsubscribeUrl(profile))
                .build();
    }

    EmailContent monthlyReport(ProfileEntity profile, YearMonth month) {
        List<IncomeEntity> incomes = incomeRepository.findByProfileIdAndDateBetween(profile.getId(), month.atDay(1), month.atEndOfMonth());
        List<ExpenseEntity> expenses = expenseRepository.findByProfileIdAndDateBetween(profile.getId(), month.atDay(1), month.atEndOfMonth());
        if (incomes.isEmpty() && expenses.isEmpty()) {
            return null;
        }
        BigDecimal income = incomes.stream().map(IncomeEntity::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = expenses.stream().map(ExpenseEntity::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = income.subtract(expense);
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, SPANISH) + " de " + month.getYear();

        Table totals = table(new String[]{"", "Importe"}, List.of(
                new String[]{"Ingresos", euros(income)},
                new String[]{"Gastos", euros(expense)}), new String[]{"Saldo", euros(balance)});

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ExpenseEntity e : expenses) {
            if (e.getAmount() != null) {
                byCategory.merge(e.getCategory() != null ? e.getCategory().getName() : "Sin categoría", e.getAmount(), BigDecimal::add);
            }
        }
        List<String[]> topRows = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new String[]{entry.getKey(), euros(entry.getValue())})
                .toList();

        List<String[]> budgetRows = budgetService.getBudgets(profile.getId(), month).stream()
                .filter(b -> b.amount() != null)
                .sorted(Comparator.comparing(BudgetDto::percent).reversed())
                .map(b -> new String[]{b.categoryName(), euros(b.spent()) + " de " + euros(b.amount()),
                        b.percent() + "%" + ("over".equals(b.status()) ? " (superado)" : "")})
                .toList();

        StringBuilder html = new StringBuilder(totals.html());
        StringBuilder text = new StringBuilder(totals.text());
        if (!topRows.isEmpty()) {
            Table top = table(new String[]{"Categorías con más gasto", "Importe"}, topRows, null);
            html.append("<div style=\"height:20px\"></div>").append(top.html());
            text.append("\n\nCategorías con más gasto:\n").append(top.text());
        }
        if (!budgetRows.isEmpty()) {
            Table budgets = table(new String[]{"Presupuesto", "Gastado", "Uso"}, budgetRows, null);
            html.append("<div style=\"height:20px\"></div>").append(budgets.html());
            text.append("\n\nPresupuestos:\n").append(budgets.text());
        }

        String verdict = balance.signum() >= 0
                ? "Ahorraste " + euros(balance) + " en " + monthName + "."
                : "En " + monthName + " gastaste " + euros(balance.negate()) + " más de lo que ingresaste.";
        return EmailTemplate.builder("Tu resumen de " + monthName)
                .preheader(verdict)
                .greeting(profile.getFullName())
                .paragraph(verdict + " Este es el resumen del mes:")
                .rawBlock(html.toString(), text.toString())
                .button("Ver el panel", frontendUrl + "/dashboard")
                .unsubscribeLink(unsubscribeUrl(profile))
                .build();
    }

    /** Creates the user's unsubscribe token the first time it is needed. */
    private String unsubscribeUrl(ProfileEntity profile) {
        if (profile.getEmailUnsubscribeToken() == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            profile.setEmailUnsubscribeToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            profileRepository.save(profile);
        }
        return frontendUrl + "/unsubscribe?token=" + profile.getEmailUnsubscribeToken();
    }

    // ------------------------------------------------------------------ formatting

    private record Table(String html, String text) {
    }

    /** Simple email-safe table; the last column is right-aligned. */
    private static Table table(String[] header, List<String[]> rows, String[] footer) {
        String cell = "border-bottom:1px solid #e5e7eb;padding:10px 8px;font-size:14px;color:#1f2937;";
        String head = "border-bottom:2px solid #e5e7eb;padding:10px 8px;font-size:13px;color:#6b7280;text-align:left;";
        String foot = "padding:10px 8px;font-size:14px;font-weight:bold;color:#1f2937;";
        StringBuilder html = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;font-family:Arial,sans-serif;\">");
        html.append(row(header, "th", head));
        StringBuilder text = new StringBuilder();
        for (String[] r : rows) {
            html.append(row(r, "td", cell));
            text.append("- ").append(String.join(" · ", nonEmpty(r))).append("\n");
        }
        if (footer != null) {
            html.append(row(footer, "td", foot));
            text.append(String.join(": ", nonEmpty(footer)));
        }
        html.append("</table>");
        return new Table(html.toString(), text.toString().stripTrailing());
    }

    private static String row(String[] cells, String tag, String style) {
        StringBuilder html = new StringBuilder("<tr>");
        for (int i = 0; i < cells.length; i++) {
            boolean last = i == cells.length - 1;
            html.append('<').append(tag).append(" style=\"").append(style).append(last ? "text-align:right;" : "").append("\">")
                    .append(HtmlUtils.htmlEscape(cells[i] == null ? "" : cells[i])).append("</").append(tag).append('>');
        }
        return html.append("</tr>").toString();
    }

    private static List<String> nonEmpty(String[] cells) {
        return java.util.Arrays.stream(cells).filter(c -> c != null && !c.isBlank()).toList();
    }

    static String euros(BigDecimal amount) {
        return amount == null ? "-" : NumberFormat.getCurrencyInstance(SPANISH).format(amount);
    }
}
