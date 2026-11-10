package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.RecurringDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ExpenseEntity;
import com.cristian.moneymanager.entity.IncomeEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.entity.RecurringEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import com.cristian.moneymanager.repository.RecurringRepository;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Recurring incomes/expenses (F9). A rule creates an ordinary income/expense on each due date:
 * when the user opens the app (catch-up for their rules) and once a day from the scheduled job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringService {

    static final Set<String> TYPES = Set.of("income", "expense");
    static final Set<String> FREQUENCIES = Set.of("weekly", "monthly", "yearly");
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    /** Safety cap: occurrences created for one rule in one go. */
    static final int MAX_OCCURRENCES_PER_RUN = 120;

    private final ProfileService profileService;
    private final RecurringRepository recurringRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------ dates

    /** The k-th occurrence (k = 0 is the start). Months/years are counted from the start, so the 31st stays the 31st when possible. */
    static LocalDate occurrence(LocalDate start, String frequency, long k) {
        return switch (frequency) {
            case "weekly" -> start.plusWeeks(k);
            case "yearly" -> start.plusYears(k);
            default -> start.plusMonths(k);
        };
    }

    /** First occurrence strictly after {@code date}. */
    static LocalDate nextAfter(LocalDate start, String frequency, LocalDate date) {
        if (date.isBefore(start)) {
            return start;
        }
        ChronoUnit unit = switch (frequency) {
            case "weekly" -> ChronoUnit.WEEKS;
            case "yearly" -> ChronoUnit.YEARS;
            default -> ChronoUnit.MONTHS;
        };
        long k = Math.max(0, unit.between(start, date));
        while (!occurrence(start, frequency, k).isAfter(date)) {
            k++;
        }
        while (k > 0 && occurrence(start, frequency, k - 1).isAfter(date)) {
            k--;
        }
        return occurrence(start, frequency, k);
    }

    /** First occurrence on or after {@code date}. */
    static LocalDate nextOnOrAfter(LocalDate start, String frequency, LocalDate date) {
        return nextAfter(start, frequency, date.minusDays(1));
    }

    private static LocalDate today() {
        return LocalDate.now(MonthParam.APP_ZONE);
    }

    // ------------------------------------------------------------------ generation

    /** Creates the due occurrences of the current user's rules (called when they open the app). */
    public int catchUpCurrentUser() {
        Long profileId = profileService.getCurrentProfile().getId();
        int created = 0;
        for (RecurringEntity rule : recurringRepository.findByProfileIdAndActiveTrueAndNextDateLessThanEqual(profileId, today())) {
            created += generateSafely(rule.getId());
        }
        return created;
    }

    /** Creates the due occurrences of every user's rules (scheduled job). */
    public int generateAllDue() {
        int created = 0;
        for (RecurringEntity rule : recurringRepository.findByActiveTrueAndNextDateLessThanEqual(today())) {
            created += generateSafely(rule.getId());
        }
        log.info("Recurring job created {} movements", created);
        return created;
    }

    /** One rule per transaction; if another request is generating the same rule, this one simply backs off. */
    private int generateSafely(Long ruleId) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer created = template.execute(status ->
                    recurringRepository.findById(ruleId).map(rule -> generateDue(rule, today())).orElse(0));
            return created == null ? 0 : created;
        } catch (OptimisticLockingFailureException e) {
            return 0;
        }
    }

    /** Creates every occurrence up to {@code today} and moves nextDate forward (must run inside a transaction). */
    int generateDue(RecurringEntity rule, LocalDate today) {
        int created = 0;
        while (Boolean.TRUE.equals(rule.getActive())
                && rule.getNextDate() != null
                && !rule.getNextDate().isAfter(today)
                && created < MAX_OCCURRENCES_PER_RUN) {
            if (rule.getEndDate() != null && rule.getNextDate().isAfter(rule.getEndDate())) {
                rule.setNextDate(null);
                break;
            }
            createMovement(rule, rule.getNextDate());
            created++;
            rule.setNextDate(nextAfter(rule.getStartDate(), rule.getFrequency(), rule.getNextDate()));
        }
        if (rule.getEndDate() != null && rule.getNextDate() != null && rule.getNextDate().isAfter(rule.getEndDate())) {
            rule.setNextDate(null); // finished
        }
        recurringRepository.save(rule);
        return created;
    }

    private void createMovement(RecurringEntity rule, LocalDate date) {
        if ("income".equals(rule.getType())) {
            incomeRepository.save(IncomeEntity.builder()
                    .name(rule.getName()).icon(rule.getIcon()).amount(rule.getAmount()).date(date)
                    .category(rule.getCategory()).profile(rule.getProfile()).recurringId(rule.getId()).build());
        } else {
            expenseRepository.save(ExpenseEntity.builder()
                    .name(rule.getName()).icon(rule.getIcon()).amount(rule.getAmount()).date(date)
                    .category(rule.getCategory()).profile(rule.getProfile()).recurringId(rule.getId()).build());
        }
    }

    // ------------------------------------------------------------------ CRUD

    public List<RecurringDto> list(String type) {
        Long profileId = profileService.getCurrentProfile().getId();
        return recurringRepository.findByProfileIdOrderByNextDateAscIdAsc(profileId).stream()
                .filter(rule -> type == null || type.isBlank() || rule.getType().equalsIgnoreCase(type))
                .map(RecurringService::toDto)
                .toList();
    }

    /** Creates a rule; if it starts today or earlier, the due movements are created right away. */
    public RecurringDto create(RecurringDto request) {
        if (request == null) {
            throw badRequest("Faltan los datos");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        String type = lower(request.type());
        if (!TYPES.contains(type)) {
            throw badRequest("El tipo debe ser ingreso o gasto");
        }
        String frequency = lower(request.frequency());
        if (!FREQUENCIES.contains(frequency)) {
            throw badRequest("Elige cada cuánto se repite");
        }
        LocalDate start = request.startDate();
        if (start == null) {
            throw badRequest("Elige la fecha de inicio");
        }
        if (start.isBefore(today().minusYears(1))) {
            throw badRequest("La fecha de inicio no puede ser de hace más de un año");
        }
        if (start.isAfter(today().plusYears(5))) {
            throw badRequest("La fecha de inicio está demasiado lejos");
        }

        RecurringEntity rule = RecurringEntity.builder()
                .profile(profile)
                .type(type)
                .name(validName(request.name()))
                .icon(request.icon())
                .amount(validAmount(request.amount()))
                .category(category(request.categoryId(), type, profile.getId()))
                .frequency(frequency)
                .startDate(start)
                .endDate(validEndDate(request.endDate(), start))
                .nextDate(start)
                .active(true)
                .build();
        rule = recurringRepository.save(rule);
        generateSafely(rule.getId());
        return toDto(recurringRepository.findById(rule.getId()).orElseThrow());
    }

    /** Changes name, icon, amount, category, end date or paused/active. Start date and frequency stay fixed. */
    public RecurringDto update(Long id, RecurringDto request) {
        if (request == null) {
            throw badRequest("Faltan los datos");
        }
        ProfileEntity profile = profileService.getCurrentProfile();
        RecurringEntity rule = recurringRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento recurrente no encontrado"));

        if (request.name() != null) {
            rule.setName(validName(request.name()));
        }
        if (request.icon() != null) {
            rule.setIcon(request.icon().isBlank() ? null : request.icon());
        }
        if (request.amount() != null) {
            rule.setAmount(validAmount(request.amount()));
        }
        if (request.categoryId() != null) {
            rule.setCategory(category(request.categoryId(), rule.getType(), profile.getId()));
        }
        // endDate is always sent by the edit form (null = no end)
        rule.setEndDate(validEndDate(request.endDate(), rule.getStartDate()));

        boolean wasActive = Boolean.TRUE.equals(rule.getActive());
        if (request.active() != null) {
            rule.setActive(request.active());
        }
        boolean resumed = !wasActive && Boolean.TRUE.equals(rule.getActive());
        if (resumed || rule.getNextDate() == null) {
            // Never backfill the paused period: continue from today
            LocalDate next = nextOnOrAfter(rule.getStartDate(), rule.getFrequency(), today());
            rule.setNextDate(rule.getEndDate() != null && next.isAfter(rule.getEndDate()) ? null : next);
        } else if (rule.getEndDate() != null && rule.getNextDate().isAfter(rule.getEndDate())) {
            rule.setNextDate(null);
        }
        rule = recurringRepository.save(rule);
        generateSafely(rule.getId());
        return toDto(recurringRepository.findById(rule.getId()).orElseThrow());
    }

    /** Deletes the rule; movements it already created stay (they just lose the link). */
    public void delete(Long id) {
        ProfileEntity profile = profileService.getCurrentProfile();
        RecurringEntity rule = recurringRepository.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento recurrente no encontrado"));
        recurringRepository.delete(rule);
    }

    // ------------------------------------------------------------------ validation

    private CategoryEntity category(Long categoryId, String type, Long profileId) {
        if (categoryId == null) {
            throw badRequest("La categoría es obligatoria");
        }
        CategoryEntity category = categoryRepository.findByIdAndProfileId(categoryId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        if (!type.equalsIgnoreCase(category.getType())) {
            throw badRequest("income".equals(type) ? "La categoría elegida no es de ingresos" : "La categoría elegida no es de gastos");
        }
        return category;
    }

    static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("El nombre es obligatorio");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw badRequest("El nombre es demasiado largo");
        }
        return trimmed;
    }

    static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw badRequest("El importe debe ser mayor que 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw badRequest("El importe es demasiado grande");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    static LocalDate validEndDate(LocalDate end, LocalDate start) {
        if (end != null && end.isBefore(start)) {
            throw badRequest("La fecha de fin no puede ser anterior a la de inicio");
        }
        return end;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static RecurringDto toDto(RecurringEntity rule) {
        return new RecurringDto(rule.getId(), rule.getType(), rule.getName(), rule.getIcon(), rule.getAmount(),
                rule.getCategory() != null ? rule.getCategory().getId() : null,
                rule.getCategory() != null ? rule.getCategory().getName() : null,
                rule.getFrequency(), rule.getStartDate(), rule.getEndDate(), rule.getNextDate(), rule.getActive());
    }
}
