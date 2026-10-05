package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opportunity.school.model.enums.StudentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Auto-generated, e.g. ON-2026-0001. */
    @Column(nullable = false, unique = true, length = 20)
    private String admissionNumber;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String fullName;

    @Column
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(length = 120)
    private String guardianName;

    @Column(length = 30)
    private String guardianPhone;

    @Column(length = 255)
    private String guardianAddress;

    @Column(length = 190)
    private String guardianEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StudentStatus status;

    @Column(length = 20)
    private String bloodGroup;

    @Column(length = 255)
    private String notes;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<Enrollment> enrollments = new ArrayList<>();
}
