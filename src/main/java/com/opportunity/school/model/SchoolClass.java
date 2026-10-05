package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opportunity.school.model.enums.ClassLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "school_classes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"level", "academic_year_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassLevel level;

    /** Display name, e.g. "KG2 - Eagle" */
    @NotBlank
    @Column(nullable = false, length = 60)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    @JsonIgnore
    private AcademicYear academicYear;

    /** The one class teacher for this class (nullable until assigned). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_teacher_id")
    @JsonIgnore
    private Teacher classTeacher;

    @Builder.Default
    @OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY)
    private List<ClassSubject> classSubjects = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY)
    private List<Enrollment> enrollments = new ArrayList<>();
}
