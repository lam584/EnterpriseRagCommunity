package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_llm_config")
public class ModerationLlmConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "text_prompt_code", length = 64)
    private String textPromptCode;

    @Column(name = "vision_prompt_code", length = 64)
    private String visionPromptCode;

    @Column(name = "judge_prompt_code", length = 64)
    private String judgePromptCode;


    @Column(name = "auto_run", nullable = false)
    private Boolean autoRun;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
