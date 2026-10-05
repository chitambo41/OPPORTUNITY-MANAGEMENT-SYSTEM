package com.opportunity.school.service;

import com.opportunity.school.dto.FeeDtos;
import com.opportunity.school.dto.StudentDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.PaymentMethod;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import com.opportunity.school.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeStructureRepository feeStructureRepository;
    private final PaymentRepository paymentRepository;
    private final TermRepository termRepository;
    private final SchoolClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AcademicYearRepository yearRepository;
    private final SecurityUtils securityUtils;

    // ---------------- Fee structures ----------------

    @Transactional
    public FeeDtos.FeeStructureDto setFee(FeeDtos.SetFeeRequest req) {
        Term term = termRepository.findById(req.getTermId())
                .orElseThrow(() -> NotFoundException.entity("Term", req.getTermId()));
        if (req.getAmount().signum() < 0) {
            throw new BusinessException("Fee amount cannot be negative");
        }

        boolean general = Boolean.TRUE.equals(req.getAllClasses()) || req.getClassId() == null;
        final SchoolClass schoolClass;
        if (!general) {
            schoolClass = classRepository.findById(req.getClassId())
                    .orElseThrow(() -> NotFoundException.entity("Class", req.getClassId()));
        } else {
            schoolClass = null;
        }

        FeeStructure fee = general
                ? feeStructureRepository.findByTermAndSchoolClassIsNull(term)
                .orElseGet(() -> FeeStructure.builder().term(term).build())
                : feeStructureRepository.findByTermAndSchoolClass(term, schoolClass)
                .orElseGet(() -> FeeStructure.builder().term(term).schoolClass(schoolClass).build());
        fee.setAmount(req.getAmount());
        fee = feeStructureRepository.save(fee);

        return toFeeDto(fee);
    }

    @Transactional(readOnly = true)
    public List<FeeDtos.FeeStructureDto> listFees(Long termId) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> NotFoundException.entity("Term", termId));
        return feeStructureRepository.findByTerm(term).stream()
                .map(this::toFeeDto)
                .toList();
    }

    // ---------------- Payments ----------------

    /**
     * Records a payment with an auto receipt number and rejects overpayment
     * (total paid after this payment must not exceed the expected fee).
     */
    @Transactional
    public FeeDtos.PaymentDto recordPayment(FeeDtos.RecordPaymentRequest req) {
        Student student = studentRepository.findById(req.getStudentId())
                .orElseThrow(() -> NotFoundException.entity("Student", req.getStudentId()));
        Term term = termRepository.findById(req.getTermId())
                .orElseThrow(() -> NotFoundException.entity("Term", req.getTermId()));
        if (req.getAmount().signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        BigDecimal expected = expectedFor(student, term);
        BigDecimal alreadyPaid = paymentRepository.sumByStudentAndTerm(student.getId(), term);
        BigDecimal after = alreadyPaid.add(req.getAmount());
        if (expected.signum() > 0 && after.compareTo(expected) > 0) {
            throw new BusinessException("Overpayment rejected: expected " + expected + ", already paid " +
                    alreadyPaid + ", this payment of " + req.getAmount() + " would exceed by " +
                    after.subtract(expected));
        }

        Payment payment = Payment.builder()
                .receiptNumber(nextReceiptNumber())
                .student(student)
                .term(term)
                .amount(req.getAmount())
                .paymentDate(req.getPaymentDate())
                .method(parseMethod(req.getMethod()))
                .note(req.getNote())
                .recordedBy(securityUtils.currentUserEntity())
                .build();
        payment = paymentRepository.save(payment);
        return toPaymentDto(payment);
    }

    // ---------------- Statuses ----------------

    /** Per-student-per-term fee status: PAID (balance 0), PARTIAL (with amounts), NOT_PAID. */
    @Transactional(readOnly = true)
    public List<FeeDtos.StudentFeeStatusDto> statuses(Long termId, Long classId, String statusFilter) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> NotFoundException.entity("Term", termId));

        List<Enrollment> enrollments;
        if (classId != null) {
            SchoolClass schoolClass = classRepository.findById(classId)
                    .orElseThrow(() -> NotFoundException.entity("Class", classId));
            enrollments = enrollmentRepository.findBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE);
        } else {
            enrollments = enrollmentRepository.findByAcademicYear(term.getAcademicYear()).stream()
                    .filter(e -> e.getStatus() == StudentStatus.ACTIVE)
                    .toList();
        }

        List<FeeDtos.StudentFeeStatusDto> rows = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Student student = e.getStudent();
            BigDecimal expected = expectedFor(student, term);
            BigDecimal paid = paymentRepository.sumByStudentAndTerm(student.getId(), term);
            BigDecimal balance = expected.subtract(paid).max(BigDecimal.ZERO);
            String status = StudentService.computeFeeStatus(expected, paid);
            if (statusFilter != null && !statusFilter.isBlank() && !status.equalsIgnoreCase(statusFilter.trim())) {
                continue;
            }
            rows.add(FeeDtos.StudentFeeStatusDto.builder()
                    .studentId(student.getId())
                    .admissionNumber(student.getAdmissionNumber())
                    .studentName(student.getFullName())
                    .className(e.getSchoolClass().getName())
                    .termId(term.getId())
                    .termNumber(term.getNumber().name())
                    .expected(expected)
                    .paid(paid)
                    .balance(balance)
                    .status(status)
                    .build());
        }
        rows.sort(java.util.Comparator.comparing(FeeDtos.StudentFeeStatusDto::getStudentName));
        return rows;
    }

    /** Summary cards: expected, collected, outstanding for a term. */
    @Transactional(readOnly = true)
    public FeeDtos.FeeSummaryDto summary(Long termId) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> NotFoundException.entity("Term", termId));
        BigDecimal expected = BigDecimal.ZERO;
        for (Enrollment e : enrollmentRepository.findByAcademicYear(term.getAcademicYear())) {
            if (e.getStatus() == StudentStatus.ACTIVE) {
                expected = expected.add(expectedFor(e.getStudent(), term));
            }
        }
        BigDecimal collected = paymentRepository.sumAmountByTerm(term);
        return FeeDtos.FeeSummaryDto.builder()
                .expected(expected)
                .collected(collected)
                .outstanding(expected.subtract(collected).max(BigDecimal.ZERO))
                .build();
    }

    /** Printable fee statement for one student (per-term expected/paid/balance + payments). */
    @Transactional(readOnly = true)
    public FeeDtos.FeeStatementDto statement(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> NotFoundException.entity("Student", studentId));
        List<FeeDtos.FeeStatementDto.Line> lines = new ArrayList<>();
        for (Term term : termRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(t -> t.getAcademicYear().getYear())).toList()) {
            BigDecimal expected = expectedFor(student, term);
            BigDecimal paid = paymentRepository.sumByStudentAndTerm(student.getId(), term);
            if (expected.signum() == 0 && paid.signum() == 0) {
                continue;
            }
            List<FeeDtos.PaymentDto> payments = paymentRepository
                    .findByStudentIdOrderByPaymentDateDescIdDesc(studentId).stream()
                    .filter(p -> p.getTerm().getId().equals(term.getId()))
                    .map(this::toPaymentDto)
                    .toList();
            lines.add(FeeDtos.FeeStatementDto.Line.builder()
                    .termId(term.getId())
                    .termNumber(term.getNumber().name())
                    .year(term.getAcademicYear().getYear())
                    .expected(expected)
                    .paid(paid)
                    .balance(expected.subtract(paid).max(BigDecimal.ZERO))
                    .status(StudentService.computeFeeStatus(expected, paid))
                    .payments(payments)
                    .build());
        }
        Integer latestYear = lines.isEmpty() ? null : lines.get(lines.size() - 1).getYear();
        return FeeDtos.FeeStatementDto.builder()
                .student(studentBrief(student, latestYear))
                .lines(lines)
                .build();
    }

    /** Printable receipt for one payment. */
    @Transactional(readOnly = true)
    public FeeDtos.PaymentDto receipt(String receiptNumber) {
        Payment payment = paymentRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new BusinessException("No payment with receipt number " + receiptNumber));
        return toPaymentDto(payment);
    }

    // ---------------- helpers ----------------

    /** Public access for dashboard aggregation. */
    public BigDecimal expectedFor(Student student, Term term) {
        return computeExpected(student, term);
    }

    private BigDecimal computeExpected(Student student, Term term) {
        Enrollment enrollment = enrollmentRepository.findByStudentAndAcademicYear(student, term.getAcademicYear())
                .orElse(null);
        if (enrollment != null) {
            var specific = feeStructureRepository.findByTermAndSchoolClass(term, enrollment.getSchoolClass());
            if (specific.isPresent()) {
                return specific.get().getAmount();
            }
        }
        return feeStructureRepository.findByTermAndSchoolClassIsNull(term)
                .map(FeeStructure::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + value + " (use CASH, BANK or MOBILE_MONEY)");
        }
    }

    private String nextReceiptNumber() {
        long next = paymentRepository.findMaxId() + 1;
        return String.format("RCP-%d-%06d", LocalDate.now().getYear(), next);
    }

    private StudentDtos.StudentDto studentBrief(Student s, Integer year) {
        StudentDtos.StudentDto dto = new StudentDtos.StudentDto();
        dto.setId(s.getId());
        dto.setAdmissionNumber(s.getAdmissionNumber());
        dto.setFullName(s.getFullName());
        dto.setStatus(s.getStatus().name());
        if (year != null) {
            var enrollment = enrollmentRepository.findByStudentAndAcademicYear(
                    s, yearRepository.findByYear(year).orElse(null));
            if (enrollment.isPresent()) {
                dto.setCurrentClassId(enrollment.get().getSchoolClass().getId());
                dto.setCurrentClassName(enrollment.get().getSchoolClass().getName());
                dto.setCurrentYear(year);
            }
        }
        return dto;
    }

    private FeeDtos.FeeStructureDto toFeeDto(FeeStructure f) {
        return FeeDtos.FeeStructureDto.builder()
                .id(f.getId())
                .termId(f.getTerm().getId())
                .year(f.getTerm().getAcademicYear().getYear())
                .termNumber(f.getTerm().getNumber().name())
                .classId(f.getSchoolClass() != null ? f.getSchoolClass().getId() : null)
                .className(f.getSchoolClass() != null ? f.getSchoolClass().getName() : null)
                .general(f.getSchoolClass() == null)
                .amount(f.getAmount())
                .build();
    }

    private FeeDtos.PaymentDto toPaymentDto(Payment p) {
        return FeeDtos.PaymentDto.builder()
                .id(p.getId())
                .receiptNumber(p.getReceiptNumber())
                .studentId(p.getStudent().getId())
                .studentName(p.getStudent().getFullName())
                .admissionNumber(p.getStudent().getAdmissionNumber())
                .termId(p.getTerm().getId())
                .termNumber(p.getTerm().getNumber().name())
                .year(p.getTerm().getAcademicYear().getYear())
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .method(p.getMethod().name())
                .note(p.getNote())
                .build();
    }
}
