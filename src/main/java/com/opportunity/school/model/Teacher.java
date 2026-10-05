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
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(length = 190)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 20, unique = true)
    private String staffNumber;

    @Column
    private LocalDate joinDate;

    /** Login account for this teacher (optional; admins may not have one). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "classTeacher", fetch = FetchType.LAZY)
    private List<SchoolClass> classesLed = new ArrayList<>();
}
