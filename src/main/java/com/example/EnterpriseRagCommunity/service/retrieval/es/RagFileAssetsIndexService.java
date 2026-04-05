package com.example.EnterpriseRagCommunity.service.retrieval.es;

import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RagFileAssetsIndexService {

    private static final Logger log = LoggerFactory.getLogger(RagFileAssetsIndexService.class);
    private static final String DEFAULT_INDEX_NAME = "rag_file_assets_v1";

    private final ElasticsearchTemplate template;
    private final ElasticsearchIkAnalyzerProbe ikProbe;
    private final DependencyIsolationGuard dependencyIsolationGuard;
    private final DependencyCircuitBreakerService dependencyCircuitBreakerService;
    private final AtomicBoolean ikDisabledWarned = new AtomicBoolean(false);

    public String defaultIndexName() {
        return DEFAULT_INDEX_NAME;
    }

    public void recreateIndex(String indexName, int embeddingDims, boolean ikEnabled) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            String idx = (indexName == null || indexName.isBlank()) ? defaultIndexName() : indexName.trim();
            IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
            if (ops.exists()) {
                ops.delete();
            }
            tryCreate(ops, resolveIkEnabled(ikEnabled), embeddingDims);
            return null;
        });
    }

    public void ensureIndex(String indexName, int embeddingDims, boolean ikEnabled) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            String idx = (indexName == null || indexName.isBlank()) ? defaultIndexName() : indexName.trim();
            IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
            if (!ops.exists()) {
                tryCreate(ops, resolveIkEnabled(ikEnabled), embeddingDims);
                return null;
            }
            if (embeddingDims > 0) {
                Integer existingDims = RagPostsIndexService.extractEmbeddingDims(ops.getMapping());
                if (existingDims == null) {
                    throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' has no embedding.dims but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
                }
                if (existingDims != embeddingDims) {
                    throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' embedding.dims=" + existingDims + " but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
                }
            }
            return null;
        });
    }

    private boolean resolveIkEnabled(boolean configured) {
        if (!configured) return false;
        boolean supported = ikProbe.isIkSupported();
        if (!supported && ikDisabledWarned.compareAndSet(false, true)) {
            log.warn("IK analyzer is enabled in config but not available on this Elasticsearch cluster. Falling back to standard analyzer. index={}", defaultIndexName());
        }
        return supported;
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled, int embeddingDims) {
        EsIndexSupport.tryCreateWithIkFallback(ops, ikEnabled, embeddingDims, log,
                this::buildSettings, enabled -> buildMapping(enabled, embeddingDims));
    }

    private Map<String, Object> buildSettings(boolean ikEnabled) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("number_of_shards", 1);
        index.put("number_of_replicas", 0);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", index);

        if (ikEnabled) {
            Map<String, Object> analysis = new LinkedHashMap<>();
            Map<String, Object> analyzer = new LinkedHashMap<>();
            analyzer.put("ik_max_word", Map.of("type", "ik_max_word"));
            analyzer.put("ik_smart", Map.of("type", "ik_smart"));
            analysis.put("analyzer", analyzer);
            settings.put("analysis", analysis);
        }

        return settings;
    }

    private Map<String, Object> buildMapping(boolean ikEnabled, int embeddingDims) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> propsMap = new LinkedHashMap<>();

        propsMap.put("id", Map.of("type", "keyword"));
        propsMap.put("file_asset_id", Map.of("type", "long"));
        propsMap.put("owner_user_id", Map.of("type", "long"));
        propsMap.put("post_ids", Map.of("type", "long"));
        propsMap.put("chunk_index", Map.of("type", "integer"));
        propsMap.put("content_hash", Map.of("type", "keyword"));
        propsMap.put("created_at", Map.of("type", "date"));
        propsMap.put("updated_at", Map.of("type", "date"));

        propsMap.put("file_name", Map.of("type", "keyword"));
        propsMap.put("mime_type", Map.of("type", "keyword"));

        EsIndexSupport.putTextField(propsMap, "content_text", ikEnabled);
        EsIndexSupport.putDenseVectorField(propsMap, "embedding", embeddingDims);

        root.put("properties", propsMap);
        return root;
    }
}
