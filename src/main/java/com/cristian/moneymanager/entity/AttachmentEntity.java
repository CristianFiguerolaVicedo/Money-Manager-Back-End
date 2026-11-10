package com.cristian.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A receipt/invoice attached to one income or one expense (F15). Bytes live in the database (bytea). */
@Entity
@Table(name = "tbl_attachments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    private Long incomeId;

    private Long expenseId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Integer sizeBytes;

    /** Only loaded when downloading (list queries use a projection without it). */
    @Column(nullable = false)
    private byte[] data;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
