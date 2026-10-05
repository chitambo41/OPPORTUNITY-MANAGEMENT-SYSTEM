package com.opportunity.school.repository;

import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findByAcademicYearOrderByNumberAsc(AcademicYear academicYear);

    Optional<Term> findByAcademicYearAndNumber(AcademicYear academicYear, com.opportunity.school.model.enums.TermNumber number);

    Optional<Term> findByCurrentTrue();

    /** All terms whose date range overlaps the given range (for overlap validation). */
    @Query("SELECT t FROM Term t WHERE t.startDate <= :end AND t.endDate >= :start")
    List<Term> findOverlapping(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Terms that can contain a given date. */
    @Query("SELECT t FROM Term t WHERE t.startDate <= :date AND t.endDate >= :date")
    List<Term> findContaining(@Param("date") LocalDate date);
}
