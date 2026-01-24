package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class OpenSearchTokenizeRequest {
    private String text;
    private List<Message> messages;
    private String workspaceName;
    private String serviceId;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
