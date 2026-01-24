package com.example.EnterpriseRagCommunity.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class OpenSearchTokenizeResponse {
    @JsonProperty("request_id")
    private String requestId;

    private Number latency;

    private String code;
    private String message;

    private Usage usage;
    private Result result;

    @Data
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
    }

    @Data
    public static class Result {
        @JsonProperty("token_ids")
        private List<Integer> tokenIds;
        private List<String> tokens;
    }
}
