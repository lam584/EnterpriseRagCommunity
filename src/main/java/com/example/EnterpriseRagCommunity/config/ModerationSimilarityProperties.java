package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.moderation.similarity")
public class ModerationSimilarityProperties {

    private Es es = new Es();

    @Data
    public static class Es {
        /** index name, e.g. ad_violation_samples_v1 */
        private String index = "ad_violation_samples_v1";

        /** whether IK analyzers are expected to exist on the ES side */
        private boolean ikEnabled = true;

        /** embedding model name */
        private String embeddingModel = "text-embedding-v4";

        /** topK */
        private int topK = 5;

        /** distance threshold (cosine distance) */
        private double threshold = 0.15;

        /** Must match embedding output dims; set to 0 until confirmed. */
        private int embeddingDims = 0;
    }
}
