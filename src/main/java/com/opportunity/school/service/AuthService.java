package com.opportunity.school.service;

import com.opportunity.school.dto.AuthDtos;
import com.opportunity.school.dto.TeacherDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.model.User;
import com.opportunity.school.model.enums.Role;
import com.opportunity.school.repository.UserRepository;
import com.opportunity.school.security.JwtService;
import com.opportunity.school.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TeacherService teacherService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    @Value("${app.public-url:http://localhost:8081}")
    private String publicUrl;

    @Value("${app.mail.from:no-reply@opportunity-school.local}")
    private String mailFrom;

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BusinessException("This account has been disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Invalid email or password");
        }

        return toLoginResponse(user);
    }

    @Transactional
    public TeacherDtos.TeacherDto registerTeacher(AuthDtos.TeacherRegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        TeacherDtos.CreateTeacherRequest teacherRequest = new TeacherDtos.CreateTeacherRequest();
        teacherRequest.setFullName(request.getFullName());
        teacherRequest.setPhone(request.getPhone());
        teacherRequest.setAddress(request.getAddress());
        teacherRequest.setEmail(request.getEmail());
        teacherRequest.setPassword(request.getPassword());
        return teacherService.create(teacherRequest);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        boolean developmentProfile = environment.acceptsProfiles(Profiles.of("dev"));
        if (mailSender == null && !developmentProfile) {
            throw new BusinessException("Password reset email is not configured. Please contact the administrator.");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(User::isActive)
                .orElse(null);
        if (user == null) {
            return;
        }

        String token = generateResetToken();
        user.setPasswordResetTokenHash(hashToken(token));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));
        userRepository.save(user);

        String resetUrl = publicUrl.replaceAll("/+$", "") + "/login.html?resetToken=" + token;
        if (mailSender == null) {
            log.warn("Password reset link for {}: {}", user.getEmail(), resetUrl);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Reset your Opportunity Nursery School password");
        message.setText("Use this link within 30 minutes to reset your password:\n\n" + resetUrl);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            user.setPasswordResetTokenHash(null);
            user.setPasswordResetExpiresAt(null);
            userRepository.save(user);
            log.error("Unable to send a password reset email", ex);
        }
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        User user = userRepository.findByPasswordResetTokenHash(hashToken(request.getToken()))
                .filter(account -> account.getPasswordResetExpiresAt() != null &&
                        account.getPasswordResetExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("This password reset link is invalid or has expired"));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    @Transactional
    public AuthDtos.LoginResponse changeCredentials(Long userId, AuthDtos.ChangeCredentialsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Account not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        String newEmail = StringUtils.hasText(request.getNewEmail()) ? request.getNewEmail().trim() : null;
        String newPassword = StringUtils.hasText(request.getNewPassword()) ? request.getNewPassword() : null;
        if (newPassword != null && !newPassword.equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }
        if (newEmail == null && newPassword == null) {
            throw new BusinessException("Enter a new email or password");
        }
        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BusinessException("An account with this email already exists");
            }
            user.setEmail(newEmail.toLowerCase());
        }
        if (newPassword != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);

        user = userRepository.save(user);
        return toLoginResponse(user);
    }

    private UserPrincipal toPrincipal(User user) {
        Long teacherId = user.getTeacher() != null ? user.getTeacher().getId() : null;
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), teacherId);
    }

    private AuthDtos.LoginResponse toLoginResponse(User user) {
        UserPrincipal principal = toPrincipal(user);
        Teacher teacher = user.getRole() == Role.TEACHER ? user.getTeacher() : null;
        return new AuthDtos.LoginResponse(jwtService.generateToken(principal), user.getId(), user.getEmail(),
                user.getRole().name(), teacher == null ? null : teacher.getId(),
                teacher == null ? null : teacher.getFullName());
    }

    private String generateResetToken() {
        byte[] tokenBytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
