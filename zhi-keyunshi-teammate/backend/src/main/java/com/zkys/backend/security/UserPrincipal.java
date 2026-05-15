package com.zkys.backend.security;

public record UserPrincipal(String id, String username, String role, String email, String name) {
}
