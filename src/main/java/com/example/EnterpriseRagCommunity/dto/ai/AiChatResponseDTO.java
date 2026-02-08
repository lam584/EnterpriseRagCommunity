package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiChatResponseDTO {
    private Long sessionId;
    private Long userMessageId;
    private Long questionMessageId;
    private Long assistantMessageId;
    private String content;
    private List<AiCitationSourceDTO> sources;
    private Long latencyMs;

    @Data
    public static class AiCitationSourceDTO {
        private Integer index;
        private Long postId;
        private Integer chunkIndex;
        private Double score;
        private String title;
        private String url;
    }
}
