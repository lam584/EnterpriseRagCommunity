package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/moderation/llm")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationLlmController {

    private final AdminModerationLlmService service;
    private final AuditLogWriter auditLogWriter;

    public AdminModerationLlmController(AdminModerationLlmService service, AuditLogWriter auditLogWriter) {
        this.service = service;
        this.auditLogWriter = auditLogWriter;
    }

    @GetMapping("/config")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationConfigDTO getConfig() {
        return service.getConfig();
    }

    @PutMapping("/config")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationConfigDTO upsertConfig(@RequestBody LlmModerationConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        try {
            return service.upsertConfig(payload, null, username);
        } catch (RuntimeException e) {
            try {
                auditLogWriter.write(
                        null,
                        username,
                        "CONFIG_CHANGE",
                        "MODERATION_LLM_CONFIG",
                        null,
                        AuditResult.FAIL,
                        safeText(e.getMessage(), 512),
                        null,
                        Map.of()
                );
            } catch (Exception ignore) {
            }
            throw e;
        }
    }

    @PostMapping("/test")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationTestResponse test(@RequestBody LlmModerationTestRequest req, Principal principal) {
        String username = principal == null ? null : principal.getName();
        try {
            LlmModerationTestResponse resp = service.test(req);
            try {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("queueId", req == null ? null : req.getQueueId());
                details.put("hasText", req != null && req.getText() != null && !req.getText().isBlank());
                details.put("images", req == null || req.getImages() == null ? 0 : req.getImages().size());
                details.put("decision", resp == null ? null : resp.getDecision());
                details.put("score", resp == null ? null : resp.getScore());
                details.put("inputMode", resp == null ? null : resp.getInputMode());
                details.put("latencyMs", resp == null ? null : resp.getLatencyMs());
                auditLogWriter.write(
                        null,
                        username,
                        "LLM_TEST",
                        (req != null && req.getQueueId() != null) ? "MODERATION_QUEUE" : "SYSTEM",
                        req == null ? null : req.getQueueId(),
                        AuditResult.SUCCESS,
                        "LLM 试运行",
                        null,
                        details
                );
            } catch (Exception ignore) {
            }
            return resp;
        } catch (RuntimeException e) {
            try {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("queueId", req == null ? null : req.getQueueId());
                details.put("hasText", req != null && req.getText() != null && !req.getText().isBlank());
                details.put("images", req == null || req.getImages() == null ? 0 : req.getImages().size());
                auditLogWriter.write(
                        null,
                        username,
                        "LLM_TEST",
                        (req != null && req.getQueueId() != null) ? "MODERATION_QUEUE" : "SYSTEM",
                        req == null ? null : req.getQueueId(),
                        AuditResult.FAIL,
                        safeText(e.getMessage(), 512),
                        null,
                        details
                );
            } catch (Exception ignore) {
            }
            throw e;
        }
    }

    private static String safeText(String s, int maxLen) {
        if (s == null) return null;
        String t = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (t.isBlank()) return null;
        if (maxLen <= 0) return "";
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }
}
