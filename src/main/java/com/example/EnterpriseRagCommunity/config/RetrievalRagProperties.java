package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.retrieval.rag")
public class RetrievalRagProperties {

    private Es es = new Es();

    @Data
    public static class Es {
        private String index = "rag_post_chunks_v1";
        private boolean ikEnabled = true;
        private String embeddingModel = "";
        private int embeddingDims = 0;
    }
}
