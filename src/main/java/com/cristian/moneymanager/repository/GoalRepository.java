package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.GoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<GoalEntity, Long> {

    List<GoalEntity> findByProfileIdOrderByCreatedAtAsc(Long profileId);

    Optional<GoalEntity> findByIdAndProfileId(Long id, Long profileId);

    long countByProfileId(Long profileId);

    void deleteByProfileId(Long profileId);
}
