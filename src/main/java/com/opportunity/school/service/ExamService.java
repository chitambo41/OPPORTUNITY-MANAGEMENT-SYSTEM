package com.opportunity.school.service;

import com.opportunity.school.dto.ExamDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.ExamStatus;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import com.opportunity.school.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final SchoolClassRepository classRepository;
    private final TermRepository termRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final ResultSubmissionRepository submissionRepository;
    private final SecurityUtils securityUtils;

    // ---------------- Admin: exam CRUD ----------------

    @Transactional
    public ExamDtos.ExamDto create(ExamDtos.CreateExamRequest req) {
        SchoolClass schoolClass = classRepository.findById(req.getClassId())
                .orElseThrow(() -> NotFoundException.entity("Class", req.getClassId()));
        Term term = termRepository.findById(req.getTermId())
                .orElseThrow(() -> NotFoundException.entity("Term", req.getTermId()));

        Exam exam = Exam.builder()
                .name(req.getName().trim())
                .schoolClass(schoolClass)
                .term(term)
                .maxMarks(req.getMaxMarks())
                .status(ExamStatus.DRAFT)
                .notes(req.getNotes())
                .build();
        return toDto(examRepository.save(exam));
    }

    @Transactional
    public ExamDtos.ExamDto updateStatus(Long examId, ExamDtos.UpdateExamStatusRequest req) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));
        ExamStatus target;
        try {
            target = ExamStatus.valueOf(req.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid exam status: " + req.getStatus());
        }

        // Allowed transitions (admin may open DRAFT exams and close OPEN ones early)
        boolean valid = switch (target) {
            case OPEN -> exam.getStatus() == ExamStatus.DRAFT;
            case DRAFT -> exam.getStatus() == ExamStatus.OPEN && markRepository.countByExamId(examId) == 0;
            case SUBMITTED, APPROVED, RETURNED -> false; // via dedicated workflow endpoints
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("Cannot change exam status from " + exam.getStatus() + " to " + target);
        }
        exam.setStatus(target);
        return toDto(examRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.ExamDto> list(Long termId, Long classId) {
        List<Exam> exams;
        if (termId != null && classId != null) {
            exams = examRepository.findBySchoolClassAndTermOrderByIdDesc(
                    classRepository.findById(classId).orElseThrow(() -> NotFoundException.entity("Class", classId)),
                    termRepository.findById(termId).orElseThrow(() -> NotFoundException.entity("Term", termId)));
        } else if (termId != null) {
            exams = examRepository.findByTermOrderByIdDesc(
                    termRepository.findById(termId).orElseThrow(() -> NotFoundException.entity("Term", termId)));
        } else {
            exams = examRepository.findAll().stream()
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                    .toList();
        }
        return exams.stream().map(this::toDto).toList();
    }

    // ---------------- Teacher: marks ----------------

    /**
     * Teacher saves marks for ONE subject they are assigned to (check ClassSubject).
     * Only when the exam is OPEN and only for ACTIVE students of the class.
     */
    @Transactional
    public void saveMarks(ExamDtos.SaveMarksRequest req) {
        Teacher teacher = securityUtils.currentTeacher();
        Exam exam = examRepository.findById(req.getExamId())
                .orElseThrow(() -> NotFoundException.entity("Exam", req.getExamId()));

        if (exam.getStatus() != ExamStatus.OPEN) {
            throw new BusinessException("Marks can only be entered while the exam is OPEN (current: " + exam.getStatus() + ")");
        }

        // Teacher must be assigned to this subject in this class
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> NotFoundException.entity("Subject", req.getSubjectId()));
        ClassSubject cs = classSubjectRepository
                .findBySchoolClassAndSubject(exam.getSchoolClass(), subject)
                .orElseThrow(() -> new BusinessException("This subject is not taught in the exam's class"));
        if (cs.getTeacher() == null || !cs.getTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException("You are not assigned to teach " + subject.getName() + " in this class");
        }

        // Only ACTIVE students of the class
        Set<Long> activeIds = activeStudentIds(exam.getSchoolClass());
        for (ExamDtos.SaveMarksRequest.MarkEntry entry : req.getMarks()) {
            if (!activeIds.contains(entry.getStudentId())) {
                throw new BusinessException("Student " + entry.getStudentId() + " is not ACTIVE in this class");
            }
            if (entry.getScore() < 0 || entry.getScore() > exam.getMaxMarks()) {
                throw new BusinessException("Score must be between 0 and " + exam.getMaxMarks());
            }
            Mark mark = markRepository.findByExamIdAndStudentIdAndSubjectId(exam.getId(), entry.getStudentId(), subject.getId())
                    .orElseGet(() -> Mark.builder()
                            .exam(exam)
                            .student(studentRepository.getReferenceById(entry.getStudentId()))
                            .subject(subject)
                            .build());
            mark.setScore(entry.getScore());
            mark.setComment(entry.getComment());
            markRepository.save(mark);
        }
    }

    /** Progress of missing marks per subject for an exam. */
    @Transactional(readOnly = true)
    public List<ExamDtos.StudentProgressDto> progress(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));
        int activeCount = activeStudentIds(exam.getSchoolClass()).size();
        List<ExamDtos.StudentProgressDto> rows = new ArrayList<>();
        for (ClassSubject cs : classSubjectRepository.findBySchoolClassOrderByIdAsc(exam.getSchoolClass())) {
            long withMarks = markRepository.findByExamId(examId).stream()
                    .filter(m -> m.getSubject().getId().equals(cs.getSubject().getId()))
                    .map(Mark::getStudent)
                    .map(Student::getId)
                    .distinct()
                    .count();
            rows.add(ExamDtos.StudentProgressDto.builder()
                    .examId(examId)
                    .subjectId(cs.getSubject().getId())
                    .subjectName(cs.getSubject().getName())
                    .studentsWithMarks((int) withMarks)
                    .activeStudents(activeCount)
                    .missing((int) (activeCount - withMarks))
                    .complete(withMarks >= activeCount && activeCount > 0)
                    .build());
        }
        return rows;
    }

    /**
     * Class teacher sends results to admin: allowed only when every subject has marks
     * for every ACTIVE student. Locks marks (exam status SUBMITTED).
     */
    @Transactional
    public ExamDtos.ExamDto sendResults(Long examId, ExamDtos.SendResultsRequest req) {
        Teacher teacher = securityUtils.currentTeacher();
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));

        if (exam.getSchoolClass().getClassTeacher() == null ||
                !exam.getSchoolClass().getClassTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException("Only the class teacher can send results");
        }
        if (exam.getStatus() != ExamStatus.OPEN && exam.getStatus() != ExamStatus.RETURNED) {
            throw new BusinessException("Results can only be sent while marks are editable (OPEN or RETURNED)");
        }

        // Every class subject must have marks for every ACTIVE student
        Set<Long> activeIds = activeStudentIds(exam.getSchoolClass());
        if (activeIds.isEmpty()) {
            throw new BusinessException("Class has no ACTIVE students");
        }
        List<ClassSubject> subjects = classSubjectRepository.findBySchoolClassOrderByIdAsc(exam.getSchoolClass());
        if (subjects.isEmpty()) {
            throw new BusinessException("Class has no subjects configured");
        }
        for (ClassSubject cs : subjects) {
            Set<Long> withMarks = new HashSet<>();
            for (Mark m : markRepository.findByExamId(examId)) {
                if (m.getSubject().getId().equals(cs.getSubject().getId())) {
                    withMarks.add(m.getStudent().getId());
                }
            }
            for (Long id : activeIds) {
                if (!withMarks.contains(id)) {
                    throw new BusinessException("Missing marks: " + cs.getSubject().getName() +
                            " for student ID " + id + ". All subjects need marks for all active students.");
                }
            }
        }

        exam.setStatus(ExamStatus.SUBMITTED);
        examRepository.save(exam);

        ResultSubmission submission = submissionRepository.findByExamId(examId)
                .orElseGet(() -> ResultSubmission.builder()
                        .exam(exam)
                        .submittedAt(LocalDateTime.now())
                        .build());
        submission.setStatus(ResultSubmission.ResultStatus.PENDING);
        submission.setSubmittedBy(teacher);
        submission.setTeacherComment(req != null ? req.getTeacherComment() : null);
        submission.setAdminComment(null);
        submission.setReviewedAt(null);
        submissionRepository.save(submission);
        return toDto(exam);
    }

    /**
     * Admin: approve a PENDING submission.
     */
    @Transactional
    public ExamDtos.ExamDto approve(Long examId, String comment) {
        ResultSubmission submission = submissionRepository.findByExamId(examId)
                .orElseThrow(() -> new BusinessException("No submission found for this exam"));
        Exam exam = submission.getExam();
        if (submission.getStatus() != ResultSubmission.ResultStatus.PENDING) {
            throw new BusinessException("Only PENDING submissions can be approved");
        }
        submission.setStatus(ResultSubmission.ResultStatus.APPROVED);
        submission.setAdminComment(comment);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        exam.setStatus(ExamStatus.APPROVED);
        return toDto(examRepository.save(exam));
    }

    /**
     * Admin: return a PENDING submission with a mandatory comment; marks become editable again.
     */
    @Transactional
    public ExamDtos.ExamDto returnToTeacher(Long examId, ExamDtos.ReturnResultsRequest req) {
        ResultSubmission submission = submissionRepository.findByExamId(examId)
                .orElseThrow(() -> new BusinessException("No submission found for this exam"));
        Exam exam = submission.getExam();
        if (submission.getStatus() != ResultSubmission.ResultStatus.PENDING) {
            throw new BusinessException("Only PENDING submissions can be returned");
        }
        submission.setStatus(ResultSubmission.ResultStatus.RETURNED);
        submission.setAdminComment(req.getComment());
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        exam.setStatus(ExamStatus.RETURNED);
        return toDto(examRepository.save(exam));
    }

    /** Full students x subjects results table for one exam. */
    @Transactional(readOnly = true)
    public ExamDtos.ResultsTableDto resultsTable(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));

        List<Student> students = enrollmentRepository
                .findBySchoolClassAndStatus(exam.getSchoolClass(), StudentStatus.ACTIVE).stream()
                .map(Enrollment::getStudent)
                .sorted(java.util.Comparator.comparing(Student::getFullName))
                .toList();
        List<ClassSubject> subjects = classSubjectRepository.findBySchoolClassOrderByIdAsc(exam.getSchoolClass());

        ExamDtos.ResultsTableDto dto = new ExamDtos.ResultsTableDto();
        dto.setExamId(examId);
        dto.setStudentIds(students.stream().map(Student::getId).toList());
        dto.setStudentNames(students.stream().map(Student::getFullName).toList());
        dto.setAdmissionNumbers(students.stream().map(Student::getAdmissionNumber).toList());
        dto.setSubjectIds(subjects.stream().map(cs -> cs.getSubject().getId()).toList());
        dto.setSubjectNames(subjects.stream().map(cs -> cs.getSubject().getName()).toList());
        dto.setMaxMarks(subjects.stream().map(cs -> exam.getMaxMarks()).toList());

        List<ExamDtos.MarkCell> cells = new ArrayList<>();
        for (Student s : students) {
            for (ClassSubject cs : subjects) {
                cells.add(markRepository
                        .findByExamIdAndStudentIdAndSubjectId(examId, s.getId(), cs.getSubject().getId())
                        .map(m -> ExamDtos.MarkCell.builder()
                                .studentId(s.getId()).subjectId(cs.getSubject().getId())
                                .score(m.getScore()).comment(m.getComment()).build())
                        .orElse(ExamDtos.MarkCell.builder()
                                .studentId(s.getId()).subjectId(cs.getSubject().getId())
                                .build()));
            }
        }
        dto.setCells(cells);
        return dto;
    }

    /** Exams relevant to a teacher: classes they lead or teach a subject in (optionally filtered by term). */
    @Transactional(readOnly = true)
    public List<ExamDtos.ExamDto> listForTeacher(Teacher teacher, Long termId) {
        List<Exam> exams = new ArrayList<>();
        for (Exam exam : (termId != null)
                ? examRepository.findByTermOrderByIdDesc(termRepository.findById(termId)
                        .orElseThrow(() -> NotFoundException.entity("Term", termId)))
                : examRepository.findAll().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList()) {
            SchoolClass c = exam.getSchoolClass();
            boolean leads = c.getClassTeacher() != null && c.getClassTeacher().getId().equals(teacher.getId());
            boolean teaches = classSubjectRepository.findBySchoolClassOrderByIdAsc(c).stream()
                    .anyMatch(cs -> cs.getTeacher() != null && cs.getTeacher().getId().equals(teacher.getId()));
            if (leads || teaches) {
                exams.add(exam);
            }
        }
        return exams.stream().map(this::toDto).toList();
    }

    /** Results table with an ownership check for the teacher's own classes. */
    @Transactional(readOnly = true)
    public ExamDtos.ResultsTableDto resultsTableForTeacher(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.entity("Exam", examId));
        Teacher teacher = securityUtils.currentTeacher();
        SchoolClass c = exam.getSchoolClass();
        boolean leads = c.getClassTeacher() != null && c.getClassTeacher().getId().equals(teacher.getId());
        boolean teaches = classSubjectRepository.findBySchoolClassOrderByIdAsc(c).stream()
                .anyMatch(cs -> cs.getTeacher() != null && cs.getTeacher().getId().equals(teacher.getId()));
        if (!leads && !teaches) {
            throw new BusinessException("You do not have access to this exam's results");
        }
        return resultsTable(examId);
    }

    // ---------------- helpers ----------------

    private Set<Long> activeStudentIds(SchoolClass schoolClass) {
        Set<Long> ids = new HashSet<>();
        for (Enrollment e : enrollmentRepository.findBySchoolClassAndStatus(schoolClass, StudentStatus.ACTIVE)) {
            ids.add(e.getStudent().getId());
        }
        return ids;
    }

    private ExamDtos.ExamDto toDto(Exam exam) {
        ResultSubmission submission = submissionRepository.findByExamId(exam.getId()).orElse(null);
        return ExamDtos.ExamDto.builder()
                .id(exam.getId())
                .name(exam.getName())
                .classId(exam.getSchoolClass().getId())
                .className(exam.getSchoolClass().getName())
                .level(exam.getSchoolClass().getLevel().name())
                .termId(exam.getTerm().getId())
                .termNumber(exam.getTerm().getNumber().name())
                .year(exam.getTerm().getAcademicYear().getYear())
                .maxMarks(exam.getMaxMarks())
                .status(exam.getStatus().name())
                .notes(exam.getNotes())
                .submissionId(submission != null ? submission.getId() : null)
                .submissionStatus(submission != null ? submission.getStatus().name() : null)
                .adminComment(submission != null ? submission.getAdminComment() : null)
                .teacherComment(submission != null ? submission.getTeacherComment() : null)
                .build();
    }
}
