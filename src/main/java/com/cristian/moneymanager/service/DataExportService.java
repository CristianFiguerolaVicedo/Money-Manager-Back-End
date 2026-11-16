package com.cristian.moneymanager.service;

import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.GoalEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.AttachmentRepository;
import com.cristian.moneymanager.repository.BudgetRepository;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.GoalContributionRepository;
import com.cristian.moneymanager.repository.GoalRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.repository.RecurringRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GDPR right of access and portability (arts. 15 and 20): everything stored about the logged-in user,
 * in a machine-readable JSON. Secrets (password hash, session and email tokens) are left out on purpose.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final ProfileService profileService;
    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final GoalRepository goalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final RecurringRepository recurringRepository;
    private final AttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> export() {
        ProfileEntity profile = profileService.getCurrentProfile();
        Long id = profile.getId();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", LocalDateTime.now());
        data.put("format", "Money Manager · exportación de datos v1");

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("fullName", profile.getFullName());
        account.put("email", profile.getEmail());
        account.put("profileImageUrl", profile.getProfileImageUrl());
        account.put("createdAt", profile.getCreatedAt());
        account.put("active", profile.getIsActive());
        account.put("termsAcceptedAt", profile.getTermsAcceptedAt());
        account.put("termsVersion", profile.getTermsVersion());
        account.put("emailDailyReminder", Boolean.TRUE.equals(profile.getEmailDailyReminder()));
        account.put("emailDailySummary", Boolean.TRUE.equals(profile.getEmailDailySummary()));
        account.put("emailMonthlyReport", Boolean.TRUE.equals(profile.getEmailMonthlyReport()));
        data.put("account", account);

        data.put("categories", categoryRepository.findByProfileId(id).stream().map(c -> row(
                "id", c.getId(), "name", c.getName(), "type", c.getType(), "icon", c.getIcon(), "createdAt", c.getCreatedAt())).toList());
        data.put("incomes", incomeRepository.findByProfileIdOrderByDateDesc(id).stream().map(DataExportService::income).toList());
        data.put("expenses", expenseRepository.findByProfileIdOrderByDateDesc(id).stream().map(DataExportService::expense).toList());
        data.put("budgets", budgetRepository.findByProfileId(id).stream().map(b -> row(
                "categoryId", b.getCategory().getId(), "category", b.getCategory().getName(), "monthlyAmount", b.getAmount())).toList());
        data.put("goals", goalRepository.findByProfileIdOrderByCreatedAtAsc(id).stream().map(this::goal).toList());
        data.put("recurring", recurringRepository.findByProfileIdOrderByNextDateAscIdAsc(id).stream().map(r -> row(
                "id", r.getId(), "type", r.getType(), "name", r.getName(), "icon", r.getIcon(), "amount", r.getAmount(),
                "categoryId", categoryId(r.getCategory()), "frequency", r.getFrequency(), "startDate", r.getStartDate(),
                "endDate", r.getEndDate(), "nextDate", r.getNextDate(), "active", r.getActive())).toList());
        data.put("attachments", attachmentRepository.exportForProfile(id).stream().map(a -> row(
                "id", a[0], "filename", a[1], "contentType", a[2], "sizeBytes", a[3],
                "incomeId", a[4], "expenseId", a[5], "createdAt", a[6])).toList());
        return data;
    }

    private static Map<String, Object> income(IncomeEntity i) {
        return row("id", i.getId(), "name", i.getName(), "icon", i.getIcon(), "amount", i.getAmount(), "date", i.getDate(),
                "categoryId", categoryId(i.getCategory()), "note", i.getNote(), "recurringId", i.getRecurringId(), "createdAt", i.getCreatedAt());
    }

    private static Map<String, Object> expense(ExpenseEntity e) {
        return row("id", e.getId(), "name", e.getName(), "icon", e.getIcon(), "amount", e.getAmount(), "date", e.getDate(),
                "categoryId", categoryId(e.getCategory()), "note", e.getNote(), "recurringId", e.getRecurringId(), "createdAt", e.getCreatedAt());
    }

    private Map<String, Object> goal(GoalEntity g) {
        Map<String, Object> goal = row("id", g.getId(), "name", g.getName(), "icon", g.getIcon(), "targetAmount", g.getTargetAmount(),
                "deadline", g.getDeadline(), "createdAt", g.getCreatedAt());
        goal.put("contributions", goalContributionRepository.findByGoalIdOrderByDateDescIdDesc(g.getId()).stream().map(c -> row(
                "id", c.getId(), "amount", c.getAmount(), "date", c.getDate(), "note", c.getNote())).toList());
        return goal;
    }

    private static Long categoryId(CategoryEntity category) {
        return category == null ? null : category.getId();
    }

    /** Ordered map that, unlike Map.of, allows null values. */
    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
