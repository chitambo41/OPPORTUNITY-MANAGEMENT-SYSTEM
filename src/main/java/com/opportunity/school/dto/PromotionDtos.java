package com.opportunity.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class PromotionDtos {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromotionLine {
        private Long studentId;
        private String admissionNumber;
        private String studentName;
        private String fromLevel;
        private String fromClass;
        private String toLevel;
        private String toClass;
        private String action; // PROMOTED / COMPLETE / SKIPPED_ALREADY_PLACED / SKIPPED_REMOVED
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromotionResult {
        private Integer fromYear;
        private Integer toYear;
        private int promotedCount;
        private int completedCount;
        private int skippedCount;
        private int createdClasses;
        private List<PromotionLine> lines;
    }
}
