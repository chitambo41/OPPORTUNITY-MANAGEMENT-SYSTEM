package com.opportunity.school.repository;

import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.enums.ClassLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findByAcademicYearOrderByIdAsc(AcademicYear academicYear);

    List<SchoolClass> findByAcademicYearYearOrderByLevelAscIdAsc(Integer year);

    List<SchoolClass> findByAcademicYearAndLevel(AcademicYear academicYear, ClassLevel level);

    Optional<SchoolClass> findByAcademicYearAndLevelAndNameIgnoreCase(AcademicYear academicYear, ClassLevel level, String name);

    long countByAcademicYear(AcademicYear academicYear);

    List<SchoolClass> findByAcademicYear(AcademicYear academicYear);

    /** Classes where the teacher is class teacher (any year). */
    List<SchoolClass> findByClassTeacher(Teacher teacher);

    /** Classes where the teacher is class teacher. */
    Optional<SchoolClass> findByClassTeacherAndAcademicYear(Teacher teacher, AcademicYear academicYear);

    /** Classes where the teacher leads OR teaches any subject. */
    @Query("SELECT DISTINCT c FROM SchoolClass c LEFT JOIN c.classSubjects cs " +
           "WHERE c.academicYear = :year AND (c.classTeacher = :teacher OR cs.teacher = :teacher)")
    List<SchoolClass> findClassesForTeacher(@Param("teacher") Teacher teacher, @Param("year") AcademicYear academicYear);
}
