package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A savings goal (F14). What has been saved is the sum of its contributions. */
@Entity
@Table(name = "tbl_goals")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Column(nullable = false, length = 100)
    private String name;

    private String icon;

    @Column(nullable = false)
    private BigDecimal targetAmount;

    /** Optional date the user wants to reach the target by. */
    private LocalDate deadline;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
