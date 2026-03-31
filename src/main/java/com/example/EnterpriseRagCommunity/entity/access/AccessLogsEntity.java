package com.example.EnterpriseRagCommunity.entity.access;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "access_logs")
public class AccessLogsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 191)
    private String username;

    @Column(name = "method", nullable = false, length = 16)
    private String method;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "query_string", length = 1024)
    private String queryString;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "client_port")
    private Integer clientPort;

    @Column(name = "server_ip", length = 64)
    private String serverIp;

    @Column(name = "server_port")
    private Integer serverPort;

    @Column(name = "scheme", length = 16)
    private String scheme;

    @Column(name = "host")
    private String host;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referer", length = 512)
    private String referer;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "details", columnDefinition = "json")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}

