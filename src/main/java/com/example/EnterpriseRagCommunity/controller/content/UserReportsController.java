package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserReportsController {
    private final PortalReportsService portalReportsService;
    private final NotificationsService notificationsService;
    private final AdministratorService administratorService;
    private final AuditLogWriter auditLogWriter;

    @Data
    public static class UserReportRequest {
        @NotBlank(message = "reasonCode 不能为空")
        @Size(max = 64, message = "reasonCode 长度不能超过 64")
        private String reasonCode;

        @Size(max = 255, message = "reasonText 长度不能超过 255")
        private String reasonText;
    }

    @PostMapping("/{userId}/report")
    public Map<String, Object> reportProfile(@PathVariable Long userId, @Valid @RequestBody UserReportRequest req) {
        Long reporterId = currentUserIdOrNull();
        String actorName = currentUsernameOrNull();
        try {
            PortalReportsService.ReportSubmitResult r = portalReportsService.reportProfile(userId, req.getReasonCode(), req.getReasonText());
            if (reporterId != null) {
                java.util.Map<String, Object> details = ReportAuditSupport.buildReportDetails("PROFILE", userId, req.getReasonCode(), req.getReasonText());
                if (r != null) {
                    details.put("queueId", r.queueId());
                }
                auditLogWriter.write(
                        reporterId,
                        actorName,
                        "REPORT_PROFILE_CREATE",
                        "REPORT",
                        r == null ? null : r.reportId(),
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
                                + "；reportId=" + r.reportId() + "；queueId=" + r.queueId();
                    }
                    notificationsService.createNotification(reporterId, "REPORT", title, content);
                } catch (Exception ignore) {
                }
            }
            if (r != null) {
                return Map.of("reportId", r.reportId(), "queueId", r.queueId());
            }
        } catch (RuntimeException ex) {
            if (reporterId != null) {
                try {
                    java.util.Map<String, Object> details = ReportAuditSupport.buildReportDetails(
                            "PROFILE",
                            userId,
                            req == null ? null : req.getReasonCode(),
                            req == null ? null : req.getReasonText()
                    );
                    ReportAuditSupport.appendFailure(details, ex, UserReportsController::safeMsg);
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
        return CurrentUsernameResolver.currentUsernameOrNull();
    }

    private static String safeMsg(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= 256) return t;
        return t.substring(0, 256);
    }
}
