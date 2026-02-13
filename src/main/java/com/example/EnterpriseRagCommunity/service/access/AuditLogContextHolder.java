package com.example.EnterpriseRagCommunity.service.access;

public final class AuditLogContextHolder {

    private static final ThreadLocal<Boolean> WRITTEN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AuditLogContextHolder() {
    }

    public static boolean wasWritten() {
        return Boolean.TRUE.equals(WRITTEN.get());
    }

    public static void markWritten() {
        WRITTEN.set(Boolean.TRUE);
    }

    public static void clear() {
        WRITTEN.remove();
    }
}
