package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiPostTitleSuggestRequest {

    @NotBlank
    private String content;

    /** Optional, default 5, max 10 */
    private Integer count;

    /** Optional override model. If null, server uses app.ai.model. */
    private String model;

    /** Optional. Lower value makes titles more stable. */
    private Double temperature;

    /** Optional. Nucleus sampling threshold [0,1]. */
    private Double topP;

    /** Optional board name hint for better titles. */
    private String boardName;

    /** Optional tags hint. */
    private List<String> tags;
}

