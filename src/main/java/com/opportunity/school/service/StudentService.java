package com.opportunity.school.service;

import com.opportunity.school.dto.StudentDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.AttendanceStatus;
import com.opportunity.school.model.enums.RemovalReason;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final Set<String> STUDENT_REASONS = Set.of("SHIFT", "DIED", "COMPLETE");

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final TermRepository termRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final PaymentRepository paymentRepository;
    private final RemovalLogRepository removalLogRepository;

    /**
     * Creates a student with an auto admission number and enrolls them in the given class
     * (enrollment is tied to the class's academic year).
     */
    @Transactional
    public StudentDtos.StudentDto create(StudentDtos.CreateStudentRequest req) {
        SchoolClass schoolClass = classRepository.findById(req.getClassId())
                .orElseThrow(() -> NotFoundException.entity("Class", req.getClassId()));

        Student student = Student.builder()
                .admissionNumber(nextAdmissionNumber(schoolClass.getAcademicYear().getYear()))
                .fullName(req.getFullName().trim())
                .dateOfBirth(req.getDateOfBirth())
                .gender(req.getGender())
                .guardianName(req.getGuardianName())
                .guardianPhone(req.getGuardianPhone())
                .guardianAddress(req.getGuardianAddress())
                .guardianEmail(req.getGuardianEmail())
                .status(StudentStatus.ACTIVE)
                .bloodGroup(req.getBloodGroup())
                .notes(req.getNotes())
                .build();
        student = studentRepository.save(student);

        // Enroll in the class's academic year
        enrollmentRepository.save(Enrollment.builder()
                .student(student)
                .schoolClass(schoolClass)
                .academicYear(schoolClass.getAcademicYear())
                .status(StudentStatus.ACTIVE)
                .joinedOn(LocalDate.now())
                .build());

        return toDto(student, schoolClass.getAcademicYear().getYear());
    }

    @Transactional
    public StudentDtos.StudentDto update(Long id, StudentDtos.UpdateStudentRequest req) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Student", id));
        student.setFullName(req.getFullName().trim());
        student.setDateOfBirth(req.getDateOfBirth());
        student.setGender(req.getGender());
        student.setGuardianName(req.getGuardianName());
        student.setGuardianPhone(req.getGuardianPhone());
        student.setGuardianAddress(req.getGuardianAddress());
        student.setGuardianEmail(req.getGuardianEmail());
        student.setBloodGroup(req.getBloodGroup());
        student.setNotes(req.getNotes());
        studentRepository.save(student);
        return toDto(student, null);
    }

    @Transactional(readOnly = true)
    public Page<StudentDtos.StudentDto> search(String name, Long classId, Integer year,
                                               String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));

        Set<StudentStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            try {
                statuses = EnumSet.of(StudentStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status filter: " + status);
            }
        }

        // When filtering by class, keep only students whose CURRENT class matches
        Page<Student> students = studentRepository.search(
                (name == null || name.isBlank()) ? null : name.trim(), statuses, classId, year, pageable);

        Integer targetYear = year;
        if (targetYear == null && classId != null) {
            targetYear = classRepository.findById(classId).map(c -> c.getAcademicYear().getYear()).orElse(null);
        }
        Integer finalYear = targetYear;
        List<StudentDtos.StudentDto> dtos = students.getContent().stream()
                .map(s -> toDto(s, finalYear))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, students.getTotalElements());
    }

    @Transactional
    public StudentDtos.StudentDto move(Long id, StudentDtos.MoveStudentRequest req) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Student", id));
        SchoolClass target = classRepository.findById(req.getTargetClassId())
                .orElseThrow(() -> NotFoundException.entity("Class", req.getTargetClassId()));

        AcademicYear targetYear = target.getAcademicYear();
        Enrollment current = enrollmentRepository.findByStudentAndAcademicYear(student, targetYear).orElse(null);

        if (current != null) {
            if (current.getSchoolClass().getId().equals(target.getId())) {
                throw new BusinessException("Student is already in this class for " + targetYear.getYear());
            }
            current.setSchoolClass(target);
            current.setLeftOn(null);
            enrollmentRepository.save(current);
        } else {
            // Student has no enrollment in the target year yet (e.g. incoming transfer into an older year)
            enrollmentRepository.save(Enrollment.builder()
                    .student(student)
                    .schoolClass(target)
                    .academicYear(targetYear)
                    .status(student.getStatus())
                    .joinedOn(LocalDate.now())
                    .build());
        }
        return toDto(student, targetYear.getYear());
    }

    @Transactional
    public void remove(Long id, StudentDtos.RemoveStudentRequest req) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Student", id));
        if (student.getStatus() == StudentStatus.REMOVED) {
            throw new BusinessException("Student is already removed");
        }

        RemovalReason reason;
        try {
            reason = RemovalReason.valueOf(req.getReason().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid removal reason. Use SHIFT, DIED or COMPLETE (FIRED is for teachers)");
        }
        if (!STUDENT_REASONS.contains(reason.name())) {
            throw new BusinessException("Invalid removal reason for a student. Use SHIFT, DIED or COMPLETE");
        }

        student.setStatus(StudentStatus.REMOVED);
        studentRepository.save(student);

        // Close ACTIVE enrollments
        for (Enrollment e : enrollmentRepository.findByStudentOrderByAcademicYearYearAsc(student)) {
            if (e.getStatus() == StudentStatus.ACTIVE) {
                e.setStatus(StudentStatus.REMOVED);
                e.setLeftOn(LocalDateTime.now());
                enrollmentRepository.save(e);
            }
        }

        removalLogRepository.save(RemovalLog.builder()
                .entityType("STUDENT")
                .entityId(student.getId())
                .entityName(student.getFullName() + " (" + student.getAdmissionNumber() + ")")
                .reason(reason)
                .note(req.getNote())
                .removalDate(req.getRemovalDate())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Profile: student info + class history + attendance summary (over all attendance records)
     * + fee status per term.
     */
    @Transactional(readOnly = true)
    public StudentDtos.StudentProfileDto profile(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Student", id));

        List<StudentDtos.EnrollmentDto> history = enrollmentRepository
                .findByStudentOrderByAcademicYearYearAsc(student).stream()
                .map(e -> StudentDtos.EnrollmentDto.builder()
                        .id(e.getId())
                        .classId(e.getSchoolClass().getId())
                        .className(e.getSchoolClass().getName())
                        .level(e.getSchoolClass().getLevel().name())
                        .year(e.getAcademicYear().getYear())
                        .status(e.getStatus().name())
                        .joinedOn(e.getJoinedOn())
                        .build())
                .toList();

        long present = attendanceRepository.countByStudentIdAndStatus(student.getId(), AttendanceStatus.PRESENT);
        long absent = attendanceRepository.countByStudentIdAndStatus(student.getId(), AttendanceStatus.ABSENT);
        long late = attendanceRepository.countByStudentIdAndStatus(student.getId(), AttendanceStatus.LATE);
        long total = present + absent + late;
        double pct = total == 0 ? 0.0 : Math.round(((present + late) * 10000.0) / total) / 100.0;

        List<StudentDtos.FeeStatusFlat> fees = termRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(t -> t.getAcademicYear().getYear()))
                .map(term -> {
                    BigDecimal expected = expectedFee(student, term);
                    BigDecimal paid = paymentRepository.sumByStudentAndTerm(student.getId(), term);
                    BigDecimal balance = expected.subtract(paid).max(BigDecimal.ZERO);
                    String feeStatus = computeFeeStatus(expected, paid);
                    return StudentDtos.FeeStatusFlat.builder()
                            .termId(term.getId())
                            .termNumber(term.getNumber().name())
                            .year(term.getAcademicYear().getYear())
                            .expected(expected.doubleValue())
                            .paid(paid.doubleValue())
                            .balance(balance.doubleValue())
                            .status(feeStatus)
                            .build();
                })
                .filter(f -> f.getExpected() > 0 || f.getPaid() > 0)
                .toList();

        Integer latestYear = history.isEmpty() ? null : history.get(history.size() - 1).getYear();
        return StudentDtos.StudentProfileDto.builder()
                .student(toDto(student, latestYear))
                .classHistory(history)
                .attendance(StudentDtos.AttendanceSummaryDto.builder()
                        .present(present).absent(absent).late(late).percentage(pct).build())
                .fees(fees)
                .build();
    }

    // ---------------- helpers ----------------

    private String nextAdmissionNumber(Integer year) {
        long next = studentRepository.findMaxId() + 1;
        String candidate;
        do {
            candidate = String.format("ON-%d-%04d", year, next);
            next++;
        } while (studentRepository.findByAdmissionNumber(candidate).isPresent());
        return candidate;
    }

    private BigDecimal expectedFee(Student student, Term term) {
        Optional<FeeStructure> specific = feeStructureRepository.findByTerm_IdAndSchoolClass_Id(
                term.getId(), currentClassId(student, term.getAcademicYear()));
        if (specific.isPresent()) {
            return specific.get().getAmount();
        }
        return feeStructureRepository.findByTermAndSchoolClassIsNull(term)
                .map(FeeStructure::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    private Long currentClassId(Student student, AcademicYear year) {
        return enrollmentRepository.findByStudentAndAcademicYear(student, year)
                .map(e -> e.getSchoolClass().getId())
                .orElse(null);
    }

    static String computeFeeStatus(BigDecimal expected, BigDecimal paid) {
        if (expected.compareTo(BigDecimal.ZERO) <= 0) {
            return paid.signum() > 0 ? "PAID" : "NOT_PAID";
        }
        int cmp = paid.compareTo(expected);
        if (cmp >= 0) return "PAID";
        if (paid.signum() > 0) return "PARTIAL";
        return "NOT_PAID";
    }

    private StudentDtos.StudentDto toDto(Student s, Integer year) {
        StudentDtos.StudentDto dto = new StudentDtos.StudentDto();
        dto.setId(s.getId());
        dto.setAdmissionNumber(s.getAdmissionNumber());
        dto.setFullName(s.getFullName());
        dto.setDateOfBirth(s.getDateOfBirth());
        dto.setGender(s.getGender());
        dto.setGuardianName(s.getGuardianName());
        dto.setGuardianPhone(s.getGuardianPhone());
        dto.setGuardianAddress(s.getGuardianAddress());
        dto.setGuardianEmail(s.getGuardianEmail());
        dto.setStatus(s.getStatus().name());
        dto.setBloodGroup(s.getBloodGroup());
        dto.setNotes(s.getNotes());
        if (year != null) {
            AcademicYear y = yearRepository.findByYear(year).orElse(null);
            if (y != null) {
                enrollmentRepository.findByStudentAndAcademicYear(s, y)
                        .ifPresent(e -> {
                            dto.setCurrentClassId(e.getSchoolClass().getId());
                            dto.setCurrentClassName(e.getSchoolClass().getName());
                            dto.setCurrentLevel(e.getSchoolClass().getLevel().name());
                            dto.setCurrentYear(year);
                        });
            }
        }
        return dto;
    }
}
