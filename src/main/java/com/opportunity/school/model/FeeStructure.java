package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "fee_structures",
        uniqueConstraints = @UniqueConstraint(columnNames = {"term_id", "school_class_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    @JsonIgnore
    private Term term;

    /** Null = applies to all classes as a default. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    @JsonIgnore
    private SchoolClass schoolClass;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
