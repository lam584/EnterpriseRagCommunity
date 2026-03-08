package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    /** Optional override. If null, backend uses active provider. */
    private String providerId;

    /** Optional, 0~2 typical; if null, backend uses server default. */
    private Double temperature;

    /** Optional, 0~1 typical; if null, backend uses server default. */
    private Double topP;

    /** Optional max history messages to include (excluding current user message). */
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

    private List<ImageInput> images;

    private List<FileInput> files;

    @Data
    public static class ImageInput {
        private Long fileAssetId;

        @Size(max = 2048)
        private String url;

        @Size(max = 128)
        private String mimeType;
    }

    @Data
    public static class FileInput {
        private Long fileAssetId;

        @Size(max = 2048)
        private String url;

        @Size(max = 128)
        private String mimeType;

        @Size(max = 255)
        private String fileName;
    }
}
