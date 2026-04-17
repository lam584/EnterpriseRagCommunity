package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditLogsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
@Api(tags = "审计日志")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AuditLogsController {

    private final AuditLogsService auditLogsService;

    public AuditLogsController(AuditLogsService auditLogsService) {
        this.auditLogsService = auditLogsService;
    }

    @GetMapping
    @ApiOperation("分页查询审计日志")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogsViewDTO>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actorName", required = false) String actorName,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "op", required = false) String op,
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
        return ResponseEntity.ok(auditLogsService.query(
                page, pageSize,
                keyword,
                actorId,
                actorName,
                action,
                op,
                entityType,
                entityId,
                result,
                createdFrom,
                createdTo,
                traceId,
                sort
        ));
    }

    @GetMapping("/{id}")
    @ApiOperation("获取审计日志详情")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditLogsViewDTO> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(auditLogsService.getById(id));
    }

    /**
     * 同步导出（面向小数据量）。
     * 前端用 POST 以便 CSRF 保护。
     */
    @PostMapping(value = "/export.csv", produces = "text/csv")
    @ApiOperation("导出 CSV")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actorName", required = false) String actorName,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "op", required = false) String op,
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
        // 复用 query，并限制导出最多 5000 条
        Page<AuditLogsViewDTO> pageRes = auditLogsService.query(
                1, 5000,
                keyword,
                actorId,
                actorName,
                action,
                op,
                entityType,
                entityId,
                result,
                createdFrom,
                createdTo,
                traceId,
                sort
        );

        String filename = "audit-logs.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(AuditLogsCsvSupport.toCsvBytes(pageRes.getContent()));
    }
}
