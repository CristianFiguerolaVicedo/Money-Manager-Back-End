package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.dto.AttachmentDto;
import com.cristian.moneymanager.entity.AttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {

    @Query("SELECT new com.cristian.moneymanager.dto.AttachmentDto(a.id, a.filename, a.contentType, a.sizeBytes, a.createdAt) "
            + "FROM AttachmentEntity a WHERE a.incomeId = :incomeId ORDER BY a.id")
    List<AttachmentDto> listForIncome(@Param("incomeId") Long incomeId);

    @Query("SELECT new com.cristian.moneymanager.dto.AttachmentDto(a.id, a.filename, a.contentType, a.sizeBytes, a.createdAt) "
            + "FROM AttachmentEntity a WHERE a.expenseId = :expenseId ORDER BY a.id")
    List<AttachmentDto> listForExpense(@Param("expenseId") Long expenseId);

    long countByIncomeId(Long incomeId);

    long countByExpenseId(Long expenseId);

    /** [incomeId, count] for the given incomes (one query for a whole list). */
    @Query("SELECT a.incomeId, COUNT(a) FROM AttachmentEntity a WHERE a.incomeId IN :ids GROUP BY a.incomeId")
    List<Object[]> countByIncomeIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT a.expenseId, COUNT(a) FROM AttachmentEntity a WHERE a.expenseId IN :ids GROUP BY a.expenseId")
    List<Object[]> countByExpenseIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM AttachmentEntity a WHERE a.profile.id = :profileId")
    long totalBytesForProfile(@Param("profileId") Long profileId);

    Optional<AttachmentEntity> findByIdAndProfileId(Long id, Long profileId);

    @Modifying
    @Query("DELETE FROM AttachmentEntity a WHERE a.profile.id = :profileId")
    int deleteByProfileId(@Param("profileId") Long profileId);

    @Modifying
    @Query("DELETE FROM AttachmentEntity a WHERE a.incomeId = :incomeId")
    int deleteByIncomeId(@Param("incomeId") Long incomeId);

    @Modifying
    @Query("DELETE FROM AttachmentEntity a WHERE a.expenseId = :expenseId")
    int deleteByExpenseId(@Param("expenseId") Long expenseId);
}
