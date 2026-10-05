package com.opportunity.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class SubjectClassDtos {

    // ---------- Subjects ----------

    @Data
    public static class SubjectRequest {
        @NotBlank(message = "Subject name is required")
        private String name;

        private String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubjectDto {
        private Long id;
        private String name;
        private String description;
    }

    // ---------- Classes ----------

    @Data
    public static class CreateClassRequest {
        @NotNull(message = "Level is required")
        private String level; // BABY_CLASS, KG1, KG2, KG3

        @NotBlank(message = "Class name is required")
        private String name;

        @NotNull(message = "Academic year id is required")
        private Long academicYearId;
    }

    @Data
    public static class AssignClassTeacherRequest {
        @NotNull(message = "Teacher id is required")
        private Long teacherId;
    }

    @Data
    public static class AddClassSubjectRequest {
        @NotNull(message = "Subject id is required")
        private Long subjectId;

        @NotNull(message = "Teacher id is required")
        private Long teacherId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClassSubjectDto {
        private Long id;
        private Long subjectId;
        private String subjectName;
        private Long teacherId;
        private String teacherName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClassDto {
        private Long id;
        private String level;
        private String name;
        private Long academicYearId;
        private Integer year;
        private Long classTeacherId;
        private String classTeacherName;
        private Long activeStudentCount;
        private List<ClassSubjectDto> subjects;
    }
}
