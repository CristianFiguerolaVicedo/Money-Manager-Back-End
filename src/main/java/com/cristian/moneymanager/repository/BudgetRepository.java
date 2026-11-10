package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {

    List<BudgetEntity> findByProfileId(Long profileId);

    Optional<BudgetEntity> findByCategoryIdAndProfileId(Long categoryId, Long profileId);

    void deleteByCategoryId(Long categoryId);

    /** Removes all rows of a user (account deletion). */
    void deleteByProfileId(Long profileId);
}
