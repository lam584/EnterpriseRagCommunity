package com.example.EnterpriseRagCommunity.service.access;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUsernameResolver {

    private CurrentUsernameResolver() {
    }

    public static String currentUsernameOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception ex) {
            return null;
        }
    }
}
