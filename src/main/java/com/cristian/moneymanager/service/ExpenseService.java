package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.util.MonthParam;
import com.cristian.moneymanager.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import com.cristian.moneymanager.dto.PageDto;
import com.cristian.moneymanager.util.LikeEscape;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ProfileService profileService;

    public ExpenseDto addExpense(ExpenseDto dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        validate(dto);
        CategoryEntity category = findExpenseCategory(dto.getCategoryId(), profile);

        ExpenseEntity newExpense = toEntity(dto, profile, category);
        newExpense = expenseRepository.save(newExpense);

        return toDto(newExpense);
    }

    public List<ExpenseDto> getCurrentMonthExpensesForCurrentUser() {
        return getExpensesForMonth(YearMonth.now(MonthParam.APP_ZONE));
    }

    /** Expenses of the current user in the given month, newest first. */
    public List<ExpenseDto> getExpensesForMonth(YearMonth month) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<ExpenseEntity> list = expenseRepository.findByProfileIdAndDateBetweenOrderByDateDescIdDesc(
                profile.getId(), month.atDay(1), month.atEndOfMonth());

        return list.stream()
                .map(this::toDto)
                .toList();
    }

    public ExpenseDto updateExpense(Long id, ExpenseDto dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        ExpenseEntity entity = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        if (!entity.getProfile().getId().equals(profile.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar este gasto");
        }

        validate(dto);
        CategoryEntity category = findExpenseCategory(dto.getCategoryId(), profile);

        entity.setName(dto.getName().trim());
        entity.setIcon(dto.getIcon());
        entity.setAmount(dto.getAmount());
        if (dto.getDate() != null) {
            entity.setDate(dto.getDate());
        }
        entity.setCategory(category);

        return toDto(expenseRepository.save(entity));
    }

    public void deleteExpense(Long id) {
        ProfileEntity profile = profileService.getCurrentProfile();
        ExpenseEntity entity = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        if (!entity.getProfile().getId().equals(profile.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes borrar este gasto");
        }

        expenseRepository.delete(entity);
    }

    /** One page of a search, with the total amount of every matching expense. */
    public PageDto<ExpenseDto> filterExpensesPage(LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        ProfileEntity profile = profileService.getCurrentProfile();
        Page<ExpenseEntity> page = expenseRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(profile.getId(), startDate, endDate, keyword, pageable);
        BigDecimal total = expenseRepository.sumForSearch(profile.getId(), startDate, endDate, LikeEscape.escape(keyword));
        return new PageDto<>(page.getContent().stream().map(this::toDto).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), total);
    }

    public List<ExpenseDto> filterExpenses(LocalDate startDate, LocalDate endDate, String keyword, Sort sort) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<ExpenseEntity> list = expenseRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(profile.getId(), startDate, endDate, keyword, sort);

        return list.stream()
                .map(this::toDto)
                .toList();
    }

    //////////////////////////////// Notifications methods

    public List<ExpenseDto> getExpensesForUserOnDate(Long profileId, LocalDate date) {
        List<ExpenseEntity> list = expenseRepository.findByProfileIdAndDate(profileId, date);
        return list.stream()
                .map(this::toDto)
                .toList();
    }

    //////////////////////////////// Dashboard information methods

    public List<ExpenseDto> getLatest5ExpensesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<ExpenseEntity> latest5Expenses = expenseRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId());

        return latest5Expenses.stream()
                .map(this::toDto)
                .toList();
    }

    public BigDecimal getTotalExpensesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal totalExpenses = expenseRepository.findTotalExpenseByProfileId(profile.getId());

        return totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
    }

    //////////////////////////////// Helper Methods

    /** The category must belong to the current user and be an expense category. */
    private CategoryEntity findExpenseCategory(Long categoryId, ProfileEntity profile) {
        CategoryEntity category = categoryRepository.findByIdAndProfileId(categoryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        if (!"expense".equalsIgnoreCase(category.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría elegida no es de gastos");
        }
        return category;
    }

    private void validate(ExpenseDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        if (dto.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría es obligatoria");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El importe debe ser mayor que 0");
        }
        if (dto.getDate() != null && dto.getDate().isAfter(LocalDate.now().plusDays(1))) { // +1 day: client may be ahead of server (UTC)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser futura");
        }
    }

    private ExpenseEntity toEntity(ExpenseDto dto, ProfileEntity profile, CategoryEntity category) {
        return ExpenseEntity.builder()
                .name(dto.getName())
                .icon(dto.getIcon())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .profile(profile)
                .category(category)
                .build();
    }

    private ExpenseDto toDto(ExpenseEntity entity) {
        return ExpenseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : "N/A")
                .amount(entity.getAmount())
                .date(entity.getDate())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .recurringId(entity.getRecurringId())
                .build();
    }
}
