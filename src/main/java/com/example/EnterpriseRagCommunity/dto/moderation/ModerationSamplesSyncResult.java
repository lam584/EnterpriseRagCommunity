package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class ModerationSamplesSyncResult {
    private Long id;
    private String action; // upsert/delete
    private boolean success;
    private String message;
}
