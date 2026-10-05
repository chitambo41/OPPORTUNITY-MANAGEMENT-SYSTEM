package com.opportunity.school.security;

import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.User;
import com.opportunity.school.repository.TeacherRepository;
import com.opportunity.school.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Helpers to fetch the current user and enforce teacher-owns-resource rules.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;

    public UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BusinessException("Not authenticated");
    }

    public User currentUserEntity() {
        return userRepository.findById(currentUser().getId())
                .orElseThrow(() -> new BusinessException("Current user no longer exists"));
    }

    public Teacher currentTeacher() {
        UserPrincipal principal = currentUser();
        if (principal.getTeacherId() == null) {
            throw new BusinessException("Current user has no teacher profile");
        }
        return teacherRepository.findById(principal.getTeacherId())
                .orElseThrow(() -> new BusinessException("Teacher profile not found"));
    }

    /** Throws when the current TEACHER does not own the given teacher id. */
    public void assertOwnTeacher(Long teacherId) {
        UserPrincipal principal = currentUser();
        if (principal.isTeacher() && !Objects.equals(principal.getTeacherId(), teacherId)) {
            throw new BusinessException("Teachers can only access their own records");
        }
    }
}
