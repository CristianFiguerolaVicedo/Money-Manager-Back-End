package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.IncomeEntity;
import org.springframework.data.domain.Sort;
import com.cristian.moneymanager.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<IncomeEntity, Long> {

    List<IncomeEntity> findByProfileIdOrderByDateDesc(Long profileId);
    List<IncomeEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(i.amount) FROM IncomeEntity i WHERE i.profile.id = :profileId")
    BigDecimal findTotalIncomeByProfileId(@Param("profileId") Long profileId);

    List<IncomeEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(Long profileId, LocalDate startDate, LocalDate endDate, String keyword, Sort sort);
    List<IncomeEntity> findByProfileIdAndDateBetween(Long profileId, LocalDate startDate, LocalDate endDate);
    List<IncomeEntity> findByProfileIdAndDateBetweenOrderByDateDescIdDesc(Long profileId, LocalDate startDate, LocalDate endDate);

    long countByCategoryId(Long categoryId);

    /** Moves every income of one category to another (used before deleting a category). */
    @Modifying
    @Query("UPDATE IncomeEntity t SET t.category = :target WHERE t.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("target") CategoryEntity target);
}
