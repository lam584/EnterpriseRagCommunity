package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_title_gen_config")
public class PostTitleGenConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "system_prompt", length = 512, nullable = false)
    private String systemPrompt;

    @Lob
    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "default_count", nullable = false)
    private Integer defaultCount;

    @Column(name = "max_count", nullable = false)
    private Integer maxCount;

    @Column(name = "max_content_chars", nullable = false)
    private Integer maxContentChars;

    @Column(name = "history_enabled", nullable = false)
    private Boolean historyEnabled;

    @Column(name = "history_keep_days")
    private Integer historyKeepDays;

    @Column(name = "history_keep_rows")
    private Integer historyKeepRows;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}

