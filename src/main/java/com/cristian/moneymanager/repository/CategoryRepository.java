package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByProfileId(Long profileId);
    Optional<CategoryEntity> findByIdAndProfileId(Long id, Long profileId);
    List<CategoryEntity> findByTypeIgnoreCaseAndProfileId(String type, Long profileId);
    boolean existsByNameIgnoreCaseAndProfileId(String name, Long profileId);
    boolean existsByNameIgnoreCaseAndProfileIdAndIdNot(String name, Long profileId, Long id);

    /** Removes all rows of a user (account deletion). */
    void deleteByProfileId(Long profileId);

    List<CategoryEntity> findByParentIdAndProfileId(Long parentId, Long profileId);

    boolean existsByParentId(Long parentId);

    /** Before deleting a category, its subcategories become main categories. */
    @Modifying
    @Query("UPDATE CategoryEntity c SET c.parentId = NULL WHERE c.parentId = :parentId")
    void detachChildren(@Param("parentId") Long parentId);
}
