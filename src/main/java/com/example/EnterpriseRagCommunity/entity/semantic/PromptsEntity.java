package com.example.EnterpriseRagCommunity.entity.semantic;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "prompts")
public class PromptsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 96)
    private String name;

    @Column(name = "prompt_code", nullable = false, unique = true, length = 64)
    private String promptCode;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Lob
    @Column(name = "user_prompt_template", nullable = false)
    private String userPromptTemplate;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "enable_deep_thinking")
    private Boolean enableDeepThinking;

    @Column(name = "vision_model", length = 128)
    private String visionModel;

    @Column(name = "vision_provider_id", length = 64)
    private String visionProviderId;

    @Column(name = "vision_temperature")
    private Double visionTemperature;

    @Column(name = "vision_top_p")
    private Double visionTopP;

    @Column(name = "vision_max_tokens")
    private Integer visionMaxTokens;

    @Column(name = "vision_enable_deep_thinking")
    private Boolean visionEnableDeepThinking;

    @Column(name = "wait_files_seconds")
    private Integer waitFilesSeconds;

    @Column(name = "vision_image_token_budget")
    private Integer visionImageTokenBudget;

    @Column(name = "vision_max_images_per_request")
    private Integer visionMaxImagesPerRequest;

    @Column(name = "vision_high_resolution_images")
    private Boolean visionHighResolutionImages;

    @Column(name = "vision_max_pixels")
    private Integer visionMaxPixels;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.JsonConverter.class)
    @Column(name = "variables", columnDefinition = "json")
    private Map<String, Object> variables;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
