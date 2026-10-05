package com.opportunity.school.repository;

import com.opportunity.school.model.Student;
import com.opportunity.school.model.enums.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByAdmissionNumber(String admissionNumber);

    /** Paginated search with optional name fragment and statuses. */
    @Query("SELECT s FROM Student s WHERE (:name IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:statuses IS NULL OR s.status IN :statuses) " +
           "AND (:classId IS NULL OR EXISTS (SELECT e FROM Enrollment e WHERE e.student = s AND e.schoolClass.id = :classId)) " +
           "AND (:year IS NULL OR EXISTS (SELECT e2 FROM Enrollment e2 WHERE e2.student = s AND e2.academicYear.year = :year)) " +
           "ORDER BY s.id")
    Page<Student> search(@Param("name") String name,
                         @Param("statuses") Collection<StudentStatus> statuses,
                         @Param("classId") Long classId,
                         @Param("year") Integer year,
                         Pageable pageable);

    long countByStatus(StudentStatus status);

    /** Students ACTIVE in a given class (via current enrollment). */
    @Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.schoolClass.id = :classId AND e.status = 'ACTIVE' ORDER BY e.student.fullName")
    List<Student> findActiveByClass(@Param("classId") Long classId);

    /** Students ACTIVE in any of the given classes. */
    @Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.schoolClass.id IN :classIds AND e.status = 'ACTIVE' ORDER BY e.student.fullName")
    List<Student> findActiveByClassIds(@Param("classIds") Collection<Long> classIds);

    /** Max numeric id, used for admission number generation. */
    @Query("SELECT COALESCE(MAX(s.id), 0) FROM Student s")
    long findMaxId();
}
