package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.FilterDto;
import com.cristian.moneymanager.dto.PageDto;
import com.cristian.moneymanager.dto.SearchCriteria;
import com.cristian.moneymanager.dto.SearchItemDto;
import com.cristian.moneymanager.entity.CategoryEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.CategoryRepository;
import com.cristian.moneymanager.service.ExpenseService;
import com.cristian.moneymanager.service.IncomeService;
import com.cristian.moneymanager.service.ProfileService;
import com.cristian.moneymanager.util.LikeEscape;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/filter")
public class FilterController {

    private static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);
    private static final BigDecimal NO_MAX_AMOUNT = new BigDecimal("1000000000000");
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    // Whitelist of sortable fields (request value -> entity property)
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "date", "date",
            "amount", "amount",
            "name", "name",
            "category", "category.name"
    );

    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final ProfileService profileService;
    private final CategoryRepository categoryRepository;

    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> filterTransactions(@RequestBody FilterDto filterDto) {
        String type = filterDto.getType() == null ? "" : filterDto.getType().toLowerCase();
        if (!List.of("income", "expense", "all").contains(type)) {
            throw badRequest("Tipo no válido, debe ser 'income', 'expense' o 'all'");
        }
        SearchCriteria criteria = criteria(filterDto);

        String sortKey = filterDto.getSortField() != null ? filterDto.getSortField().toLowerCase() : "date";
        String sortField = SORT_FIELDS.getOrDefault(sortKey, "date");
        Sort.Direction direction = "desc".equalsIgnoreCase(filterDto.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortField).and(Sort.by(direction, "id"));

        if (filterDto.getPage() == null) {
            // Whole list (older clients)
            return switch (type) {
                case "income" -> ResponseEntity.ok(incomeService.filterIncomes(criteria, sort));
                case "expense" -> ResponseEntity.ok(expenseService.filterExpenses(criteria, sort));
                default -> ResponseEntity.ok(mergeAll(criteria, sort, sortKey, direction));
            };
        }

        int page = Math.max(0, filterDto.getPage());
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, filterDto.getSize() != null ? filterDto.getSize() : DEFAULT_PAGE_SIZE));
        return switch (type) {
            case "income" -> ResponseEntity.ok(incomeService.filterIncomesPage(criteria, PageRequest.of(page, size, sort)));
            case "expense" -> ResponseEntity.ok(expenseService.filterExpensesPage(criteria, PageRequest.of(page, size, sort)));
            default -> ResponseEntity.ok(pageOfAll(criteria, sort, sortKey, direction, page, size));
        };
    }

    /** Validates the request and turns it into search conditions for the current user. */
    private SearchCriteria criteria(FilterDto filterDto) {
        ProfileEntity profile = profileService.getCurrentProfile();

        // LocalDate.MIN is out of range for PostgreSQL, so use a sane lower bound instead
        LocalDate startDate = filterDto.getStartDate() != null ? filterDto.getStartDate() : EARLIEST_DATE;
        // Allow one extra day because the client's local date may be ahead of the server (UTC)
        LocalDate endDate = filterDto.getEndDate() != null ? filterDto.getEndDate() : LocalDate.now().plusDays(1);
        if (startDate.isAfter(endDate)) {
            throw badRequest("La fecha de inicio debe ser anterior a la de fin");
        }

        BigDecimal min = filterDto.getMinAmount() != null ? filterDto.getMinAmount() : BigDecimal.ZERO;
        BigDecimal max = filterDto.getMaxAmount() != null ? filterDto.getMaxAmount() : NO_MAX_AMOUNT;
        if (min.signum() < 0 || max.signum() < 0) {
            throw badRequest("Los importes del filtro no pueden ser negativos");
        }
        if (min.compareTo(max) > 0) {
            throw badRequest("El importe mínimo no puede ser mayor que el máximo");
        }

        List<Long> categoryIds = List.of();
        if (filterDto.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findByIdAndProfileId(filterDto.getCategoryId(), profile.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
            // A category includes its subcategories
            categoryIds = Stream.concat(Stream.of(category.getId()),
                    categoryRepository.findByParentIdAndProfileId(category.getId(), profile.getId()).stream().map(CategoryEntity::getId)).toList();
        }

        String keyword = filterDto.getKeyword() != null ? filterDto.getKeyword().trim() : "";
        return new SearchCriteria(profile.getId(), startDate, endDate, LikeEscape.escape(keyword), categoryIds, min, max);
    }

    /** "Todos": incomes and expenses merged in the requested order. Totals are income − expenses. */
    private List<SearchItemDto> mergeAll(SearchCriteria criteria, Sort sort, String sortKey, Sort.Direction direction) {
        List<SearchItemDto> items = new ArrayList<>();
        incomeService.filterIncomes(criteria, sort).forEach(i -> items.add(SearchItemDto.of(i)));
        expenseService.filterExpenses(criteria, sort).forEach(e -> items.add(SearchItemDto.of(e)));
        Comparator<SearchItemDto> order = switch (sortKey) {
            case "amount" -> Comparator.comparing(SearchItemDto::amount);
            case "name" -> Comparator.comparing(i -> i.name().toLowerCase());
            case "category" -> Comparator.comparing(i -> i.categoryName() == null ? "" : i.categoryName().toLowerCase());
            default -> Comparator.comparing(SearchItemDto::date);
        };
        order = order.thenComparing(SearchItemDto::id);
        items.sort(direction == Sort.Direction.DESC ? order.reversed() : order);
        return items;
    }

    private PageDto<SearchItemDto> pageOfAll(SearchCriteria criteria, Sort sort, String sortKey, Sort.Direction direction, int page, int size) {
        List<SearchItemDto> all = mergeAll(criteria, sort, sortKey, direction);
        int from = Math.min(all.size(), page * size);
        int to = Math.min(all.size(), from + size);
        BigDecimal balance = all.stream()
                .map(i -> "income".equals(i.type()) ? i.amount() : i.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalPages = (int) Math.ceil(all.size() / (double) size);
        return new PageDto<>(all.subList(from, to), page, size, all.size(), totalPages, balance);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
