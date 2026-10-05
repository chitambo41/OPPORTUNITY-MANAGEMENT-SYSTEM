package com.opportunity.school.service;

import com.opportunity.school.dto.SubjectClassDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.Subject;
import com.opportunity.school.repository.ClassSubjectRepository;
import com.opportunity.school.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;

    @Transactional
    public SubjectClassDtos.SubjectDto create(SubjectClassDtos.SubjectRequest req) {
        if (subjectRepository.existsByNameIgnoreCase(req.getName().trim())) {
            throw new BusinessException("Subject \"" + req.getName() + "\" already exists");
        }
        Subject subject = Subject.builder()
                .name(req.getName().trim())
                .description(req.getDescription())
                .build();
        return toDto(subjectRepository.save(subject));
    }

    @Transactional
    public SubjectClassDtos.SubjectDto update(Long id, SubjectClassDtos.SubjectRequest req) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Subject", id));
        subjectRepository.findByNameIgnoreCase(req.getName().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException("Subject \"" + req.getName() + "\" already exists");
                });
        subject.setName(req.getName().trim());
        subject.setDescription(req.getDescription());
        return toDto(subjectRepository.save(subject));
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Subject", id));
        if (classSubjectRepository.existsBySubject_Id(id)) {
            throw new BusinessException("Cannot delete: subject is used in at least one class. Remove it from classes first.");
        }
        subjectRepository.delete(subject);
    }

    public List<SubjectClassDtos.SubjectDto> list() {
        return subjectRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    private SubjectClassDtos.SubjectDto toDto(Subject s) {
        return new SubjectClassDtos.SubjectDto(s.getId(), s.getName(), s.getDescription());
    }
}
