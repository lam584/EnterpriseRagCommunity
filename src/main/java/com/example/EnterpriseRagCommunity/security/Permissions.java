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

    public static String permScoped(String resource, String action, String scopeType, long scopeId) {
        String st = (scopeType == null || scopeType.isBlank()) ? "GLOBAL" : scopeType.trim().toUpperCase(java.util.Locale.ROOT);
        long sid = scopeId < 0 ? 0 : scopeId;
        String suffix = ("GLOBAL".equals(st) && sid == 0L) ? "" : "@" + st + ":" + sid;
        return perm(resource, action) + suffix;
    }
}

