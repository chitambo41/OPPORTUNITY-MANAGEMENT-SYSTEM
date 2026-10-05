package com.opportunity.school.controller;

import com.opportunity.school.dto.TeacherDtos;
import com.opportunity.school.service.TeacherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/teachers")
@RequiredArgsConstructor
public class AdminTeacherController {

    private final TeacherService teacherService;

    @PostMapping
    public TeacherDtos.TeacherDto create(@Valid @RequestBody TeacherDtos.CreateTeacherRequest request) {
        return teacherService.create(request);
    }

    @GetMapping
    public Page<TeacherDtos.TeacherDto> list(@RequestParam(defaultValue = "false") boolean removed,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return teacherService.list(removed, search,
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100)));
    }

    @PutMapping("/{id}")
    public TeacherDtos.TeacherDto update(@PathVariable Long id,
                                         @Valid @RequestBody TeacherDtos.UpdateTeacherRequest request) {
        return teacherService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable Long id,
                                                      @Valid @RequestBody TeacherDtos.RemoveTeacherRequest request) {
        teacherService.remove(id, request);
        return ResponseEntity.ok(Map.of("message", "Teacher removed"));
    }
}
