package com.opportunity.school.service;

import com.opportunity.school.dto.AuthDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.model.User;
import com.opportunity.school.model.enums.Role;
import com.opportunity.school.repository.UserRepository;
import com.opportunity.school.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TeacherService teacherService;
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private Environment environment;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void configureMail() {
        ReflectionTestUtils.setField(authService, "publicUrl", "https://school.example");
        ReflectionTestUtils.setField(authService, "mailFrom", "no-reply@school.example");
    }

    @Test
    void resetPasswordConsumesEmailedToken() throws Exception {
        User user = User.builder()
                .id(5L)
                .email("teacher@example.com")
                .password("old-hash")
                .role(Role.TEACHER)
                .active(true)
                .build();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(userRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authService.requestPasswordReset("teacher@example.com");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        Matcher tokenMatch = Pattern.compile("resetToken=([A-Za-z0-9_-]+)")
                .matcher(messageCaptor.getValue().getText());
        assertTrue(tokenMatch.find());
        String token = tokenMatch.group(1);
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        when(userRepository.findByPasswordResetTokenHash(tokenHash)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        authService.resetPassword(new AuthDtos.ResetPasswordRequest(token, "NewPass123", "NewPass123"));

        assertEquals("new-hash", user.getPassword());
        assertNull(user.getPasswordResetTokenHash());
        assertNull(user.getPasswordResetExpiresAt());
        verify(userRepository).findByPasswordResetTokenHash(tokenHash);
    }

    @Test
    void changeCredentialsRequiresAndChecksCurrentPassword() {
        User user = User.builder()
                .id(7L)
                .email("admin@example.com")
                .password("old-hash")
                .role(Role.ADMIN)
                .active(true)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass123", "old-hash")).thenReturn(false);

        AuthDtos.ChangeCredentialsRequest request = new AuthDtos.ChangeCredentialsRequest(
                "WrongPass123", "new@example.com", "NewPass123", "NewPass123");

        assertThrows(BusinessException.class, () -> authService.changeCredentials(7L, request));
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any());
    }

        @Test
        void changeCredentialsUpdatesAccountAndInvalidatesResetLink() {
                User user = User.builder()
                                .id(7L)
                                .email("admin@example.com")
                                .password("old-hash")
                                .role(Role.ADMIN)
                                .active(true)
                                .passwordResetTokenHash("pending-hash")
                                .passwordResetExpiresAt(LocalDateTime.now().plusMinutes(10))
                                .build();
                when(userRepository.findById(7L)).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("CurrentPass123", "old-hash")).thenReturn(true);
                when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");
                when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
                when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(jwtService.generateToken(any())).thenReturn("new-jwt");

                AuthDtos.LoginResponse response = authService.changeCredentials(7L,
                                new AuthDtos.ChangeCredentialsRequest("CurrentPass123", "new@example.com",
                                                "NewPass123", "NewPass123"));

                assertEquals("new@example.com", response.getEmail());
                assertEquals("new-jwt", response.getToken());
                assertEquals("new-hash", user.getPassword());
                assertNull(user.getPasswordResetTokenHash());
                assertNull(user.getPasswordResetExpiresAt());
        }

    @Test
    void resetPasswordRejectsExpiredToken() {
        User user = User.builder()
                .id(5L)
                .email("teacher@example.com")
                .password("old-hash")
                .role(Role.TEACHER)
                .active(true)
                .passwordResetTokenHash("stored-hash")
                .passwordResetExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(userRepository.findByPasswordResetTokenHash(anyString())).thenReturn(Optional.of(user));

        AuthDtos.ResetPasswordRequest request = new AuthDtos.ResetPasswordRequest(
                "expired-token", "NewPass123", "NewPass123");

        assertThrows(BusinessException.class, () -> authService.resetPassword(request));
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}