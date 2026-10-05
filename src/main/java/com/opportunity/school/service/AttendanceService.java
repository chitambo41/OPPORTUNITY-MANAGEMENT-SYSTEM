package com.opportunity.school.service;

import com.opportunity.school.dto.AttendanceDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.Attendance;
import com.opportunity.school.model.Enrollment;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Student;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.Term;
import com.opportunity.school.model.enums.AttendanceStatus;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.AttendanceRepository;
import com.opportunity.school.repository.EnrollmentRepository;
import com.opportunity.school.repository.SchoolClassRepository;
import com.opportunity.school.repository.StudentRepository;
import com.opportunity.school.repository.TermRepository;
import com.opportunity.school.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final TermRepository termRepository;
    private final SecurityUtils securityUtils;

    /**
     * Class teacher saves attendance for a class on a date.
     * Rules: weekdays only, date inside current term, no future dates,
     * editable only on the same day, one record per student per date.
     */
    @Transactional
    public void save(AttendanceDtos.SaveAttendanceRequest req) {
        SchoolClass schoolClass = classRepository.findById(req.getClassId())
                .orElseThrow(() -> NotFoundException.entity("Class", req.getClassId()));

        // Only the class teacher may save
        Teacher teacher = securityUtils.currentTeacher();
        if (schoolClass.getClassTeacher() == null ||
                !schoolClass.getClassTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException("Only the class teacher can mark attendance for this class");
        }

        LocalDate date = req.getDate();

        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("Cannot mark attendance for a future date");
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new BusinessException("Attendance can only be marked on weekdays");
        }

        Term currentTerm = termRepository.findByCurrentTrue()
                .orElseThrow(() -> new BusinessException("No current term is set. Ask the admin to set the current year and term."));
        if (date.isBefore(currentTerm.getStartDate()) || date.isAfter(currentTerm.getEndDate())) {
            throw new BusinessException("Date must be inside the current term (" +
                    currentTerm.getStartDate() + " to " + currentTerm.getEndDate() + ")");
        }

        if (!date.isEqual(LocalDate.now())) {
            throw new BusinessException("Attendance is only editable on the same day");
        }

        // Upsert per student: unique student+date
        for (AttendanceDtos.SaveAttendanceRequest.Entry entry : req.getEntries()) {
            Student student = studentRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> NotFoundException.entity("Student", entry.getStudentId()));

            boolean inClass = enrollmentRepository
                    .findByStudentAndAcademicYear(student, currentTerm.getAcademicYear())
                    .map(e -> e.getSchoolClass().getId().equals(schoolClass.getId()) && e.getStatus() == StudentStatus.ACTIVE)
                    .orElse(false);
            if (!inClass) {
                throw new BusinessException("Student " + student.getFullName() + " is not ACTIVE in this class");
            }

            AttendanceStatus status;
            try {
                status = AttendanceStatus.fromString(entry.getStatus());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid attendance status: " + entry.getStatus());
            }

            Attendance record = attendanceRepository.findByStudentIdAndDate(student.getId(), date)
                    .orElseGet(() -> Attendance.builder().student(student).date(date).build());
            record.setStatus(status);
            record.setNote(entry.getNote());
            attendanceRepository.save(record);
        }
    }

    /** Roster of ACTIVE students with their marked status for a date. */
    @Transactional(readOnly = true)
    public AttendanceDtos.StudentAttendanceRow rosterRow(Student student, LocalDate date) {
        Attendance a = attendanceRepository.findByStudentIdAndDate(student.getId(), date).orElse(null);
        return AttendanceDtos.StudentAttendanceRow.builder()
                .studentId(student.getId())
                .admissionNumber(student.getAdmissionNumber())
                .studentName(student.getFullName())
                .status(a != null ? a.getStatus().name() : null)
                .note(a != null ? a.getNote() : null)
                .build();
    }

    /** Daily summary for one class (counts per status + unmarked). */
    @Transactional(readOnly = true)
    public AttendanceDtos.DailySummaryDto dailySummary(Long classId, LocalDate date) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        long totalActive = enrollmentRepository.countBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE);

        Map<AttendanceStatus, Long> counts = new HashMap<>();
        for (Object[] row : attendanceRepository.countByClassAndDate(classId, date)) {
            counts.put((AttendanceStatus) row[0], (Long) row[1]);
        }
        long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
        long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
        long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);

        return AttendanceDtos.DailySummaryDto.builder()
                .classId(classId)
                .className(schoolClass.getName())
                .date(date)
                .present(present)
                .absent(absent)
                .late(late)
                .unmarked(Math.max(0, totalActive - present - absent - late))
                .totalActive(totalActive)
                .build();
    }

    /** Per-student attendance report for a class within a date range, with percentage. */
    @Transactional(readOnly = true)
    public List<AttendanceDtos.StudentReportRow> classReport(Long classId, LocalDate start, LocalDate end) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        List<Student> students = enrollmentRepository
                .findBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE).stream()
                .map(Enrollment::getStudent)
                .toList();

        List<AttendanceDtos.StudentReportRow> rows = new java.util.ArrayList<>();
        for (Student s : students) {
            Map<AttendanceStatus, Long> counts = new HashMap<>();
            for (Object[] row : attendanceRepository.countByStudentInRange(s.getId(), start, end)) {
                counts.put((AttendanceStatus) row[0], (Long) row[1]);
            }
            long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
            long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
            long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
            long total = present + absent + late;
            double pct = total == 0 ? 0.0 : Math.round(((present + late) * 10000.0) / total) / 100.0;
            rows.add(AttendanceDtos.StudentReportRow.builder()
                    .studentId(s.getId())
                    .admissionNumber(s.getAdmissionNumber())
                    .studentName(s.getFullName())
                    .present(present).absent(absent).late(late)
                    .totalMarked(total).percentage(pct)
                    .build());
        }
        return rows;
    }

    /** Attendance rows for a whole class on a date (admin view). */
    @Transactional(readOnly = true)
    public List<AttendanceDtos.StudentAttendanceRow> classDayList(Long classId, LocalDate date) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        return enrollmentRepository.findBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE).stream()
                .map(Enrollment::getStudent)
                .map(s -> rosterRow(s, date))
                .toList();
    }
}
