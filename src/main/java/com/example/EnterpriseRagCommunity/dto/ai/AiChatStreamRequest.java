package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Frontend -> Backend chat stream request.
 *
 * Contract:
 * - message: required user message
 * - sessionId: optional (new session when null)
 */
@Data
public class AiChatStreamRequest {
    private Long sessionId;

    @NotBlank
    private String message;

    /** Optional override. If null, backend uses app.ai.model. */
    private String model;

    /** Optional, 0~2 typical; if null, backend uses server default. */
    private Double temperature;

    /** Optional max history messages to include (excluding current user message). */
    private Integer historyLimit;

    /** If true, backend won't persist to DB (useful for debugging). Default false. */
    @NotNull
    private Boolean dryRun = false;
}

