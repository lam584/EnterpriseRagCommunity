package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.service.AdministratorService;

import java.security.Principal;

final class AdminAiControllerSupport {

    private AdminAiControllerSupport() {
    }

    static Long resolveActorUserId(AdministratorService administratorService, Principal principal) {
        String username = principal == null ? null : principal.getName();
        if (username == null) {
            return null;
        }
        return administratorService.findByUsername(username)
                .map(com.example.EnterpriseRagCommunity.entity.access.UsersEntity::getId)
                .orElse(null);
    }
}
