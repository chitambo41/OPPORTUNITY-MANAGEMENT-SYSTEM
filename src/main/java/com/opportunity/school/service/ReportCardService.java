package com.opportunity.school.service;

import com.opportunity.school.dto.ExamDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.AttendanceStatus;
import com.opportunity.school.model.enums.ExamStatus;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Report cards. Only APPROVED results can be printed.
 * Grades: A >= 80, B >= 65, C >= 50, D >= 35, else E (percentage of maxMarks).
 */
@Service
@RequiredArgsConstructor
public class ReportCardService {

    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;

    public Map<String, Object> reportCard(Long examId, Long studentId) {
        Map<String, Object> all = reportCardsForClass(examId);
        List<Map<String, Object>> cards = castList(all.get("reportCards"));
        for (Map<String, Object> card : cards) {
            if (Objects.equals(((Number) card.get("studentId")).longValue(), studentId)) {
                return card;
            }
        }
        throw new NotFoundException("Student " + studentId + " not found in this exam's class");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reportCardsForClass(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));
        if (exam.getStatus() != ExamStatus.APPROVED) {
            throw new BusinessException("Report cards can only be printed for APPROVED results. Current status: "
                    + exam.getStatus());
        }

        SchoolClass schoolClass = exam.getSchoolClass();
        List<ClassSubject> subjects = classSubjectRepository.findBySchoolClassOrderByIdAsc(schoolClass);
        List<Student> students = enrollmentRepository
                .findBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE).stream()
                .map(Enrollment::getStudent)
                .sorted(Comparator.comparing(Student::getFullName))
                .toList();

        // Pre-compute totals for positions
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Student s : students) {
            List<Map<String, Object>> subjectRows = new ArrayList<>();
            int total = 0;
            int maxTotal = 0;
            int counted = 0;
            for (ClassSubject cs : subjects) {
                Optional<Mark> markOpt = markRepository
                        .findByExamIdAndStudentIdAndSubjectId(examId, s.getId(), cs.getSubject().getId());
                int max = exam.getMaxMarks();
                maxTotal += max;
                if (markOpt.isPresent()) {
                    int score = markOpt.get().getScore();
                    total += score;
                    counted++;
                    subjectRows.add(Map.of(
                            "subject", cs.getSubject().getName(),
                            "score", score,
                            "maxMarks", max,
                            "grade", grade(score * 100.0 / max)));
                } else {
                    subjectRows.add(Map.of(
                            "subject", cs.getSubject().getName(),
                            "score", "-",
                            "maxMarks", max,
                            "grade", "-"));
                }
            }
            double average = counted > 0 ? Math.round((total * 1000.0) / counted) / 10.0 : 0.0;

            // Attendance summary inside the exam's term
            Term term = exam.getTerm();
            Map<AttendanceStatus, Long> attCounts = new EnumMap<>(AttendanceStatus.class);
            for (Object[] row : attendanceRepository.countByStudentInRange(
                    s.getId(), term.getStartDate(), term.getEndDate())) {
                attCounts.put((AttendanceStatus) row[0], (Long) row[1]);
            }
            long present = attCounts.getOrDefault(AttendanceStatus.PRESENT, 0L);
            long absent = attCounts.getOrDefault(AttendanceStatus.ABSENT, 0L);
            long late = attCounts.getOrDefault(AttendanceStatus.LATE, 0L);
            long markedDays = present + absent + late;
            double attPct = markedDays == 0 ? 0.0 : Math.round(((present + late) * 10000.0) / markedDays) / 100.0;

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("studentId", s.getId());
            card.put("admissionNumber", s.getAdmissionNumber());
            card.put("studentName", s.getFullName());
            card.put("subjects", subjectRows);
            card.put("total", total);
            card.put("maxTotal", maxTotal);
            card.put("average", average);
            card.put("grade", grade(average));
            card.put("attendancePresent", present);
            card.put("attendanceAbsent", absent);
            card.put("attendanceLate", late);
            card.put("attendancePercentage", attPct);
            card.put("remarks", "");
            cards.add(card);
        }

        // Positions with tie handling: same total => same position, next ranks skipped
        cards.sort((a, b) -> Integer.compare((Integer) b.get("total"), (Integer) a.get("total")));
        int position = 0;
        int lastTotal = Integer.MIN_VALUE;
        int index = 0;
        for (Map<String, Object> card : cards) {
            index++;
            int total = (Integer) card.get("total");
            if (total != lastTotal) {
                position = index;
                lastTotal = total;
            }
            card.put("position", position);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exam", Map.of(
                "id", exam.getId(),
                "name", exam.getName(),
                "className", schoolClass.getName(),
                "level", schoolClass.getLevel().name(),
                "term", exam.getTerm().getNumber().name(),
                "year", exam.getTerm().getAcademicYear().getYear(),
                "maxMarks", exam.getMaxMarks(),
                "status", exam.getStatus().name()));
        result.put("reportCards", cards);
        return result;
    }

    private static String grade(double pct) {
        if (pct >= 80) return "A";
        if (pct >= 65) return "B";
        if (pct >= 50) return "C";
        if (pct >= 35) return "D";
        return "E";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return (List<Map<String, Object>>) o;
    }
}
