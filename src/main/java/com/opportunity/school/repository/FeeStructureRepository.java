package com.opportunity.school.repository;

import com.opportunity.school.model.FeeStructure;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

    Optional<FeeStructure> findByTermAndSchoolClass(Term term, SchoolClass schoolClass);

    Optional<FeeStructure> findByTerm_IdAndSchoolClass_Id(Long termId, Long classId);

    List<FeeStructure> findByTerm(Term term);

    /** General (all-classes) fee for a term. */
    Optional<FeeStructure> findByTermAndSchoolClassIsNull(Term term);
}
