package com.opportunity.school.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class AttendanceDtos {

    @Data
    public static class SaveAttendanceRequest {
        @NotNull(message = "Class id is required")
        private Long classId;

        @NotNull(message = "Date is required")
        private LocalDate date;

        @NotNull(message = "Entries are required")
        private List<Entry> entries;

        @Data
        public static class Entry {
            @NotNull(message = "Student id is required")
            private Long studentId;

            @NotNull(message = "Status is required")
            private String status; // PRESENT / ABSENT / LATE

            private String note;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentAttendanceRow {
        private Long studentId;
        private String admissionNumber;
        private String studentName;
        private String status;   // PRESENT / ABSENT / LATE / null if not marked
        private String note;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailySummaryDto {
        private Long classId;
        private String className;
        private LocalDate date;
        private long present;
        private long absent;
        private long late;
        private long unmarked;
        private long totalActive;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentReportRow {
        private Long studentId;
        private String admissionNumber;
        private String studentName;
        private long present;
        private long absent;
        private long late;
        private long totalMarked;
        private double percentage;
    }
}
