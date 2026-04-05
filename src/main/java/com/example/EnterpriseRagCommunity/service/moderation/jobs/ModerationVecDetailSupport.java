package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModerationVecDetailSupport {

    private ModerationVecDetailSupport() {
    }

    static Map<String, Object> baseDetails(boolean hit, SimilarityCheckResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("hit", hit);
        details.put("bestDistance", resp == null ? null : resp.getBestDistance());
        details.put("threshold", resp == null ? null : resp.getThreshold());
        details.put("hits", resp == null ? List.of() : resp.getHits());
        return details;
    }
}
