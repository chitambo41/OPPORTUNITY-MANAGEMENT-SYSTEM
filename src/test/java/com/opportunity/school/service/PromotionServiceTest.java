package com.opportunity.school.service;

import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.ClassLevel;
import com.opportunity.school.model.enums.StudentStatus;
import com.opportunity.school.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PromotionServiceTest {

    @Autowired private PromotionService promotionService;
    @Autowired private AcademicYearRepository yearRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private AcademicYear y2026;
    private AcademicYear y2027;
    private SchoolClass baby26, kg1_26, kg2_26, kg3_26;

    @BeforeEach
    void setUp() {
        y2026 = yearRepository.save(AcademicYear.builder().year(2026).current(true).build());
        y2027 = yearRepository.save(AcademicYear.builder().year(2027).build());

        baby26 = classRepository.save(SchoolClass.builder().level(ClassLevel.BABY_CLASS).name("Baby 2026").academicYear(y2026).build());
        kg1_26 = classRepository.save(SchoolClass.builder().level(ClassLevel.KG1).name("KG1 2026").academicYear(y2026).build());
        kg2_26 = classRepository.save(SchoolClass.builder().level(ClassLevel.KG2).name("KG2 2026").academicYear(y2026).build());
        kg3_26 = classRepository.save(SchoolClass.builder().level(ClassLevel.KG3).name("KG3 2026").academicYear(y2026).build());
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Student admit(String name, SchoolClass cls) {
        Student s = studentRepository.save(Student.builder()
                .admissionNumber("ON-T-" + SEQ.incrementAndGet())
                .fullName(name)
                .status(StudentStatus.ACTIVE)
                .build());
        enrollmentRepository.save(Enrollment.builder()
                .student(s).schoolClass(cls).academicYear(y2026)
                .status(StudentStatus.ACTIVE)
                .joinedOn(LocalDate.now())
                .build());
        return s;
    }

    private void place(Student s, SchoolClass cls, AcademicYear year, StudentStatus status) {
        enrollmentRepository.save(Enrollment.builder()
                .student(s).schoolClass(cls).academicYear(year)
                .status(status)
                .joinedOn(LocalDate.now())
                .build());
    }

    @Test
    void classLevelOrderAndNext() {
        assertThat(ClassLevel.BABY_CLASS.next()).isEqualTo(ClassLevel.KG1);
        assertThat(ClassLevel.KG1.next()).isEqualTo(ClassLevel.KG2);
        assertThat(ClassLevel.KG2.next()).isEqualTo(ClassLevel.KG3);
        assertThat(ClassLevel.KG3.next()).isNull();
    }

    @Test
    void promotesEachLevelAndCreatesMissingClasses() {
        Student baby = admit("Baby Student", baby26);
        Student k1 = admit("Kg1 Student", kg1_26);
        Student k2 = admit("Kg2 Student", kg2_26);
        Student k3 = admit("Kg3 Student", kg3_26);

        assertThat(classRepository.findByAcademicYear(y2027)).isEmpty();

        var result = promotionService.promote(2027);

        assertThat(result.getPromotedCount()).isEqualTo(3);
        assertThat(result.getCompletedCount()).isEqualTo(1);
        assertThat(result.getCreatedClasses()).isEqualTo(4);

        // Baby -> KG1 class in 2027
        assertThat(classRepository.findByAcademicYearAndLevel(y2027, ClassLevel.KG1))
                .hasSize(1);
        assertThat(enrollmentRepository.findByStudentAndAcademicYear(baby, y2027))
                .isPresent()
                .get()
                .satisfies(e -> {
                    assertThat(e.getSchoolClass().getLevel()).isEqualTo(ClassLevel.KG1);
                    assertThat(e.getStatus()).isEqualTo(StudentStatus.ACTIVE);
                });
        assertThat(baby.getStatus()).isEqualTo(StudentStatus.ACTIVE);

        // KG3 student becomes COMPLETE
        assertThat(studentRepository.findById(k3.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.COMPLETE);

        // Old enrollments completed
        assertThat(enrollmentRepository.findByStudentAndAcademicYear(k3, y2026).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.COMPLETE);

        // keep references used
        assertThat(k1.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(k2.getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void skipsStudentsAlreadyPlacedInNewYear() {
        Student manual = admit("Manual Placement", baby26);
        SchoolClass kg1_27 = classRepository.save(SchoolClass.builder()
                .level(ClassLevel.KG1).name("KG1 2027 manual").academicYear(y2027).build());
        place(manual, kg1_27, y2027, StudentStatus.ACTIVE);

        var result = promotionService.promote(2027);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getPromotedCount()).isEqualTo(0);
        // Their 2027 enrollment still points to the manually chosen class
        assertThat(enrollmentRepository.findByStudentAndAcademicYear(manual, y2027).orElseThrow()
                .getSchoolClass().getId()).isEqualTo(kg1_27.getId());
    }

    @Test
    void neverPromotesRemovedStudents() {
        Student removed = admit("Removed Student", kg1_26);
        removed.setStatus(StudentStatus.REMOVED);
        studentRepository.save(removed);
        var enr = enrollmentRepository.findByStudentAndAcademicYear(removed, y2026).orElseThrow();
        enr.setStatus(StudentStatus.REMOVED);
        enrollmentRepository.save(enr);

        var result = promotionService.promote(2027);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getPromotedCount()).isEqualTo(0);
        assertThat(enrollmentRepository.findByStudentAndAcademicYear(removed, y2027)).isEmpty();
        assertThat(studentRepository.findById(removed.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.REMOVED);
    }

    @Test
    void previewDoesNotPersistAnything() {
        admit("Preview Student", baby26);

        var preview = promotionService.preview(2027);
        assertThat(preview.getPromotedCount()).isEqualTo(1);

        // Nothing persisted
        assertThat(classRepository.findByAcademicYear(y2027)).isEmpty();
        assertThat(enrollmentRepository.findByAcademicYear(y2027)).isEmpty();

        // Real run still works after a preview
        var result = promotionService.promote(2027);
        assertThat(result.getPromotedCount()).isEqualTo(1);
        assertThat(classRepository.findByAcademicYear(y2027)).isNotEmpty();
    }

    @Test
    void failsWhenSourceYearDoesNotExist() {
        assertThatThrownBy(() -> promotionService.promote(2099))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist");
    }
}
