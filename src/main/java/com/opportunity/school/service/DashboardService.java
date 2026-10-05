package com.opportunity.school.service;

import com.opportunity.school.dto.DashboardDtos;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.AttendanceStatus;
import com.opportunity.school.model.enums.ExamStatus;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import com.opportunity.school.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ExamRepository examRepository;
    private final ResultSubmissionRepository submissionRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository classRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final MarkRepository markRepository;
    private final AttendanceRepository attendanceRepository;
    private final TermRepository termRepository;
    private final FeeService feeService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public DashboardDtos.AdminDashboardDto adminDashboard() {
        long activeStudents = studentRepository.countByStatus(StudentStatus.ACTIVE);
        long activeTeachers = teacherRepository.countByActiveTrue();
        long pending = submissionRepository.countByStatus(ResultSubmission.ResultStatus.PENDING);

        Term currentTerm = termRepository.findByCurrentTrue().orElse(null);
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal expected = BigDecimal.ZERO;
        if (currentTerm != null) {
            collected = paymentRepository.sumAmountByTerm(currentTerm);
            for (Enrollment e : enrollmentRepository.findByAcademicYear(currentTerm.getAcademicYear())) {
                if (e.getStatus() == StudentStatus.ACTIVE) {
                    expected = expected.add(feeService.expectedFor(e.getStudent(), currentTerm));
                }
            }
        }

        // Students per class (current year)
        List<DashboardDtos.AdminDashboardDto.ClassCount> perClass = new ArrayList<>();
        AcademicYear currentYear = currentTerm != null ? currentTerm.getAcademicYear() : null;
        if (currentYear != null) {
            for (SchoolClass c : classRepository.findByAcademicYearOrderByIdAsc(currentYear)) {
                perClass.add(DashboardDtos.AdminDashboardDto.ClassCount.builder()
                        .classId(c.getId())
                        .className(c.getName())
                        .level(c.getLevel().name())
                        .count(enrollmentRepository.countBySchoolClassAndStatus(c, StudentStatus.ACTIVE))
                        .build());
            }
        }

        // Today's attendance per class
        LocalDate today = LocalDate.now();
        List<DashboardDtos.AdminDashboardDto.ClassAttendance> attendanceRows = new ArrayList<>();
        for (DashboardDtos.AdminDashboardDto.ClassCount cc : perClass) {
            Map<AttendanceStatus, Long> counts = new HashMap<>();
            for (Object[] row : attendanceRepository.countByClassAndDate(cc.getClassId(), today)) {
                counts.put((AttendanceStatus) row[0], (Long) row[1]);
            }
            long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
            long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
            long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
            attendanceRows.add(DashboardDtos.AdminDashboardDto.ClassAttendance.builder()
                    .classId(cc.getClassId())
                    .className(cc.getClassName())
                    .present(present).absent(absent).late(late)
                    .unmarked(Math.max(0, cc.getCount() - present - absent - late))
                    .totalActive(cc.getCount())
                    .build());
        }

        return DashboardDtos.AdminDashboardDto.builder()
                .activeStudents(activeStudents)
                .activeTeachers(activeTeachers)
                .resultsPendingReview(pending)
                .feesCollected(collected)
                .feesOutstanding(expected.subtract(collected).max(BigDecimal.ZERO))
                .studentsPerClass(perClass)
                .todayAttendance(attendanceRows)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardDtos.TeacherDashboardDto teacherDashboard() {
        Teacher teacher = securityUtils.currentTeacher();
        Term currentTerm = termRepository.findByCurrentTrue()
                .orElseThrow(() -> new NotFoundException("No current academic year is set"));
        AcademicYear currentYear = currentTerm.getAcademicYear();

        List<DashboardDtos.TeacherDashboardDto.ClassInfo> myClasses = new ArrayList<>();
        Long classTeacherOfId = null;
        String classTeacherOfName = null;

        for (SchoolClass c : classRepository.findClassesForTeacher(teacher, currentYear)) {
            boolean isClassTeacher = c.getClassTeacher() != null && c.getClassTeacher().getId().equals(teacher.getId());
            List<String> subjects = classSubjectRepository.findBySchoolClassOrderByIdAsc(c).stream()
                    .filter(cs -> cs.getTeacher() != null && cs.getTeacher().getId().equals(teacher.getId()))
                    .map(cs -> cs.getSubject().getName())
                    .toList();
            if (isClassTeacher) {
                classTeacherOfId = c.getId();
                classTeacherOfName = c.getName();
            }
            myClasses.add(DashboardDtos.TeacherDashboardDto.ClassInfo.builder()
                    .classId(c.getId())
                    .className(c.getName())
                    .level(c.getLevel().name())
                    .role(isClassTeacher ? "CLASS_TEACHER" : "SUBJECT_TEACHER")
                    .subjects(subjects)
                    .build());
        }

        // Attendance status today for the class they lead
        String attendanceStatus = "NOT_A_CLASS_TEACHER";
        if (classTeacherOfId != null) {
            long totalActive = enrollmentRepository.countBySchoolClassAndStatus(
                    classRepository.findById(classTeacherOfId).orElseThrow(), StudentStatus.ACTIVE);
            Map<AttendanceStatus, Long> counts = new HashMap<>();
            for (Object[] row : attendanceRepository.countByClassAndDate(classTeacherOfId, LocalDate.now())) {
                counts.put((AttendanceStatus) row[0], (Long) row[1]);
            }
            long marked = counts.getOrDefault(AttendanceStatus.PRESENT, 0L)
                    + counts.getOrDefault(AttendanceStatus.ABSENT, 0L)
                    + counts.getOrDefault(AttendanceStatus.LATE, 0L);
            attendanceStatus = marked == 0 ? "NOT_MARKED" : (marked >= totalActive && totalActive > 0 ? "MARKED" : "PARTIAL");
        }

        // Pending marks: OPEN exams where a subject they teach still has missing marks
        List<DashboardDtos.TeacherDashboardDto.PendingMarks> pending = new ArrayList<>();
        for (Exam exam : examRepository.findByStatus(ExamStatus.OPEN)) {
            for (ClassSubject cs : classSubjectRepository.findBySchoolClassOrderByIdAsc(exam.getSchoolClass())) {
                if (cs.getTeacher() == null || !cs.getTeacher().getId().equals(teacher.getId())) {
                    continue;
                }
                long withMarks = markRepository.findByExamIdAndSubjectId(exam.getId(), cs.getSubject().getId()).stream()
                        .map(Mark::getStudent)
                        .map(Student::getId)
                        .distinct()
                        .count();
                long active = enrollmentRepository.countBySchoolClassAndStatus(exam.getSchoolClass(), StudentStatus.ACTIVE);
                if (withMarks < active) {
                    pending.add(DashboardDtos.TeacherDashboardDto.PendingMarks.builder()
                            .examId(exam.getId())
                            .examName(exam.getName())
                            .className(exam.getSchoolClass().getName())
                            .subjectId(cs.getSubject().getId())
                            .subjectName(cs.getSubject().getName())
                            .status(exam.getStatus().name())
                            .studentsWithMarks((int) withMarks)
                            .activeStudents((int) active)
                            .missing((int) (active - withMarks))
                            .build());
                }
            }
        }

        return DashboardDtos.TeacherDashboardDto.builder()
                .myClasses(myClasses)
                .classTeacherOfId(classTeacherOfId)
                .classTeacherOfName(classTeacherOfName)
                .attendanceStatusToday(attendanceStatus)
                .pendingMarks(pending)
                .build();
    }
}
