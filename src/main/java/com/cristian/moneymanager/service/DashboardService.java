package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.dto.IncomeDto;
import com.cristian.moneymanager.dto.RecentTransactionDto;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final ProfileService profileService;
    private final RecurringService recurringService;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    /** Months shown in the dashboard's income vs expense chart (current month included). */
    static final int TREND_MONTHS = 6;

    public Map<String, Object> getDashboardData() {
        recurringService.catchUpCurrentUser(); // recurring movements due since the last visit
        ProfileEntity profile = profileService.getCurrentProfile();
        Map<String, Object> returnValue = new LinkedHashMap<>();
        List<IncomeDto> latestIncomes = incomeService.getLatest5IncomesForCurrentUser();
        List<ExpenseDto> latestExpenses = expenseService.getLatest5ExpensesForCurrentUser();

        List<RecentTransactionDto> recentTransactions = Stream.concat(latestIncomes.stream()
                        .map(income ->
                                RecentTransactionDto.builder()
                                        .id(income.getId())
                                        .profileId(profile.getId())
                                        .icon(income.getIcon())
                                        .name(income.getName())
                                        .amount(income.getAmount())
                                        .date(income.getDate())
                                        .createdAt(income.getCreatedAt())
                                        .updatedAt(income.getUpdatedAt())
                                        .type("income")
                                        .build()),
                latestExpenses.stream()
                        .map(expense -> RecentTransactionDto.builder()
                                .id(expense.getId())
                                .profileId(profile.getId())
                                .icon(expense.getIcon())
                                .name(expense.getName())
                                .amount(expense.getAmount())
                                .date(expense.getDate())
                                .createdAt(expense.getCreatedAt())
                                .updatedAt(expense.getUpdatedAt())
                                .type("expense")
                                .build()))
                .sorted((a, b) -> {
                    int cmp = b.getDate().compareTo(a.getDate());
                    if (cmp == 0 && a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }

                    return cmp;
                }).toList();

        BigDecimal totalIncome = incomeService.getTotalIncomesForCurrentUser();
        BigDecimal totalExpense = expenseService.getTotalExpensesForCurrentUser();

        returnValue.put("totalBalance", totalIncome.subtract(totalExpense));
        returnValue.put("totalIncome", totalIncome);
        returnValue.put("totalExpense", totalExpense);
        returnValue.put("recent5Expense", latestExpenses);
        returnValue.put("recent5Incomes", latestIncomes);
        returnValue.put("recentTransactions", recentTransactions);

        YearMonth currentMonth = YearMonth.now(MonthParam.APP_ZONE);
        returnValue.put("monthlySummary", monthlySummary(profile.getId(), currentMonth));
        returnValue.put("expenseByCategory", expenseByCategory(profile.getId(), currentMonth));
        return returnValue;
    }

    /** Income, expense and balance of each of the last months, oldest first (months without data are 0). */
    List<Map<String, Object>> monthlySummary(Long profileId, YearMonth currentMonth) {
        YearMonth first = currentMonth.minusMonths(TREND_MONTHS - 1);
        Map<YearMonth, BigDecimal> incomes = new HashMap<>();
        Map<YearMonth, BigDecimal> expenses = new HashMap<>();

        for (IncomeEntity income : incomeRepository.findByProfileIdAndDateBetween(profileId, first.atDay(1), currentMonth.atEndOfMonth())) {
            incomes.merge(YearMonth.from(income.getDate()), amountOf(income.getAmount()), BigDecimal::add);
        }
        for (ExpenseEntity expense : expenseRepository.findByProfileIdAndDateBetween(profileId, first.atDay(1), currentMonth.atEndOfMonth())) {
            expenses.merge(YearMonth.from(expense.getDate()), amountOf(expense.getAmount()), BigDecimal::add);
        }

        List<Map<String, Object>> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            BigDecimal income = incomes.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal expense = expenses.getOrDefault(month, BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month.toString());
            row.put("income", income);
            row.put("expense", expense);
            row.put("balance", income.subtract(expense));
            months.add(row);
        }
        return months;
    }

    /** This month's expenses per category, biggest first. */
    List<Map<String, Object>> expenseByCategory(Long profileId, YearMonth month) {
        Map<Long, Map<String, Object>> byCategory = new LinkedHashMap<>();
        for (ExpenseEntity expense : expenseRepository.findByProfileIdAndDateBetween(profileId, month.atDay(1), month.atEndOfMonth())) {
            Map<String, Object> row = byCategory.computeIfAbsent(expense.getCategory().getId(), id -> {
                Map<String, Object> newRow = new LinkedHashMap<>();
                newRow.put("categoryId", id);
                newRow.put("categoryName", expense.getCategory().getName());
                newRow.put("icon", expense.getCategory().getIcon());
                newRow.put("amount", BigDecimal.ZERO);
                return newRow;
            });
            row.put("amount", ((BigDecimal) row.get("amount")).add(amountOf(expense.getAmount())));
        }
        return byCategory.values().stream()
                .sorted(Comparator.comparing((Map<String, Object> row) -> (BigDecimal) row.get("amount")).reversed())
                .toList();
    }

    private static BigDecimal amountOf(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
