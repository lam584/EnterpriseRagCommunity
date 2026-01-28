package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_summary_gen_config")
public class PostSummaryGenConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Lob
    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

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

