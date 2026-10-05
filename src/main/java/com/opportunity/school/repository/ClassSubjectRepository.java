package com.opportunity.school.repository;

import com.opportunity.school.model.ClassSubject;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Subject;
import com.opportunity.school.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

    List<ClassSubject> findBySchoolClassOrderByIdAsc(SchoolClass schoolClass);

    List<ClassSubject> findByTeacher(Teacher teacher);

    Optional<ClassSubject> findBySchoolClassAndSubject(SchoolClass schoolClass, Subject subject);

    boolean existsBySchoolClassAndSubject(SchoolClass schoolClass, Subject subject);

    long countBySchoolClass(SchoolClass schoolClass);

    long countByTeacher(Teacher teacher);

    boolean existsBySubject_Id(Long subjectId);

    boolean existsByTeacher(Teacher teacher);
}
