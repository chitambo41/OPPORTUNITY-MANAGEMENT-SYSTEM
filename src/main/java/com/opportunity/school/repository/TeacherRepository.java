package com.opportunity.school.repository;

import com.opportunity.school.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findByActiveTrueOrderByIdAsc();

    List<Teacher> findByActiveFalseOrderByIdAsc();

    List<Teacher> findByActiveTrueAndFullNameContainingIgnoreCaseOrderByIdAsc(String fragment);

    Optional<Teacher> findByUser_EmailIgnoreCase(String email);

    long countByActiveTrue();
}
