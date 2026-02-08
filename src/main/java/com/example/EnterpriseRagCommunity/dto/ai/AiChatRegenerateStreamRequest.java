package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiChatRegenerateStreamRequest {
    /** Optional override. If null, backend uses app.ai.model. */
    private String model;

    /** Optional override. If null, backend uses active provider. */
    private String providerId;

    /** Optional, 0~2 typical; if null, backend uses server default. */
    private Double temperature;

    /** Optional, 0~1 typical; if null, backend uses server default. */
    private Double topP;

    /** Optional max history messages to include (only messages before the question). */
    private Integer historyLimit;

    /** If true, use a more thorough system instruction and a steadier default temperature. */
    @NotNull
    private Boolean deepThink = false;

    /** If true, enable retrieval-augmented generation. Default true for backward compatibility. */
    @NotNull
    private Boolean useRag = true;

    /** Optional retrieval TopK override (1~50). */
    private Integer ragTopK;

    /** If true, backend won't persist to DB (useful for debugging). Default false. */
    @NotNull
    private Boolean dryRun = false;
}
