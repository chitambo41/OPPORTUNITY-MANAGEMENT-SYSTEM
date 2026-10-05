package com.opportunity.school.repository;

import com.opportunity.school.model.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    Optional<AcademicYear> findByYear(Integer year);

    Optional<AcademicYear> findByCurrentTrue();

    boolean existsByYear(Integer year);
}
