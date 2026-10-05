package com.opportunity.school.repository;

import com.opportunity.school.model.Exam;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Term;
import com.opportunity.school.model.enums.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findBySchoolClassAndTermOrderByIdDesc(SchoolClass schoolClass, Term term);

    List<Exam> findByTermOrderByIdDesc(Term term);

    List<Exam> findByStatus(ExamStatus status);

    Optional<Exam> findByIdAndSchoolClass(Long id, SchoolClass schoolClass);

    long countByStatus(ExamStatus status);
}
