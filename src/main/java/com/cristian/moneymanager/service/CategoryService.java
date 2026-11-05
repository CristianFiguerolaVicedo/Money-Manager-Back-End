package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.CategoryDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProfileService profileService;

    public CategoryDto saveCategory(CategoryDto categoryDto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        String name = requireName(categoryDto.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndProfileId(name, profile.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found or not accessible"));

        String name = requireName(dto.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndProfileIdAndIdNot(name, profile.getId(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists");
        }

        // The type is intentionally not editable: incomes/expenses already linked to it would become inconsistent
        existingCategory.setName(name);
        existingCategory.setIcon(dto.getIcon());

        existingCategory = categoryRepository.save(existingCategory);
        return toDto(existingCategory);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name is required");
        }
        return name.trim();
    }

    private String normalizeType(String type) {
        if (type == null || !(type.equalsIgnoreCase("income") || type.equalsIgnoreCase("expense"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category type must be 'income' or 'expense'");
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
