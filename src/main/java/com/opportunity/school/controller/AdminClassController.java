package com.opportunity.school.controller;

import com.opportunity.school.dto.SubjectClassDtos;
import com.opportunity.school.service.SchoolClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
public class AdminClassController {

    private final SchoolClassService classService;

    @PostMapping
    public SubjectClassDtos.ClassDto create(@Valid @RequestBody SubjectClassDtos.CreateClassRequest request) {
        return classService.create(request);
    }

    @GetMapping
    public List<SubjectClassDtos.ClassDto> list(@RequestParam(required = false) Integer year) {
        return classService.listByYear(year);
    }

    @GetMapping("/{id}")
    public SubjectClassDtos.ClassDto get(@PathVariable Long id) {
        return classService.get(id);
    }

    @PostMapping("/{id}/class-teacher")
    public SubjectClassDtos.ClassDto assignClassTeacher(@PathVariable Long id,
                                                        @Valid @RequestBody SubjectClassDtos.AssignClassTeacherRequest request) {
        return classService.assignClassTeacher(id, request);
    }

    @PostMapping("/{id}/subjects")
    public SubjectClassDtos.ClassDto addSubject(@PathVariable Long id,
                                                @Valid @RequestBody SubjectClassDtos.AddClassSubjectRequest request) {
        return classService.addSubject(id, request);
    }

    @DeleteMapping("/{id}/subjects/{classSubjectId}")
    public SubjectClassDtos.ClassDto removeSubject(@PathVariable Long id,
                                                   @PathVariable Long classSubjectId) {
        return classService.removeSubject(id, classSubjectId);
    }
}
