package com.opportunity.school.repository;

import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.Enrollment;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Student;
import com.opportunity.school.model.enums.ClassLevel;
import com.opportunity.school.model.enums.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsBySchoolClass_Id(Long classId);

    List<Enrollment> findByStudentOrderByAcademicYearYearAsc(Student student);

    Optional<Enrollment> findByStudentAndAcademicYear(Student student, AcademicYear academicYear);

    Optional<Enrollment> findByStudentAndSchoolClass(Student student, SchoolClass schoolClass);

    List<Enrollment> findBySchoolClassAndStatus(SchoolClass schoolClass, StudentStatus status);

    /** For promotion: all enrollments of a year grouped by the class level. */
    List<Enrollment> findByAcademicYear(AcademicYear academicYear);

    /** Count ACTIVE enrollments per class. */
    long countBySchoolClassAndStatus(SchoolClass schoolClass, StudentStatus status);

    /** Enrollments in classes of a level for a year (used by promotion). */
    List<Enrollment> findByAcademicYearAndSchoolClass_Level(AcademicYear academicYear, ClassLevel level);
}
