package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "academic_years", uniqueConstraints = @UniqueConstraint(columnNames = "year"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Calendar year, e.g. 2026. */
    @Column(name = "year", nullable = false, unique = true)
    private Integer year;

    @Column(nullable = false)
    private boolean current = false;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "academicYear", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("number ASC")
    private List<Term> terms = new ArrayList<>();
}
