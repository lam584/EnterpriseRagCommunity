package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.monitor.LogRetentionConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.LogRetentionConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/log-retention")
@Api(tags = "日志保留策略")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class LogRetentionController {

    private final LogRetentionConfigService logRetentionConfigService;

    public LogRetentionController(LogRetentionConfigService logRetentionConfigService) {
        this.logRetentionConfigService = logRetentionConfigService;
    }

    @GetMapping
    @ApiOperation("获取日志保留策略配置")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LogRetentionConfigDTO> get() {
        return ResponseEntity.ok(logRetentionConfigService.getConfig());
    }

    @PutMapping
    @ApiOperation("更新日志保留策略配置")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LogRetentionConfigDTO> update(@RequestBody LogRetentionConfigDTO payload) {
        return ResponseEntity.ok(logRetentionConfigService.updateConfig(payload));
    }
}

