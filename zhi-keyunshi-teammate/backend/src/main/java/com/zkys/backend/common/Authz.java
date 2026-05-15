package com.zkys.backend.common;

import com.zkys.backend.security.UserPrincipal;
import org.springframework.security.core.Authentication;

public final class Authz {
    private Authz() {}

    public static UserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal p)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return p;
    }

    public static void requireRole(UserPrincipal principal, String role) {
        if (!role.equals(principal.role())) {
            throw new IllegalArgumentException("无权限访问该接口");
        }
    }
}
