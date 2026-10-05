package com.opportunity.school.service;

import com.opportunity.school.dto.PromotionDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.ClassLevel;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yearly promotion. Baby->KG1->KG2->KG3->COMPLETE in one @Transactional method.
 * Auto-creates missing classes for the new year; skips students already manually placed
 * in the new year and never touches removed students.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionService {

    private final AcademicYearRepository yearRepository;
    private final SchoolClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    /**
     * Dry run: returns what WOULD happen without persisting anything.
     * Implemented by running the same algorithm against a read-only snapshot.
     */
    @Transactional(readOnly = true)
    public PromotionDtos.PromotionResult preview(Integer toYear) {
        return run(toYear, true);
    }

    /**
     * Executes the promotion in one transaction.
     */
    @Transactional
    public PromotionDtos.PromotionResult promote(Integer toYear) {
        return run(toYear, false);
    }

    private PromotionDtos.PromotionResult run(Integer toYear, boolean dryRun) {
        int fromYearValue = toYear - 1;
        AcademicYear fromYear = yearRepository.findByYear(fromYearValue)
                .orElseThrow(() -> new BusinessException("Academic year " + fromYearValue + " does not exist. Create it first."));
        AcademicYear newYear = yearRepository.findByYear(toYear)
                .orElseThrow(() -> new BusinessException("Academic year " + toYear + " does not exist. Create it first."));

        // 1. Auto-create missing classes for the new year, mirroring from-year levels.
        Map<ClassLevel, SchoolClass> newClasses = new HashMap<>();
        int createdClasses = 0;
        for (ClassLevel level : ClassLevel.values()) {
            List<SchoolClass> existing = classRepository.findByAcademicYearAndLevel(newYear, level);
            if (existing.isEmpty()) {
                // Mirror classes only when the from-year has that level in use
                if (!classRepository.findByAcademicYearAndLevel(fromYear, level).isEmpty()) {
                    if (!dryRun) {
                        classRepository.save(SchoolClass.builder()
                                .level(level)
                                .name(level.name() + " - " + toYear)
                                .academicYear(newYear)
                                .build());
                    }
                    createdClasses++;
                }
                continue;
            }
            newClasses.put(level, existing.get(0));
        }

        // 2. Walk every enrollment of the from-year.
        List<Enrollment> enrollments = enrollmentRepository.findByAcademicYear(fromYear);
        int promoted = 0, completed = 0, skipped = 0;

        List<PromotionDtos.PromotionLine> lines = new java.util.ArrayList<>();
        for (Enrollment e : enrollments) {
            Student student = e.getStudent();
            PromotionDtos.PromotionLine.PromotionLineBuilder line = PromotionDtos.PromotionLine.builder()
                    .studentId(student.getId())
                    .admissionNumber(student.getAdmissionNumber())
                    .studentName(student.getFullName())
                    .fromLevel(e.getSchoolClass().getLevel().name())
                    .fromClass(e.getSchoolClass().getName());

            // Never promote removed students
            if (e.getStatus() == StudentStatus.REMOVED || student.getStatus() == StudentStatus.REMOVED) {
                lines.add(line.action("SKIPPED_REMOVED").toLevel(null).toClass(null).build());
                skipped++;
                continue;
            }

            // Skip students already manually placed in the new year
            boolean alreadyPlaced = enrollmentRepository
                    .findByStudentAndAcademicYear(student, newYear)
                    .isPresent();
            if (alreadyPlaced) {
                lines.add(line.action("SKIPPED_ALREADY_PLACED").toLevel(null).toClass(null).build());
                skipped++;
                continue;
            }

            ClassLevel fromLevel = e.getSchoolClass().getLevel();
            ClassLevel toLevel = fromLevel.next();

            if (toLevel == null) {
                // After KG3: student becomes COMPLETE
                if (!dryRun) {
                    student.setStatus(StudentStatus.COMPLETE);
                    studentRepository.save(student);
                    e.setStatus(StudentStatus.COMPLETE);
                    enrollmentRepository.save(e);
                }
                lines.add(line.action("COMPLETE").toLevel("COMPLETE").toClass(null).build());
                completed++;
                continue;
            }

            SchoolClass target = newClasses.get(toLevel);
            if (target == null) {
                // Class missing in the new year (from-year had no students there) - create it on demand
                List<SchoolClass> nowExisting = classRepository.findByAcademicYearAndLevel(newYear, toLevel);
                if (nowExisting.isEmpty()) {
                    if (!dryRun) {
                        target = classRepository.save(SchoolClass.builder()
                                .level(toLevel)
                                .name(toLevel.name() + " - " + toYear)
                                .academicYear(newYear)
                                .build());
                    }
                    createdClasses++;
                } else {
                    target = nowExisting.get(0);
                }
                if (target == null && !dryRun) {
                    target = classRepository.findByAcademicYearAndLevel(newYear, toLevel).get(0);
                }
            }

            // Update student status ACTIVE (it stays ACTIVE across levels)
            if (!dryRun) {
                student.setStatus(StudentStatus.ACTIVE);
                studentRepository.save(student);
                e.setStatus(StudentStatus.COMPLETE);
                enrollmentRepository.save(e);
                enrollmentRepository.save(Enrollment.builder()
                        .student(student)
                        .schoolClass(target)
                        .academicYear(newYear)
                        .status(StudentStatus.ACTIVE)
                        .joinedOn(LocalDate.now())
                        .build());
            }
            lines.add(line.action("PROMOTED").toLevel(toLevel.name()).toClass(targetName(target, toLevel, toYear)).build());
            promoted++;
        }

        return PromotionDtos.PromotionResult.builder()
                .fromYear(fromYearValue)
                .toYear(toYear)
                .promotedCount(promoted)
                .completedCount(completed)
                .skippedCount(skipped)
                .createdClasses(createdClasses)
                .lines(lines)
                .build();
    }

    private String targetName(SchoolClass target, ClassLevel level, Integer year) {
        if (target != null) return target.getName();
        return level.name() + " - " + year;
    }
}
