package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.BudgetDto;
import com.cristian.moneymanager.entity.BudgetEntity;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.BudgetRepository;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Monthly budgets per expense category (F8). */
@Service
@RequiredArgsConstructor
public class BudgetService {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    /** From this share of the budget on, the category is "warning". */
    static final int WARNING_PERCENT = 80;

    private final ProfileService profileService;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    /** Every expense category of the current user, with its budget (if any) and what was spent in {@code month}. */
    @Transactional(readOnly = true)
    public List<BudgetDto> getBudgets(YearMonth month) {
        return getBudgets(profileService.getCurrentProfile().getId(), month);
    }

    /** Same, for any user (used by the monthly report email). */
    @Transactional(readOnly = true)
    public List<BudgetDto> getBudgets(Long profileId, YearMonth month) {
        Map<Long, BigDecimal> budgets = budgetRepository.findByProfileId(profileId).stream()
                .collect(Collectors.toMap(b -> b.getCategory().getId(), BudgetEntity::getAmount));

        Map<Long, BigDecimal> spent = new HashMap<>();
        for (ExpenseEntity expense : expenseRepository.findByProfileIdAndDateBetween(profileId, month.atDay(1), month.atEndOfMonth())) {
            if (expense.getCategory() != null && expense.getAmount() != null) {
                spent.merge(expense.getCategory().getId(), expense.getAmount(), BigDecimal::add);
            }
        }

        return categoryRepository.findByTypeIgnoreCaseAndProfileId("expense", profileId).stream()
                .map(category -> toDto(category, budgets.get(category.getId()), spent.getOrDefault(category.getId(), BigDecimal.ZERO)))
                // Categories with a budget first (most used first), then the rest by name
                .sorted(Comparator.comparing((BudgetDto b) -> b.amount() == null)
                        .thenComparing(b -> b.percent() == null ? 0 : -b.percent())
                        .thenComparing(BudgetDto::categoryName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional
    public BudgetDto setBudget(Long categoryId, BudgetDto.SetBudgetRequest request, YearMonth month) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = expenseCategory(categoryId, profile.getId());
        BigDecimal amount = validateAmount(request == null ? null : request.amount());

        BudgetEntity budget = budgetRepository.findByCategoryIdAndProfileId(categoryId, profile.getId())
                .orElseGet(() -> BudgetEntity.builder().profile(profile).category(category).build());
        budget.setAmount(amount);
        budgetRepository.save(budget);

        return getBudgets(profile.getId(), month).stream()
                .filter(b -> b.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteBudget(Long categoryId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        BudgetEntity budget = budgetRepository.findByCategoryIdAndProfileId(categoryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta categoría no tiene presupuesto"));
        budgetRepository.delete(budget);
    }

    private CategoryEntity expenseCategory(Long categoryId, Long profileId) {
        CategoryEntity category = categoryRepository.findByIdAndProfileId(categoryId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        if (!"expense".equalsIgnoreCase(category.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los presupuestos solo se pueden poner a categorías de gastos");
        }
        return category;
    }

    static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El presupuesto debe ser un importe mayor que 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El presupuesto es demasiado grande");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    static BudgetDto toDto(CategoryEntity category, BigDecimal budget, BigDecimal spent) {
        if (budget == null) {
            return new BudgetDto(category.getId(), category.getName(), category.getIcon(), null, spent, null, null, "none");
        }
        int percent = budget.signum() == 0 ? 0
                : spent.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.HALF_UP).intValue();
        String status = percent >= 100 && spent.compareTo(budget) > 0 ? "over"
                : percent >= WARNING_PERCENT ? "warning" : "ok";
        return new BudgetDto(category.getId(), category.getName(), category.getIcon(), budget, spent, budget.subtract(spent), percent, status);
    }
}
