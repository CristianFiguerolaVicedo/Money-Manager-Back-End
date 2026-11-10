package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.GoalContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GoalContributionRepository extends JpaRepository<GoalContributionEntity, Long> {

    List<GoalContributionEntity> findByGoalIdOrderByDateDescIdDesc(Long goalId);

    Optional<GoalContributionEntity> findByIdAndGoalId(Long id, Long goalId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContributionEntity c WHERE c.goal.id = :goalId")
    BigDecimal sumByGoalId(@Param("goalId") Long goalId);

    @Modifying
    @Query("DELETE FROM GoalContributionEntity c WHERE c.goal.id IN (SELECT g.id FROM GoalEntity g WHERE g.profile.id = :profileId)")
    int deleteByProfileId(@Param("profileId") Long profileId);

    @Modifying
    @Query("DELETE FROM GoalContributionEntity c WHERE c.goal.id = :goalId")
    int deleteByGoalId(@Param("goalId") Long goalId);
}
