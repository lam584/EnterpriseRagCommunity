package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "ai_gen_task_config")
public class PostSummaryGenConfigEntity {

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

    @Column(name = "prompt_code", length = 64, nullable = false)
    private String promptCode;

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
