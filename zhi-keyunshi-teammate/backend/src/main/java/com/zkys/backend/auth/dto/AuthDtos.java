package com.zkys.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Email String email,
            @NotBlank String role,
            String name,
            String inviteCode,
            String studentNo,
            String studentName
    ) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record AuthResponse(String token, Object user, String className) {}

    public record SafeUser(String id, String username, String role, String email, String name) {}
}
