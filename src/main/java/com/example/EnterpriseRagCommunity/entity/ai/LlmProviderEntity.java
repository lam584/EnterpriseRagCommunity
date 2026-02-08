package com.example.EnterpriseRagCommunity.entity.ai;

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
@Table(name = "llm_providers")
public class LlmProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "env", length = 32, nullable = false)
    private String env;

    @Column(name = "provider_id", length = 64, nullable = false)
    private String providerId;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "api_key_encrypted")
    private byte[] apiKeyEncrypted;

    @Column(name = "extra_headers_encrypted")
    private byte[] extraHeadersEncrypted;

    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "max_concurrent")
    private Integer maxConcurrent;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "default_chat_model", length = 128)
    private String defaultChatModel;

    @Column(name = "default_embedding_model", length = 128)
    private String defaultEmbeddingModel;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
