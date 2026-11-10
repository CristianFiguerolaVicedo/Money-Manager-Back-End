package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.CategoryDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.exception.CategoryInUseException;
import com.cristian.moneymanager.repository.BudgetRepository;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.RecurringRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringRepository recurringRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final ProfileService profileService;

    public CategoryDto saveCategory(CategoryDto categoryDto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        String name = requireName(categoryDto.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndProfileId(name, profile.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con este nombre");
        }

        categoryDto.setName(name);
        categoryDto.setType(normalizeType(categoryDto.getType()));
        CategoryEntity newCategory = toEntity(categoryDto, profile);
        newCategory = categoryRepository.save(newCategory);
        return toDto(newCategory);
    }

    public List<CategoryDto> getCategoriesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<CategoryEntity> categories = categoryRepository.findByProfileId(profile.getId());
        return categories.stream()
                .map(this::toDto)
                .toList();
    }

    public List<CategoryDto> getCategoriesByTypeForCurrentUser(String type) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<CategoryEntity> entities = categoryRepository.findByTypeIgnoreCaseAndProfileId(type, profile.getId());
        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    public CategoryDto updateCategory(Long id, CategoryDto dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity existingCategory = categoryRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        String name = requireName(dto.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndProfileIdAndIdNot(name, profile.getId(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con este nombre");
        }

        // The type is intentionally not editable: incomes/expenses already linked to it would become inconsistent
        existingCategory.setName(name);
        existingCategory.setIcon(dto.getIcon());

        existingCategory = categoryRepository.save(existingCategory);
        return toDto(existingCategory);
    }

    /**
     * Deletes one of the current user's categories. If it still has incomes/expenses they are
     * moved to {@code reassignToId} first (same owner and type); without it, 409 with the count.
     */
    @Transactional
    public void deleteCategory(Long id, Long reassignToId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        boolean isIncome = "income".equalsIgnoreCase(category.getType());
        long count = (isIncome ? incomeRepository.countByCategoryId(id) : expenseRepository.countByCategoryId(id))
                + recurringRepository.countByCategoryId(id);

        if (count > 0) {
            if (reassignToId == null) {
                throw new CategoryInUseException(count);
            }
            if (reassignToId.equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige una categoría distinta a la que vas a borrar");
            }
            CategoryEntity target = categoryRepository.findByIdAndProfileId(reassignToId, profile.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoría de destino no existe"));
            if (!category.getType().equalsIgnoreCase(target.getType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría de destino debe ser del mismo tipo");
            }
            recurringRepository.reassignCategory(id, target);
            if (isIncome) {
                incomeRepository.reassignCategory(id, target);
            } else {
                expenseRepository.reassignCategory(id, target);
            }
        }

        budgetRepository.deleteByCategoryId(id);
        categoryRepository.delete(category);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la categoría es obligatorio");
        }
        return name.trim();
    }

    private String normalizeType(String type) {
        if (type == null || !(type.equalsIgnoreCase("income") || type.equalsIgnoreCase("expense"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de categoría debe ser 'income' o 'expense'");
        }
        return type.toLowerCase();
    }

    private CategoryEntity toEntity(CategoryDto categoryDto, ProfileEntity profile) {
        return CategoryEntity.builder()
                .name(categoryDto.getName())
                .type(categoryDto.getType())
                .icon(categoryDto.getIcon())
                .profile(profile)
                .build();
    }

    private CategoryDto toDto(CategoryEntity categoryEntity) {
        return CategoryDto.builder()
                .id(categoryEntity.getId())
                .name(categoryEntity.getName())
                .profileId(categoryEntity.getProfile() != null ? categoryEntity.getProfile().getId() : null)
                .icon(categoryEntity.getIcon())
                .createdAt(categoryEntity.getCreatedAt())
                .updatedAt(categoryEntity.getUpdatedAt())
                .type(categoryEntity.getType())
                .build();
    }
}
