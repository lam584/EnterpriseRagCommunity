package com.example.EnterpriseRagCommunity.controller.content;

import java.util.LinkedHashMap;
import java.util.Map;

final class ReportAuditSupport {

    private ReportAuditSupport() {
    }

    static Map<String, Object> buildReportDetails(String targetType, Long targetId, String reasonCode, String reasonText) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetType", targetType);
        details.put("targetId", targetId);
        details.put("reasonCode", reasonCode);
        details.put("reasonTextLen", reasonText == null ? 0 : reasonText.length());
        return details;
    }

    static void appendFailure(Map<String, Object> details, RuntimeException ex, java.util.function.Function<String, String> safeMessage) {
        details.put("error", ex.getClass().getName());
        details.put("message", safeMessage.apply(ex.getMessage()));
    }
}
