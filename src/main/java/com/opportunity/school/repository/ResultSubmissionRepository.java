package com.opportunity.school.repository;

import com.opportunity.school.model.ResultSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultSubmissionRepository extends JpaRepository<ResultSubmission, Long> {

    Optional<ResultSubmission> findByExamId(Long examId);

    long countByStatus(ResultSubmission.ResultStatus status);
}
