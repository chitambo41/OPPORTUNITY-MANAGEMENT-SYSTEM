package com.opportunity.school.controller;

import com.opportunity.school.dto.SubjectClassDtos;
import com.opportunity.school.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public SubjectClassDtos.SubjectDto create(@Valid @RequestBody SubjectClassDtos.SubjectRequest request) {
        return subjectService.create(request);
    }

    @GetMapping
    public List<SubjectClassDtos.SubjectDto> list() {
        return subjectService.list();
    }

    @PutMapping("/{id}")
    public SubjectClassDtos.SubjectDto update(@PathVariable Long id,
                                              @Valid @RequestBody SubjectClassDtos.SubjectRequest request) {
        return subjectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Subject deleted"));
    }
}
