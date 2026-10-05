package com.opportunity.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class TeacherDtos {

    @Data
    public static class CreateTeacherRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Phone is required")
        private String phone;

        private String address;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        /** Optional custom password; defaults to Teacher@2026. */
        private String password;

        private LocalDate joinDate;
    }

    @Data
    public static class UpdateTeacherRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        private String phone;

        private String address;

        private LocalDate joinDate;
    }

    @Data
    public static class RemoveTeacherRequest {
        @NotBlank(message = "Removal reason is required")
        private String reason; // FIRED expected; SHIFT/DIED/COMPLETE accepted too

        @NotBlank(message = "Note is required")
        private String note;

        @NotNull(message = "Removal date is required")
        private LocalDate removalDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeacherDto {
        private Long id;
        private String fullName;
        private String phone;
        private String address;
        private String email;
        private boolean active;
        private String staffNumber;
        private LocalDate joinDate;
        private List<Long> classIds;
    }
}
