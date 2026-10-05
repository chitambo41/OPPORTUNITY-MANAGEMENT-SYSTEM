package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opportunity.school.model.enums.RemovalReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit record for every removal (student or teacher). Removal itself is a soft delete
 * on the target entity; this log preserves who/when/why.
 */
@Entity
@Table(name = "removal_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemovalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** STUDENT or TEACHER. */
    @Column(nullable = false, length = 15)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    /** Human readable snapshot of the removed record's name. */
    @Column(nullable = false, length = 150)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RemovalReason reason;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDate removalDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_id")
    @JsonIgnore
    private User removedBy;
}
