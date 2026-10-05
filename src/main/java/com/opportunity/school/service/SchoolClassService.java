package com.opportunity.school.service;

import com.opportunity.school.dto.SubjectClassDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.ClassSubject;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Subject;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.enums.ClassLevel;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolClassService {

    private final SchoolClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;

    // ---------------- Classes ----------------

    @Transactional
    public SubjectClassDtos.ClassDto create(SubjectClassDtos.CreateClassRequest req) {
        AcademicYear year = yearRepository.findById(req.getAcademicYearId())
                .orElseThrow(() -> NotFoundException.entity("Academic year", req.getAcademicYearId()));

        ClassLevel level = parseLevel(req.getLevel());

        // One class per level per year (name uniqueness enforced separately per level)
        boolean levelExists = !classRepository.findByAcademicYearAndLevel(year, level).isEmpty();
        if (levelExists) {
            throw new BusinessException("A " + level + " class already exists for year " + year.getYear());
        }

        SchoolClass schoolClass = SchoolClass.builder()
                .level(level)
                .name(req.getName().trim())
                .academicYear(year)
                .build();
        return toDto(classRepository.save(schoolClass));
    }

    @Transactional
    public SubjectClassDtos.ClassDto assignClassTeacher(Long classId, SubjectClassDtos.AssignClassTeacherRequest req) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));

        Teacher teacher = teacherRepository.findById(req.getTeacherId())
                .orElseThrow(() -> NotFoundException.entity("Teacher", req.getTeacherId()));
        if (!teacher.isActive()) {
            throw new BusinessException("Only active teachers can be assigned as class teacher");
        }

        // One class per teacher per year
        classRepository.findByClassTeacherAndAcademicYear(teacher, schoolClass.getAcademicYear())
                .filter(existing -> !existing.getId().equals(classId))
                .ifPresent(existing -> {
                    throw new BusinessException(teacher.getFullName() + " is already class teacher of \"" +
                            existing.getName() + "\" for " + schoolClass.getAcademicYear().getYear());
                });

        schoolClass.setClassTeacher(teacher);
        return toDto(classRepository.save(schoolClass));
    }

    @Transactional(readOnly = true)
    public List<SubjectClassDtos.ClassDto> listByYear(Integer year) {
        List<SchoolClass> classes = (year == null)
                ? classRepository.findAll().stream().toList()
                : classRepository.findByAcademicYearYearOrderByLevelAscIdAsc(year);
        return classes.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SubjectClassDtos.ClassDto get(Long classId) {
        return toDto(classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId)));
    }

    // ---------------- Class subjects ----------------

    @Transactional
    public SubjectClassDtos.ClassDto addSubject(Long classId, SubjectClassDtos.AddClassSubjectRequest req) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> NotFoundException.entity("Subject", req.getSubjectId()));
        Teacher teacher = teacherRepository.findById(req.getTeacherId())
                .orElseThrow(() -> NotFoundException.entity("Teacher", req.getTeacherId()));
        if (!teacher.isActive()) {
            throw new BusinessException("Only active teachers can teach a subject");
        }
        if (classSubjectRepository.existsBySchoolClassAndSubject(schoolClass, subject)) {
            throw new BusinessException("Subject \"" + subject.getName() + "\" is already added to this class");
        }
        classSubjectRepository.save(ClassSubject.builder()
                .schoolClass(schoolClass)
                .subject(subject)
                .teacher(teacher)
                .build());
        return toDto(schoolClass);
    }

    @Transactional
    public SubjectClassDtos.ClassDto removeSubject(Long classId, Long classSubjectId) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> NotFoundException.entity("Class", classId));
        ClassSubject cs = classSubjectRepository.findById(classSubjectId)
                .orElseThrow(() -> NotFoundException.entity("Class subject", classSubjectId));
        if (!cs.getSchoolClass().getId().equals(classId)) {
            throw new BusinessException("Class subject does not belong to this class");
        }
        classSubjectRepository.delete(cs);
        return toDto(schoolClass);
    }

    // ---------------- Helpers ----------------

    private ClassLevel parseLevel(String value) {
        try {
            return ClassLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid class level: " + value + " (use BABY_CLASS, KG1, KG2 or KG3)");
        }
    }

    private SubjectClassDtos.ClassDto toDto(SchoolClass c) {
        List<SubjectClassDtos.ClassSubjectDto> subjects = classSubjectRepository.findBySchoolClassOrderByIdAsc(c).stream()
                .map(cs -> new SubjectClassDtos.ClassSubjectDto(
                        cs.getId(),
                        cs.getSubject().getId(), cs.getSubject().getName(),
                        cs.getTeacher() != null ? cs.getTeacher().getId() : null,
                        cs.getTeacher() != null ? cs.getTeacher().getFullName() : null))
                .toList();
        long activeStudents = enrollmentRepository.countBySchoolClassAndStatus(c, StudentStatus.ACTIVE);
        return new SubjectClassDtos.ClassDto(
                c.getId(), c.getLevel().name(), c.getName(),
                c.getAcademicYear().getId(), c.getAcademicYear().getYear(),
                c.getClassTeacher() != null ? c.getClassTeacher().getId() : null,
                c.getClassTeacher() != null ? c.getClassTeacher().getFullName() : null,
                activeStudents, subjects);
    }
}
