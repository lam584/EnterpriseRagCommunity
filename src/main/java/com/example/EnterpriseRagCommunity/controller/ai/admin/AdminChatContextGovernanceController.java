package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventDetailDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import com.example.EnterpriseRagCommunity.service.ai.admin.ChatContextEventLogsService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.access.DateTimeParamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/ai/chat-context")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminChatContextGovernanceController {
    private final ChatContextGovernanceConfigService configService;
    private final ChatContextEventLogsService logsService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_chat_context','access'))")
    public ChatContextGovernanceConfigDTO getConfig() {
        return configService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_chat_context','write'))")
    public ChatContextGovernanceConfigDTO updateConfig(@RequestBody ChatContextGovernanceConfigDTO payload) {
        ChatContextGovernanceConfigDTO before = configService.getConfig();
        ChatContextGovernanceConfigDTO after = configService.updateConfig(payload);
        auditLogWriter.write(
                null,
                CurrentUsernameResolver.currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "CHAT_CONTEXT_GOVERNANCE_CONFIG",
                null,
                AuditResult.SUCCESS,
                "更新对话上下文治理配置",
                null,
                auditDiffBuilder.build(before, after)
        );
        return after;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_chat_context','access'))")
    public Page<AdminChatContextEventLogDTO> listLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        return logsService.list(parseTimeOrNull(from), parseTimeOrNull(to), page, size);
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_chat_context','access'))")
    public AdminChatContextEventDetailDTO getLog(@PathVariable("id") long id) {
        return logsService.get(id);
    }

    private static LocalDateTime parseTimeOrNull(String s) {
        return DateTimeParamSupport.parseOrNull(s);
    }

    private static String currentUsernameOrNull() {
        return CurrentUsernameResolver.currentUsernameOrNull();
    }
}
