package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Money put into a goal (positive) or taken out of it (negative). */
@Entity
@Table(name = "tbl_goal_contributions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GoalContributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private GoalEntity goal;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    private String note;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
