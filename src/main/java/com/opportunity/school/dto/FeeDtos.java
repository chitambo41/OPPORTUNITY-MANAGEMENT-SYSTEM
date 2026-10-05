package com.opportunity.school.dto;

import com.opportunity.school.dto.StudentDtos.StudentDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FeeDtos {

    @Data
    public static class SetFeeRequest {
        @NotNull(message = "Term id is required")
        private Long termId;

        /** Null = apply to all classes (general fee). */
        private Long classId;

        /** Null classId sets the general fee; requires allClasses=true. */
        private Boolean allClasses;

        @NotNull(message = "Amount is required")
        private BigDecimal amount;
    }

    @Data
    public static class RecordPaymentRequest {
        @NotNull(message = "Student id is required")
        private Long studentId;

        @NotNull(message = "Term id is required")
        private Long termId;

        @NotNull(message = "Amount is required")
        private BigDecimal amount;

        @NotNull(message = "Payment date is required")
        private LocalDate paymentDate;

        @NotBlank(message = "Payment method is required")
        private String method; // CASH / BANK / MOBILE_MONEY

        private String note;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeStructureDto {
        private Long id;
        private Long termId;
        private Integer year;
        private String termNumber;
        private Long classId;
        private String className;
        private boolean general;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentDto {
        private Long id;
        private String receiptNumber;
        private Long studentId;
        private String studentName;
        private String admissionNumber;
        private Long termId;
        private String termNumber;
        private Integer year;
        private BigDecimal amount;
        private LocalDate paymentDate;
        private String method;
        private String note;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentFeeStatusDto {
        private Long studentId;
        private String admissionNumber;
        private String studentName;
        private String className;
        private Long termId;
        private String termNumber;
        private BigDecimal expected;
        private BigDecimal paid;
        private BigDecimal balance;
        private String status; // PAID / PARTIAL / NOT_PAID
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeSummaryDto {
        private BigDecimal expected;
        private BigDecimal collected;
        private BigDecimal outstanding;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeStatementDto {
        private StudentDto student;
        private List<Line> lines;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Line {
            private Long termId;
            private String termNumber;
            private Integer year;
            private BigDecimal expected;
            private BigDecimal paid;
            private BigDecimal balance;
            private String status;
            private List<PaymentDto> payments;
        }
    }
}
