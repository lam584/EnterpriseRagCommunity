package com.example.EnterpriseRagCommunity.service.retrieval.es;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIndexSettingsSupport;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagPostsIndexService {

    private static final Logger log = LoggerFactory.getLogger(RagPostsIndexService.class);

    private final ElasticsearchTemplate template;
    private final RetrievalRagProperties props;
    private final ElasticsearchIkAnalyzerProbe ikProbe;
    private final DependencyIsolationGuard dependencyIsolationGuard;
    private final DependencyCircuitBreakerService dependencyCircuitBreakerService;
    private final AtomicBoolean ikDisabledWarned = new AtomicBoolean(false);

    public String defaultIndexName() {
        return props.getEs().getIndex();
    }

    public void recreateIndex(String indexName, int embeddingDims) {
        EsIndexSupport.recreateIndex(
                dependencyIsolationGuard,
                dependencyCircuitBreakerService,
                template,
                indexName,
                this::defaultIndexName,
                ops -> {
            tryCreate(ops, resolveIkEnabled(), embeddingDims);
                }
        );
    }

    public void ensureIndex(String indexName, int embeddingDims) {
        EsIndexSupport.ensureIndex(
                dependencyIsolationGuard,
                dependencyCircuitBreakerService,
                template,
                indexName,
                this::defaultIndexName,
                embeddingDims,
                this::resolveIkEnabled,
                this::readEmbeddingDims,
                this::tryCreate
        );
    }

    private boolean resolveIkEnabled() {
        return EsIndexSupport.resolveIkEnabled(props, ikProbe, ikDisabledWarned, log, defaultIndexName());
    }

    private Integer readEmbeddingDims(IndexOperations ops) {
        try {
            Map<String, Object> mapping = ops.getMapping();
            return EmbeddingMappingSupport.extractEmbeddingDims(mapping);
        } catch (Exception e) {
            log.warn("Read ES mapping failed. err={}", e.getMessage());
            return null;
        }
    }

    public static Integer extractEmbeddingDims(Map<String, Object> mapping) {
        return EmbeddingMappingSupport.extractEmbeddingDims(mapping);
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled, int embeddingDims) {
        EsIndexSupport.tryCreateWithIkFallback(ops, ikEnabled, embeddingDims, log,
                this::buildSettings, enabled -> buildMapping(enabled, embeddingDims));
    }

    private Map<String, Object> buildSettings(boolean ikEnabled) {
        return ElasticsearchIndexSettingsSupport.buildBasicIndexSettings(ikEnabled);
    }

    private Map<String, Object> buildMapping(boolean ikEnabled, int embeddingDims) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> propsMap = new LinkedHashMap<>();

        propsMap.put("id", Map.of("type", "keyword"));
        propsMap.put("post_id", Map.of("type", "long"));
        propsMap.put("board_id", Map.of("type", "long"));
        propsMap.put("author_id", Map.of("type", "long"));
        propsMap.put("chunk_index", Map.of("type", "integer"));
        propsMap.put("content_hash", Map.of("type", "keyword"));
        propsMap.put("created_at", Map.of("type", "date"));
        propsMap.put("updated_at", Map.of("type", "date"));

        propsMap.put("title", Map.of("type", "text"));

        EsIndexSupport.putTextField(propsMap, "content_text", ikEnabled);
        EsIndexSupport.putDenseVectorField(propsMap, "embedding", embeddingDims);

        root.put("properties", propsMap);
        return root;
    }
}
