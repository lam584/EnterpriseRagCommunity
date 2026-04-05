package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.service.AdministratorService;

import java.security.Principal;

final class AdminSemanticControllerSupport {

    private AdminSemanticControllerSupport() {
    }

    static String resolveUsername(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    static Long resolveActorUserId(AdministratorService administratorService, Principal principal) {
        String username = resolveUsername(principal);
        if (username == null) {
            return null;
        }
        return administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
    }
}
