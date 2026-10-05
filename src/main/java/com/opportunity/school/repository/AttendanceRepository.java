package com.opportunity.school.repository;

import com.opportunity.school.model.Attendance;
import com.opportunity.school.model.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStudentIdAndDate(Long studentId, LocalDate date);

    List<Attendance> findByDate(LocalDate date);

    List<Attendance> findByStudentIdOrderByDateDesc(Long studentId);

    @Query("SELECT a FROM Attendance a JOIN a.student s JOIN Enrollment e ON e.student = s " +
           "WHERE e.schoolClass.id = :classId AND a.date BETWEEN :start AND :end")
    List<Attendance> findByClassAndDateRange(@Param("classId") Long classId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    /** Counts per status for one class on one date. */
    @Query("SELECT a.status, COUNT(a) FROM Attendance a JOIN a.student s " +
           "JOIN Enrollment e ON e.student = s AND e.schoolClass.id = :classId " +
           "WHERE a.date = :date GROUP BY a.status")
    List<Object[]> countByClassAndDate(@Param("classId") Long classId, @Param("date") LocalDate date);

    /** Counts per status for one student in a range. */
    @Query("SELECT a.status, COUNT(a) FROM Attendance a WHERE a.student.id = :studentId " +
           "AND a.date BETWEEN :start AND :end GROUP BY a.status")
    List<Object[]> countByStudentInRange(@Param("studentId") Long studentId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    boolean existsByStudentIdAndDate(Long studentId, LocalDate date);

    long countByStudentIdAndStatus(Long studentId, AttendanceStatus status);

    long countByDateAndStatus(LocalDate date, AttendanceStatus status);
}
