package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.JobRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {

    boolean existsByJobNameAndRunDate(String jobName, LocalDate runDate);
}
