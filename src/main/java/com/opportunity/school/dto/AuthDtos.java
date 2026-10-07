package com.opportunity.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private Long userId;
        private String email;
        private String role;
        private Long teacherId;
        private String teacherName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherRegistrationRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Phone is required")
        private String phone;

        private String address;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        private String password;

        @NotBlank(message = "Password confirmation is required")
        private String confirmPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank(message = "Reset token is required")
        private String token;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        private String password;

        @NotBlank(message = "Password confirmation is required")
        private String confirmPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeCredentialsRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @Email(message = "Email must be valid")
        private String newEmail;

        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        private String newPassword;

        private String confirmPassword;
    }
}
