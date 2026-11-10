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

/** A rule that adds the same income or expense every week, month or year (F9). */
@Entity
@Table(name = "tbl_recurring")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecurringEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    /** "income" or "expense". */
    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(nullable = false)
    private BigDecimal amount;

    /** "weekly", "monthly" or "yearly". */
    @Column(nullable = false, length = 10)
    private String frequency;

    /** First occurrence; it also fixes the weekday / day of month repeated afterwards. */
    @Column(nullable = false)
    private LocalDate startDate;

    /** Last day an occurrence may fall on (null = no end). */
    private LocalDate endDate;

    /** Next occurrence still to be created (null when the rule has finished). */
    private LocalDate nextDate;

    @Column(nullable = false)
    private Boolean active;

    /** Optimistic lock: two requests can never create the same occurrence twice. */
    @Version
    private Long version;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
