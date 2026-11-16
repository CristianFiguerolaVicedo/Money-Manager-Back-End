package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.dto.SearchCriteria;
import com.cristian.moneymanager.entity.ExpenseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.cristian.moneymanager.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    List<ExpenseEntity> findByProfileIdOrderByDateDesc(Long profileId);
    List<ExpenseEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(e.amount) FROM ExpenseEntity e WHERE e.profile.id = :profileId")
    BigDecimal findTotalExpenseByProfileId(@Param("profileId") Long profileId);

    /** Search conditions shared by the list, the page and the total (see SearchCriteria). */
    String SEARCH_WHERE = "WHERE t.profile.id = :#{#c.profileId()} AND t.date BETWEEN :#{#c.startDate()} AND :#{#c.endDate()} "
            + "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :#{#c.keyword()}, '%')) ESCAPE '\\' "
            + "AND (:#{#c.anyCategory()} = true OR t.category.id IN :#{#c.categoryIdsForQuery()}) "
            + "AND t.amount >= :#{#c.minAmount()} AND t.amount <= :#{#c.maxAmount()}";

    @Query("SELECT t FROM ExpenseEntity t " + SEARCH_WHERE)
    List<ExpenseEntity> search(@Param("c") SearchCriteria c, Sort sort);

    @Query(value = "SELECT t FROM ExpenseEntity t " + SEARCH_WHERE, countQuery = "SELECT COUNT(t) FROM ExpenseEntity t " + SEARCH_WHERE)
    Page<ExpenseEntity> search(@Param("c") SearchCriteria c, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ExpenseEntity t " + SEARCH_WHERE)
    BigDecimal sumForSearch(@Param("c") SearchCriteria c);
    List<ExpenseEntity> findByProfileIdAndDateBetween(Long profileId, LocalDate startDate, LocalDate endDate);
    List<ExpenseEntity> findByProfileIdAndDateBetweenOrderByDateDescIdDesc(Long profileId, LocalDate startDate, LocalDate endDate);
    List<ExpenseEntity> findByProfileIdAndDate(Long profileId, LocalDate date);
    boolean existsByProfileIdAndDate(Long profileId, LocalDate date);


    long countByCategoryId(Long categoryId);

    /** Moves every expense of one category to another (used before deleting a category). */
    @Modifying
    @Query("UPDATE ExpenseEntity t SET t.category = :target WHERE t.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("target") CategoryEntity target);

    /** Removes all rows of a user (account deletion). */
    void deleteByProfileId(Long profileId);
}
