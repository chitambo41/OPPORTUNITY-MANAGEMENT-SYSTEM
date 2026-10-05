package com.opportunity.school.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class YearDtos {

    @Data
    public static class CreateYearRequest {
        @NotNull(message = "Year is required")
        @Min(value = 2000, message = "Year must be >= 2000")
        @Max(value = 2100, message = "Year must be <= 2100")
        private Integer year;

        @NotNull(message = "Term 1 start date is required")
        private LocalDate term1Start;

        @NotNull(message = "Term 1 end date is required")
        private LocalDate term1End;

        @NotNull(message = "Term 2 start date is required")
        private LocalDate term2Start;

        @NotNull(message = "Term 2 end date is required")
        private LocalDate term2End;

        /** When true, runs yearly promotion after creating the year. */
        private Boolean promote;
    }

    @Data
    public static class SetCurrentRequest {
        @NotNull(message = "Academic year id is required")
        private Long academicYearId;

        /** TERM_1 or TERM_2; optional if the year already has a current term. */
        private String termNumber;
    }

    @Data
    public static class TermUpdateRequest {
        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TermDto {
        private Long id;
        private String number;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean current;
        private Integer year;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class YearDto {
        private Long id;
        private Integer year;
        private boolean current;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<TermDto> terms;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CurrentContextDto {
        private Long academicYearId;
        private Integer year;
        private Long termId;
        private String termNumber;
        private LocalDate termStart;
        private LocalDate termEnd;
    }
}
