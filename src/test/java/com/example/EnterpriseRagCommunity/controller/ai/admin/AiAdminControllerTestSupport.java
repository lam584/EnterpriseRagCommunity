package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.security.Permissions;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class AiAdminControllerTestSupport {
    private AiAdminControllerTestSupport() {
    }

    static SimpleGrantedAuthority perm(String resource, String action) {
        return new SimpleGrantedAuthority(Permissions.perm(resource, action));
    }
}

