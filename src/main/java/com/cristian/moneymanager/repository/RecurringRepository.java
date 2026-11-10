package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.RecurringEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringRepository extends JpaRepository<RecurringEntity, Long> {

    List<RecurringEntity> findByProfileIdOrderByNextDateAscIdAsc(Long profileId);

    Optional<RecurringEntity> findByIdAndProfileId(Long id, Long profileId);

    List<RecurringEntity> findByActiveTrueAndNextDateLessThanEqual(LocalDate date);

    List<RecurringEntity> findByProfileIdAndActiveTrueAndNextDateLessThanEqual(Long profileId, LocalDate date);

    long countByCategoryId(Long categoryId);

    @Modifying
    @Query("UPDATE RecurringEntity r SET r.category = :target WHERE r.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("target") com.cristian.moneymanager.entity.CategoryEntity target);

    void deleteByProfileId(Long profileId);
}
