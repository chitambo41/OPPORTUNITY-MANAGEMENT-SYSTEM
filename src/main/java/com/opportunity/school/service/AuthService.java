package com.opportunity.school.service;

import com.opportunity.school.dto.AuthDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.User;
import com.opportunity.school.model.enums.Role;
import com.opportunity.school.repository.UserRepository;
import com.opportunity.school.security.JwtService;
import com.opportunity.school.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BusinessException("This account has been disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Invalid email or password");
        }

        UserPrincipal principal = toPrincipal(user);
        String token = jwtService.generateToken(principal);

        Long teacherId = null;
        String teacherName = null;
        if (user.getRole() == Role.TEACHER) {
            Teacher teacher = user.getTeacher();
            if (teacher != null) {
                teacherId = teacher.getId();
                teacherName = teacher.getFullName();
            }
        }

        return new AuthDtos.LoginResponse(token, user.getId(), user.getEmail(),
                user.getRole().name(), teacherId, teacherName);
    }

    private UserPrincipal toPrincipal(User user) {
        Long teacherId = user.getTeacher() != null ? user.getTeacher().getId() : null;
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), teacherId);
    }
}
