package com.opportunity.school.service;

import com.opportunity.school.dto.TeacherDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.RemovalLog;
import com.opportunity.school.model.SchoolClass;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.User;
import com.opportunity.school.model.enums.RemovalReason;
import com.opportunity.school.model.enums.Role;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final RemovalLogRepository removalLogRepository;
    private final SchoolClassRepository classRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TeacherDtos.TeacherDto create(TeacherDtos.CreateTeacherRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new BusinessException("A user with email " + req.getEmail() + " already exists");
        }

        User user = User.builder()
                .email(req.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(req.getPassword() == null || req.getPassword().isBlank()
                        ? "Teacher@2026" : req.getPassword()))
                .role(Role.TEACHER)
                .active(true)
                .build();
        user = userRepository.save(user);

        Teacher teacher = Teacher.builder()
                .fullName(req.getFullName().trim())
                .phone(req.getPhone())
                .address(req.getAddress())
                .active(true)
                .staffNumber(nextStaffNumber())
                .joinDate(req.getJoinDate() != null ? req.getJoinDate() : LocalDate.now())
                .user(user)
                .build();
        teacher = teacherRepository.save(teacher);
        return toDto(teacher);
    }

    @Transactional
    public TeacherDtos.TeacherDto update(Long id, TeacherDtos.UpdateTeacherRequest req) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Teacher", id));
        teacher.setFullName(req.getFullName().trim());
        teacher.setPhone(req.getPhone());
        teacher.setAddress(req.getAddress());
        teacher.setJoinDate(req.getJoinDate());
        teacherRepository.save(teacher);
        return toDto(teacher);
    }

    @Transactional(readOnly = true)
    public Page<TeacherDtos.TeacherDto> list(boolean removed, String search, Pageable pageable) {
        List<Teacher> teachers;
        if (search != null && !search.isBlank()) {
            // Search only active teachers; removed teachers are shown on their own tab without search
            teachers = removed ? List.of() : teacherRepository.findByActiveTrueAndFullNameContainingIgnoreCaseOrderByIdAsc(search.trim());
        } else {
            teachers = removed ? teacherRepository.findByActiveFalseOrderByIdAsc()
                    : teacherRepository.findByActiveTrueOrderByIdAsc();
        }
        return toPage(teachers, pageable);
    }

    @Transactional
    public void remove(Long id, TeacherDtos.RemoveTeacherRequest req) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Teacher", id));
        if (!teacher.isActive()) {
            throw new BusinessException("Teacher is already removed");
        }

        RemovalReason reason;
        try {
            reason = RemovalReason.valueOf(req.getReason().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid removal reason: " + req.getReason());
        }

        teacher.setActive(false);
        // Disable login
        if (teacher.getUser() != null) {
            teacher.getUser().setActive(false);
            userRepository.save(teacher.getUser());
        }

        // Detach from class-teacher roles so classes can reassign
        List<SchoolClass> led = classRepository.findByClassTeacher(teacher);
        for (SchoolClass c : led) {
            c.setClassTeacher(null);
        }
        classRepository.saveAll(led);

        removalLogRepository.save(RemovalLog.builder()
                .entityType("TEACHER")
                .entityId(teacher.getId())
                .entityName(teacher.getFullName())
                .reason(reason)
                .note(req.getNote())
                .removalDate(req.getRemovalDate())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String nextStaffNumber() {
        return String.format("T-%04d", teacherRepository.count() + 1);
    }

    private Page<TeacherDtos.TeacherDto> toPage(List<Teacher> teachers, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), teachers.size());
        List<TeacherDtos.TeacherDto> content = teachers.subList(start, end).stream()
                .map(this::toDto)
                .toList();
        return new PageImpl<>(content, pageable, teachers.size());
    }

    private TeacherDtos.TeacherDto toDto(Teacher t) {
        List<Long> classIds = t.getClassesLed().stream().map(SchoolClass::getId).toList();
        String email = t.getUser() != null ? t.getUser().getEmail() : null;
        return new TeacherDtos.TeacherDto(t.getId(), t.getFullName(), t.getPhone(), t.getAddress(),
                email, t.isActive(), t.getStaffNumber(), t.getJoinDate(), classIds);
    }
}
