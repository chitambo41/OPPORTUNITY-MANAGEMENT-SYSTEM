package com.opportunity.school.controller;

import com.opportunity.school.dto.StudentDtos;
import com.opportunity.school.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class AdminStudentController {

    private final StudentService studentService;

    @PostMapping
    public StudentDtos.StudentDto create(@Valid @RequestBody StudentDtos.CreateStudentRequest request) {
        return studentService.create(request);
    }

    @GetMapping
    public Page<StudentDtos.StudentDto> search(@RequestParam(required = false) String name,
                                               @RequestParam(required = false) Long classId,
                                               @RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return studentService.search(name, classId, year, status, page, size);
    }

    @GetMapping("/{id}")
    public StudentDtos.StudentProfileDto profile(@PathVariable Long id) {
        return studentService.profile(id);
    }

    @PutMapping("/{id}")
    public StudentDtos.StudentDto update(@PathVariable Long id,
                                         @Valid @RequestBody StudentDtos.UpdateStudentRequest request) {
        return studentService.update(id, request);
    }

    @PostMapping("/{id}/move")
    public StudentDtos.StudentDto move(@PathVariable Long id,
                                       @Valid @RequestBody StudentDtos.MoveStudentRequest request) {
        return studentService.move(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable Long id,
                                                      @Valid @RequestBody StudentDtos.RemoveStudentRequest request) {
        studentService.remove(id, request);
        return ResponseEntity.ok(Map.of("message", "Student removed"));
    }
}
