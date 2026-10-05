package com.opportunity.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ExamDtos {

    @Data
    public static class CreateExamRequest {
        @NotBlank(message = "Exam name is required")
        private String name;

        @NotNull(message = "Class id is required")
        private Long classId;

        @NotNull(message = "Term id is required")
        private Long termId;

        @NotNull(message = "Max marks is required")
        @Min(value = 1, message = "Max marks must be at least 1")
        @Max(value = 1000, message = "Max marks must be at most 1000")
        private Integer maxMarks;

        private String notes;
    }

    @Data
    public static class UpdateExamStatusRequest {
        @NotNull(message = "Status is required")
        private String status; // DRAFT / OPEN / SUBMITTED / APPROVED / RETURNED

        private String comment;
    }

    @Data
    public static class SaveMarksRequest {
        @NotNull(message = "Exam id is required")
        private Long examId;

        @NotNull(message = "Subject id is required")
        private Long subjectId;

        @NotNull(message = "Marks are required")
        private List<MarkEntry> marks;

        @Data
        public static class MarkEntry {
            @NotNull(message = "Student id is required")
            private Long studentId;

            @NotNull(message = "Score is required")
            @Min(value = 0, message = "Score must be >= 0")
            @Max(value = 1000, message = "Score must be <= maxMarks")
            private Integer score;

            private String comment;
        }
    }

    @Data
    public static class SendResultsRequest {
        private String teacherComment;
    }

    @Data
    public static class ReturnResultsRequest {
        @NotBlank(message = "A comment is required when returning results")
        private String comment;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExamDto {
        private Long id;
        private String name;
        private Long classId;
        private String className;
        private String level;
        private Long termId;
        private String termNumber;
        private Integer year;
        private Integer maxMarks;
        private String status;
        private String notes;
        private Long submissionId;
        private String submissionStatus;
        private String adminComment;
        private String teacherComment;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MarkCell {
        private Long studentId;
        private Long subjectId;
        private Integer score;
        private String comment;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResultsTableDto {
        private Long examId;
        private List<Long> studentIds;
        private List<String> studentNames;
        private List<String> admissionNumbers;
        private List<Long> subjectIds;
        private List<String> subjectNames;
        private List<Integer> maxMarks; // per subject (same value repeated)
        private List<MarkCell> cells;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentProgressDto {
        private Long examId;
        private Long subjectId;
        private String subjectName;
        private int studentsWithMarks;
        private int activeStudents;
        private int missing;
        private boolean complete;
    }
}
