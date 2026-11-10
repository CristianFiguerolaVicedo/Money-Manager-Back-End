package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.dto.IncomeDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.util.MonthParam;
import com.cristian.moneymanager.repository.IncomeRepository;
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

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final com.cristian.moneymanager.repository.AttachmentRepository attachmentRepository;
    private final ProfileService profileService;

    public IncomeDto addIncome(IncomeDto dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        validate(dto);
        CategoryEntity category = findIncomeCategory(dto.getCategoryId(), profile);

        IncomeEntity newIncome = toEntity(dto, profile, category);
        newIncome = incomeRepository.save(newIncome);

        return toDto(newIncome);
    }

    public List<IncomeDto> getCurrentMonthIncomesForCurrentUser() {
        return getIncomesForMonth(YearMonth.now(MonthParam.APP_ZONE));
    }

    /** Incomes of the current user in the given month, newest first. */
    public List<IncomeDto> getIncomesForMonth(YearMonth month) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<IncomeEntity> list = incomeRepository.findByProfileIdAndDateBetweenOrderByDateDescIdDesc(
                profile.getId(), month.atDay(1), month.atEndOfMonth());

        return withAttachmentCounts(list.stream()
                .map(this::toDto)
                .toList());
    }

    public IncomeDto updateIncome(Long id, IncomeDto dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        IncomeEntity entity = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingreso no encontrado"));

        if (!entity.getProfile().getId().equals(profile.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar este ingreso");
        }

        validate(dto);
        CategoryEntity category = findIncomeCategory(dto.getCategoryId(), profile);

        entity.setName(dto.getName().trim());
        entity.setIcon(dto.getIcon());
        entity.setAmount(dto.getAmount());
        if (dto.getDate() != null) {
            entity.setDate(dto.getDate());
        }
        entity.setCategory(category);
        entity.setNote(cleanNote(dto.getNote()));

        return toDto(incomeRepository.save(entity));
    }

    public void deleteIncome(Long id) {
        ProfileEntity profile = profileService.getCurrentProfile();
        IncomeEntity entity = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingreso no encontrado"));

        if (!entity.getProfile().getId().equals(profile.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes borrar este ingreso");
        }

        incomeRepository.delete(entity);
    }

    /** One page of a search, with the total amount of every matching income. */
    public PageDto<IncomeDto> filterIncomesPage(LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        ProfileEntity profile = profileService.getCurrentProfile();
        Page<IncomeEntity> page = incomeRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(profile.getId(), startDate, endDate, keyword, pageable);
        BigDecimal total = incomeRepository.sumForSearch(profile.getId(), startDate, endDate, LikeEscape.escape(keyword));
        return new PageDto<>(withAttachmentCounts(page.getContent().stream().map(this::toDto).toList()),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), total);
    }

    public List<IncomeDto> filterIncomes(LocalDate startDate, LocalDate endDate, String keyword, Sort sort) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<IncomeEntity> list = incomeRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(profile.getId(), startDate, endDate, keyword, sort);

        return list.stream()
                .map(this::toDto)
                .toList();
    }

    //////////////////////////////// Dashboard information methods

    public List<IncomeDto> getLatest5IncomesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<IncomeEntity> latest5Incomes = incomeRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId());

        return latest5Incomes.stream()
                .map(this::toDto)
                .toList();
    }

    public BigDecimal getTotalIncomesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal totalIncomes = incomeRepository.findTotalIncomeByProfileId(profile.getId());

        return totalIncomes != null ? totalIncomes : BigDecimal.ZERO;
    }

    //////////////////////////////// Helper Methods

    /** The category must belong to the current user and be an income category. */
    private CategoryEntity findIncomeCategory(Long categoryId, ProfileEntity profile) {
        CategoryEntity category = categoryRepository.findByIdAndProfileId(categoryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        if (!"income".equalsIgnoreCase(category.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría elegida no es de ingresos");
        }
        return category;
    }

    private void validate(IncomeDto dto) {
        if (dto.getNote() != null && dto.getNote().trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nota no puede pasar de 500 caracteres");
        }
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

    private IncomeEntity toEntity(IncomeDto dto, ProfileEntity profile, CategoryEntity category) {
        return IncomeEntity.builder()
                .note(cleanNote(dto.getNote()))
                .name(dto.getName())
                .icon(dto.getIcon())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .profile(profile)
                .category(category)
                .build();
    }

    private IncomeDto toDto(IncomeEntity entity) {
        return IncomeDto.builder()
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
                .note(entity.getNote())
                .build();
    }

    private static String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** Fills attachmentCount with one query for the whole list. */
    private List<IncomeDto> withAttachmentCounts(List<IncomeDto> list) {
        if (list.isEmpty()) {
            return list;
        }
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : attachmentRepository.countByIncomeIds(list.stream().map(IncomeDto::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        list.forEach(dto -> dto.setAttachmentCount(counts.getOrDefault(dto.getId(), 0L).intValue()));
        return list;
    }
}
