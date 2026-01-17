package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SimilarityCheckResponse {

    private boolean hit;

    /** Lower distance means more similar (cosine distance). */
    private Double bestDistance;

    private Double threshold;

    private List<Hit> hits = new ArrayList<>();

    @Data
    public static class Hit {
        private Long sampleId;
        private Double distance;
        private String category;
        private Integer riskLevel;
        private String rawTextPreview;
    }
}
