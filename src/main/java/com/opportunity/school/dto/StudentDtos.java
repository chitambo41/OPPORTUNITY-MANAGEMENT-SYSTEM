package com.opportunity.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class StudentDtos {

    @Data
    public static class CreateStudentRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        private LocalDate dateOfBirth;

        private String gender;

        private String guardianName;

        private String guardianPhone;

        private String guardianAddress;

        private String guardianEmail;

        @NotNull(message = "Class id is required")
        private Long classId;

        private String bloodGroup;

        private String notes;
    }

    @Data
    public static class UpdateStudentRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        private LocalDate dateOfBirth;

        private String gender;

        private String guardianName;

        private String guardianPhone;

        private String guardianAddress;

        private String guardianEmail;

        private String bloodGroup;

        private String notes;
    }

    @Data
    public static class MoveStudentRequest {
        @NotNull(message = "Target class id is required")
        private Long targetClassId;
    }

    @Data
    public static class RemoveStudentRequest {
        @NotBlank(message = "Removal reason is required")
        private String reason; // SHIFT, DIED, COMPLETE

        @NotBlank(message = "Note is required")
        private String note;

        @NotNull(message = "Removal date is required")
        private LocalDate removalDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentDto {
        private Long id;
        private String admissionNumber;
        private String fullName;
        private LocalDate dateOfBirth;
        private String gender;
        private String guardianName;
        private String guardianPhone;
        private String guardianAddress;
        private String guardianEmail;
        private String status;
        private String bloodGroup;
        private String notes;
        // convenience fields for list rows
        private Long currentClassId;
        private String currentClassName;
        private String currentLevel;
        private Integer currentYear;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnrollmentDto {
        private Long id;
        private Long classId;
        private String className;
        private String level;
        private Integer year;
        private String status;
        private LocalDate joinedOn;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AttendanceSummaryDto {
        private long present;
        private long absent;
        private long late;
        private double percentage;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeStatusFlat {
        private Long termId;
        private String termNumber;
        private Integer year;
        private Double expected;
        private Double paid;
        private Double balance;
        private String status; // PAID / PARTIAL / NOT_PAID
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentProfileDto {
        private StudentDto student;
        private List<EnrollmentDto> classHistory;
        private AttendanceSummaryDto attendance;
        private List<FeeStatusFlat> fees;
    }
}
