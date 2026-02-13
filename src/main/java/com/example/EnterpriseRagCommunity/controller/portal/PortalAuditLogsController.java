package com.example.EnterpriseRagCommunity.controller.portal;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogsService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/portal/audit-logs")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PortalAuditLogsController {

    private final AuditLogsService auditLogsService;
    private final AdministratorService administratorService;

    public PortalAuditLogsController(AuditLogsService auditLogsService, AdministratorService administratorService) {
        this.auditLogsService = auditLogsService;
        this.administratorService = administratorService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AuditLogsViewDTO>> listMine(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "result", required = false) AuditResult result,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        Long me = currentUserIdOrThrow();
        String effectiveAction = StringUtils.hasText(action) ? action.trim() : "MODERATION_";
        return ResponseEntity.ok(auditLogsService.query(
                page, pageSize,
                keyword,
                me,
                null,
                effectiveAction,
                null,
                entityType,
                entityId,
                result,
                createdFrom,
                createdTo,
                traceId,
                sort
        ));
    }

    @GetMapping("/me/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuditLogsViewDTO> getMineById(@PathVariable("id") Long id) {
        Long me = currentUserIdOrThrow();
        AuditLogsViewDTO dto = auditLogsService.getById(id);
        if (dto.actorId() == null || !dto.actorId().equals(me)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping(value = "/me/export.csv", produces = "text/csv")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportMineCsv(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "result", required = false) AuditResult result,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        Long me = currentUserIdOrThrow();
        String effectiveAction = StringUtils.hasText(action) ? action.trim() : "MODERATION_";
        Page<AuditLogsViewDTO> pageRes = auditLogsService.query(
                1, 5000,
                keyword,
                me,
                null,
                effectiveAction,
                null,
                entityType,
                entityId,
                result,
                createdFrom,
                createdTo,
                traceId,
                sort
        );

        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,actorId,actorName,action,entityType,entityId,result,traceId,method,path,autoCrud,message\n");
        for (AuditLogsViewDTO it : pageRes.getContent()) {
            sb.append(csv(it.id())).append(',')
                    .append(csv(it.createdAt())).append(',')
                    .append(csv(it.actorId())).append(',')
                    .append(csv(it.actorName())).append(',')
                    .append(csv(it.action())).append(',')
                    .append(csv(it.entityType())).append(',')
                    .append(csv(it.entityId())).append(',')
                    .append(csv(it.result())).append(',')
                    .append(csv(it.traceId())).append(',')
                    .append(csv(it.method())).append(',')
                    .append(csv(it.path())).append(',')
                    .append(csv(it.autoCrud())).append(',')
                    .append(csv(it.message()))
                    .append('\n');
        }

        String filename = "my-moderation-audit-logs.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException("未登录或会话已过期");
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("当前用户不存在"))
                .getId();
    }

    private static String csv(Object v) {
        if (v == null) return "\"\"";
        String s = String.valueOf(v);
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
