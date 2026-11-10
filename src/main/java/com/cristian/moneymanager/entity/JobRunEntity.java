package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row per scheduled email job and day: the unique key stops a retried trigger from sending twice. */
@Entity
@Table(name = "tbl_job_runs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String jobName;

    @Column(nullable = false)
    private LocalDate runDate;

    private LocalDateTime startedAt;

    private Integer emailsSent;
}
