package com.opportunity.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDtos {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdminDashboardDto {
        private long activeStudents;
        private long activeTeachers;
        private long resultsPendingReview;
        private BigDecimal feesCollected;
        private BigDecimal feesOutstanding;
        private List<ClassCount> studentsPerClass;
        private List<ClassAttendance> todayAttendance;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ClassCount {
            private Long classId;
            private String className;
            private String level;
            private long count;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ClassAttendance {
            private Long classId;
            private String className;
            private long present;
            private long absent;
            private long late;
            private long unmarked;
            private long totalActive;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeacherDashboardDto {
        private List<ClassInfo> myClasses;
        private Long classTeacherOfId;      // null when not a class teacher
        private String classTeacherOfName;
        private String attendanceStatusToday; // MARKED / PARTIAL / NOT_MARKED / NOT_A_CLASS_TEACHER
        private List<PendingMarks> pendingMarks;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ClassInfo {
            private Long classId;
            private String className;
            private String level;
            private String role; // CLASS_TEACHER / SUBJECT_TEACHER
            private List<String> subjects;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class PendingMarks {
            private Long examId;
            private String examName;
            private String className;
            private Long subjectId;
            private String subjectName;
            private String status;
            private int studentsWithMarks;
            private int activeStudents;
            private int missing;
        }
    }
}
