package com.opportunity.school.controller;

import com.opportunity.school.dto.AuthDtos;
import com.opportunity.school.security.UserPrincipal;
import com.opportunity.school.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;

    @PutMapping("/credentials")
    public ResponseEntity<AuthDtos.LoginResponse> changeCredentials(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AuthDtos.ChangeCredentialsRequest request) {
        return ResponseEntity.ok(authService.changeCredentials(principal.getId(), request));
    }
}