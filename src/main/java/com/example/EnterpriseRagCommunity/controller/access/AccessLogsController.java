package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogEsIndexStatusDTO;
import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.service.access.AccessLogEsIndexStatusService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/access-logs")
@Api(tags = "访问日志")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccessLogsController {

    private final AccessLogsService accessLogsService;
    private final AccessLogEsIndexStatusService accessLogEsIndexStatusService;

    public AccessLogsController(
            AccessLogsService accessLogsService,
            AccessLogEsIndexStatusService accessLogEsIndexStatusService
    ) {
        this.accessLogsService = accessLogsService;
        this.accessLogEsIndexStatusService = accessLogEsIndexStatusService;
    }

    @GetMapping
    @ApiOperation("分页查询访问日志")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AccessLogsViewDTO>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "statusCode", required = false) Integer statusCode,
            @RequestParam(value = "clientIp", required = false) String clientIp,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        return ResponseEntity.ok(accessLogsService.query(
                page, pageSize,
                keyword,
                userId,
                username,
                method,
                path,
                statusCode,
                clientIp,
                requestId,
                traceId,
                createdFrom,
                createdTo,
                sort
        ));
    }

    @GetMapping("/{id}")
    @ApiOperation("获取访问日志详情")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccessLogsViewDTO> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(accessLogsService.getById(id));
    }

    @GetMapping("/es-index-status")
    @ApiOperation("获取访问日志 ES 索引状态")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccessLogEsIndexStatusDTO> getEsIndexStatus() {
        return ResponseEntity.ok(accessLogEsIndexStatusService.getStatus());
    }

    @PostMapping(value = "/export.csv", produces = "text/csv")
    @ApiOperation("导出 CSV")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "statusCode", required = false) Integer statusCode,
            @RequestParam(value = "clientIp", required = false) String clientIp,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        Page<AccessLogsViewDTO> pageRes = accessLogsService.query(
                1, 5000,
                keyword,
                userId,
                username,
                method,
                path,
                statusCode,
                clientIp,
                requestId,
                traceId,
                createdFrom,
                createdTo,
                sort
        );

        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,userId,username,method,path,statusCode,latencyMs,clientIp,clientPort,serverIp,serverPort,requestId,traceId,userAgent\n");
        for (AccessLogsViewDTO it : pageRes.getContent()) {
            sb.append(csv(it.id())).append(',')
                    .append(csv(it.createdAt())).append(',')
                    .append(csv(it.userId())).append(',')
                    .append(csv(it.username())).append(',')
                    .append(csv(it.method())).append(',')
                    .append(csv(it.path())).append(',')
                    .append(csv(it.statusCode())).append(',')
                    .append(csv(it.latencyMs())).append(',')
                    .append(csv(it.clientIp())).append(',')
                    .append(csv(it.clientPort())).append(',')
                    .append(csv(it.serverIp())).append(',')
                    .append(csv(it.serverPort())).append(',')
                    .append(csv(it.requestId())).append(',')
                    .append(csv(it.traceId())).append(',')
                    .append(csv(it.userAgent()))
                    .append('\n');
        }

        sb.insert(0, '\uFEFF');
        String filename = "access-logs.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(Object v) {
        if (v == null) return "\"\"";
        String s = String.valueOf(v);
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
