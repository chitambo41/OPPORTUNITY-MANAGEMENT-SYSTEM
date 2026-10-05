package com.opportunity.school.controller;

import com.opportunity.school.dto.AttendanceDtos;
import com.opportunity.school.dto.ExamDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.EnrollmentRepository;
import com.opportunity.school.repository.SchoolClassRepository;
import com.opportunity.school.repository.TermRepository;
import com.opportunity.school.security.SecurityUtils;
import com.opportunity.school.service.AttendanceService;
import com.opportunity.school.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final AttendanceService attendanceService;
    private final ExamService examService;
    private final SchoolClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TermRepository termRepository;
    private final SecurityUtils securityUtils;

    // ---------------- My classes ----------------

    @GetMapping("/my-classes")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myClasses() {
        Teacher teacher = securityUtils.currentTeacher();
        AcademicYear currentYear = currentYear();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SchoolClass c : classRepository.findClassesForTeacher(teacher, currentYear)) {
            boolean isClassTeacher = c.getClassTeacher() != null
                    && c.getClassTeacher().getId().equals(teacher.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("level", c.getLevel().name());
            row.put("year", c.getAcademicYear().getYear());
            row.put("isClassTeacher", isClassTeacher);
            rows.add(row);
        }
        return rows;
    }

    /** Class detail: subjects the teacher is involved in + ACTIVE students. */
    @GetMapping("/my-classes/{classId}")
    @Transactional(readOnly = true)
    public Map<String, Object> myClassDetail(@PathVariable Long classId) {
        Teacher teacher = securityUtils.currentTeacher();
        SchoolClass c = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        if (!teacherTeachesClass(teacher, c)) {
            throw new BusinessException("You do not teach this class");
        }
        boolean isClassTeacher = c.getClassTeacher() != null
                && c.getClassTeacher().getId().equals(teacher.getId());

        List<Map<String, Object>> subjects = new ArrayList<>();
        c.getClassSubjects().stream()
                .filter(cs -> isClassTeacher
                        || (cs.getTeacher() != null && cs.getTeacher().getId().equals(teacher.getId())))
                .forEach(cs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("classSubjectId", cs.getId());
                    row.put("subjectId", cs.getSubject().getId());
                    row.put("subjectName", cs.getSubject().getName());
                    row.put("teacherName", cs.getTeacher() != null ? cs.getTeacher().getFullName() : "");
                    subjects.add(row);
                });

        List<Map<String, Object>> students = new ArrayList<>();
        for (var e : enrollmentRepository.findBySchoolClassAndStatus(c, StudentStatus.ACTIVE)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", e.getStudent().getId());
            row.put("admissionNumber", e.getStudent().getAdmissionNumber());
            row.put("fullName", e.getStudent().getFullName());
            students.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", c.getId());
        body.put("name", c.getName());
        body.put("level", c.getLevel().name());
        body.put("isClassTeacher", isClassTeacher);
        body.put("subjects", subjects);
        body.put("students", students);
        return body;
    }

    // ---------------- Attendance (class teacher only) ----------------

    @GetMapping("/attendance")
    @Transactional(readOnly = true)
    public List<AttendanceDtos.StudentAttendanceRow> attendanceRoster(@RequestParam Long classId,
                                                                      @RequestParam LocalDate date) {
        Teacher teacher = securityUtils.currentTeacher();
        SchoolClass c = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        requireClassTeacher(teacher, c);
        List<AttendanceDtos.StudentAttendanceRow> rows = new ArrayList<>();
        for (var e : enrollmentRepository.findBySchoolClassAndStatus(c, StudentStatus.ACTIVE)) {
            rows.add(attendanceService.rosterRow(e.getStudent(), date));
        }
        return rows;
    }

    @PostMapping("/attendance")
    public ResponseEntity<Map<String, String>> saveAttendance(
            @Valid @RequestBody AttendanceDtos.SaveAttendanceRequest request) {
        attendanceService.save(request);
        return ResponseEntity.ok(Map.of("message", "Attendance saved"));
    }

    // ---------------- Marks ----------------

    @PostMapping("/marks")
    public ResponseEntity<Map<String, String>> saveMarks(@Valid @RequestBody ExamDtos.SaveMarksRequest request) {
        examService.saveMarks(request);
        return ResponseEntity.ok(Map.of("message", "Marks saved"));
    }

    @GetMapping("/exams/{examId}/progress")
    public List<ExamDtos.StudentProgressDto> progress(@PathVariable Long examId) {
        return examService.progress(examId);
    }

    @GetMapping("/exams")
    public List<ExamDtos.ExamDto> myExams(@RequestParam(required = false) Long termId) {
        Teacher teacher = securityUtils.currentTeacher();
        return examService.listForTeacher(teacher, termId);
    }

    @GetMapping("/exams/{examId}/results")
    public ExamDtos.ResultsTableDto results(@PathVariable Long examId) {
        return examService.resultsTableForTeacher(examId);
    }

    @PostMapping("/exams/{examId}/send")
    public ExamDtos.ExamDto sendResults(@PathVariable Long examId,
                                        @RequestBody(required = false) ExamDtos.SendResultsRequest request) {
        return examService.sendResults(examId, request);
    }

    // ---------------- helpers ----------------

    private AcademicYear currentYear() {
        return termRepository.findByCurrentTrue()
                .map(t -> t.getAcademicYear())
                .orElseThrow(() -> new BusinessException("No current academic year/term is set"));
    }

    private boolean teacherTeachesClass(Teacher teacher, SchoolClass c) {
        boolean leads = c.getClassTeacher() != null && c.getClassTeacher().getId().equals(teacher.getId());
        boolean teaches = c.getClassSubjects().stream()
                .anyMatch(cs -> cs.getTeacher() != null && cs.getTeacher().getId().equals(teacher.getId()));
        return leads || teaches;
    }

    private void requireClassTeacher(Teacher teacher, SchoolClass c) {
        if (c.getClassTeacher() == null || !c.getClassTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException("Only the class teacher can do this");
        }
    }
}
