package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserReportsController {

    @Autowired
    private PortalReportsService portalReportsService;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @Data
    public static class UserReportRequest {
        @NotBlank(message = "reasonCode 不能为空")
        @Size(max = 64, message = "reasonCode 长度不能超过 64")
        private String reasonCode;

        @Size(max = 255, message = "reasonText 长度不能超过 255")
        private String reasonText;
    }

    @PostMapping("/{userId}/report")
    public Map<String, Object> reportProfile(@PathVariable("userId") Long userId, @Valid @RequestBody UserReportRequest req) {
        Long reporterId = currentUserIdOrNull();
        String actorName = currentUsernameOrNull();
        try {
            PortalReportsService.ReportSubmitResult r = portalReportsService.reportProfile(userId, req.getReasonCode(), req.getReasonText());
            if (reporterId != null) {
                java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                details.put("targetType", "PROFILE");
                details.put("targetId", userId);
                details.put("reasonCode", req.getReasonCode());
                details.put("reasonTextLen", req.getReasonText() == null ? 0 : req.getReasonText().length());
                if (r != null) {
                    details.put("queueId", r.getQueueId());
                }
                auditLogWriter.write(
                        reporterId,
                        actorName,
                        "REPORT_PROFILE_CREATE",
                        "REPORT",
                        r == null ? null : r.getReportId(),
                        AuditResult.SUCCESS,
                        "举报用户资料",
                        null,
                        details
                );
            }
            if (reporterId != null) {
                try {
                    String title = "举报提交成功";
                    String content = null;
                    if (r != null) {
                        content = "目标：用户#" + userId + "；原因：" + req.getReasonCode()
                                + (req.getReasonText() == null || req.getReasonText().isBlank() ? "" : ("；补充：" + req.getReasonText().trim()))
                                + "；reportId=" + r.getReportId() + "；queueId=" + r.getQueueId();
                    }
                    notificationsService.createNotification(reporterId, "REPORT", title, content);
                } catch (Exception ignore) {
                }
            }
            if (r != null) {
                return Map.of("reportId", r.getReportId(), "queueId", r.getQueueId());
            }
        } catch (RuntimeException ex) {
            if (reporterId != null) {
                try {
                    java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                    details.put("targetType", "PROFILE");
                    details.put("targetId", userId);
                    if (req != null) {
                        details.put("reasonCode", req.getReasonCode());
                        details.put("reasonTextLen", req.getReasonText() == null ? 0 : req.getReasonText().length());
                    }
                    details.put("error", ex.getClass().getName());
                    details.put("message", safeMsg(ex.getMessage()));
                    auditLogWriter.write(
                            reporterId,
                            actorName,
                            "REPORT_PROFILE_CREATE",
                            "REPORT",
                            null,
                            AuditResult.FAIL,
                            "举报用户资料失败",
                            null,
                            details
                    );
                } catch (Exception ignore) {
                }
            }
            if (reporterId != null) {
                try {
                    String title = "举报提交失败";
                    String msg = ex.getMessage() == null ? "举报失败" : ex.getMessage();
                    notificationsService.createNotification(reporterId, "REPORT", title, "目标：用户#" + userId + "；原因：" + req.getReasonCode() + "；失败原因：" + msg);
                } catch (Exception ignore) {
                }
            }
            throw ex;
        }
        return Map.of();
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String email = auth.getName();
        return administratorService.findByUsername(email).map(UsersEntity::getId).orElse(null);
    }

    private static String currentUsernameOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String name = auth.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static String safeMsg(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= 256) return t;
        return t.substring(0, 256);
    }
}

