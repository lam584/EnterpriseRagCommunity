package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.service.access.AccessControlService;

/**
 * Helper utilities for permission string naming.
 */
public final class Permissions {

    private Permissions() {
    }

    public static String perm(String resource, String action) {
        return AccessControlService.PERM_PREFIX + resource + ":" + action;
    }
}

