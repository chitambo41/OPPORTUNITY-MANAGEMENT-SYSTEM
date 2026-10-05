package com.opportunity.school.repository;

import com.opportunity.school.model.Mark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarkRepository extends JpaRepository<Mark, Long> {

    List<Mark> findByExamId(Long examId);

    List<Mark> findByExamIdAndSubjectId(Long examId, Long subjectId);

    Optional<Mark> findByExamIdAndStudentIdAndSubjectId(Long examId, Long studentId, Long subjectId);

    void deleteByExamId(Long examId);

    long countByExamId(Long examId);

    /** Distinct student ids having at least one mark in an exam. */
    @Query("SELECT DISTINCT m.student.id FROM Mark m WHERE m.exam.id = :examId")
    List<Long> findStudentIdsWithMarks(@Param("examId") Long examId);
}
