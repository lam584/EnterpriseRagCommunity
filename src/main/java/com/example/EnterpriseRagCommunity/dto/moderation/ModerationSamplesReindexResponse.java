package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class ModerationSamplesReindexResponse {

    private long total;
    private long success;
    private long failed;

    /**
     * For UI/debug: include a small list of failed ids.
     */
    private List<Long> failedIds;

    private Long fromId;
    private Integer batchSize;
    private Boolean onlyEnabled;

    /**
     * Whether ES index was cleared at the beginning of this run.
     * Usually true when fromId is null/0 (full rebuild).
     */
    private Boolean cleared;

    /**
     * If clear failed, provide message for UI.
     */
    private String clearError;

    /**
     * Orphan docs deleted from ES (docs exist in ES but not in MySQL).
     */
    private Long orphanDeleted;

    /**
     * Orphan deletes failed.
     */
    private Long orphanFailed;

    /** For UI/debug: include a small list of orphan ids failed to delete. */
    private List<Long> orphanFailedIds;
}
