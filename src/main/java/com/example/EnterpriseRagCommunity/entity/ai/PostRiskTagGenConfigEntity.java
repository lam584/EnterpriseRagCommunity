package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "ai_gen_task_config")
public class PostRiskTagGenConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "group_code", length = 64, nullable = false)
    private String groupCode;

    @Column(name = "sub_type", length = 32, nullable = false)
    private String subType;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "system_prompt", length = 512, nullable = false)
    private String systemPrompt;

    @Lob
    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "enable_thinking", nullable = false)
    private Boolean enableThinking;

    @Column(name = "max_count", nullable = false)
    private Integer maxCount;

    @Column(name = "max_content_chars", nullable = false)
    private Integer maxContentChars;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
