package com.cristian.moneymanager.repository;

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

    List<ExpenseEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(Long profileId, LocalDate startDate, LocalDate endDate, String keyword, Sort sort);
    Page<ExpenseEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(Long profileId, LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable);

    /** Total of a search; keyword must already have %, _ and \\ escaped (see LikeEscape). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ExpenseEntity t WHERE t.profile.id = :profileId AND t.date BETWEEN :startDate AND :endDate "
            + "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'")
    BigDecimal sumForSearch(@Param("profileId") Long profileId, @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate, @Param("keyword") String keyword);
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
