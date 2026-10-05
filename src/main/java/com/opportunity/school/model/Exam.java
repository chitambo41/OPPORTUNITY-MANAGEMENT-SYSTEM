package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opportunity.school.model.enums.ExamStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    @JsonIgnore
    private SchoolClass schoolClass;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    @JsonIgnore
    private Term term;

    /** Highest possible mark per subject in this exam. */
    @NotNull
    @Column(nullable = false)
    private Integer maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ExamStatus status;

    @Column(length = 255)
    private String notes;
}
