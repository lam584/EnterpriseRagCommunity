package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "ai_gen_task_config")
public class PostSuggestionGenConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_type", nullable = false, length = 32)
    private SuggestionKind kind;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "prompt_code", length = 64, nullable = false)
    private String promptCode;

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
