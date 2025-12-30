package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class AdminModerationQueueBackfillResponse {
    private int scannedPosts;
    private int scannedComments;

    private int alreadyQueued;
    private int enqueued;
    private int skipped;
}

