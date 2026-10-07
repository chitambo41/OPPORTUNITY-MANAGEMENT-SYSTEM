package com.opportunity.school.controller;

import com.opportunity.school.dto.TeacherDtos;
import com.opportunity.school.service.ProfilePictureService;
import com.opportunity.school.service.TeacherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/teachers")
@RequiredArgsConstructor
public class AdminTeacherController {

    private final TeacherService teacherService;
    private final ProfilePictureService profilePictureService;

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

    @PostMapping(value = "/{id}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadProfilePicture(@PathVariable Long id,
                                                                     @RequestParam("file") MultipartFile file) {
        profilePictureService.saveTeacherPicture(id, file);
        return ResponseEntity.ok(Map.of("message", "Teacher profile picture updated"));
    }

    @GetMapping("/{id}/profile-picture")
    public ResponseEntity<byte[]> profilePicture(@PathVariable Long id) {
        return profilePictureService.getTeacherPicture(id)
                .map(picture -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(picture.contentType()))
                        .cacheControl(CacheControl.noStore())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(picture.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
