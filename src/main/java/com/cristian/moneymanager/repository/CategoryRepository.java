package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByProfileId(Long profileId);
    Optional<CategoryEntity> findByIdAndProfileId(Long id, Long profileId);
    List<CategoryEntity> findByTypeIgnoreCaseAndProfileId(String type, Long profileId);
    boolean existsByNameIgnoreCaseAndProfileId(String name, Long profileId);
    boolean existsByNameIgnoreCaseAndProfileIdAndIdNot(String name, Long profileId, Long id);
}
