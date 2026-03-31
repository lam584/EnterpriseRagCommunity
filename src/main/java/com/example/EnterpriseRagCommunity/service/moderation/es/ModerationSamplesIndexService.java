package com.example.EnterpriseRagCommunity.service.moderation.es;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModerationSamplesIndexService {

    private static final Logger log = LoggerFactory.getLogger(ModerationSamplesIndexService.class);

    private final ElasticsearchTemplate template;
    private final ModerationSamplesIndexConfigService indexConfigService;
    private final ModerationSimilarityConfigRepository similarityConfigRepository;
    private final ElasticsearchIkAnalyzerProbe ikProbe;
    private final DependencyIsolationGuard dependencyIsolationGuard;
    private final DependencyCircuitBreakerService dependencyCircuitBreakerService;
    private final AtomicBoolean ikDisabledWarned = new AtomicBoolean(false);

    public boolean indexExists() {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        return dependencyCircuitBreakerService.run("ES", () -> {
            IndexOperations ops = template.indexOps(IndexCoordinates.of(indexConfigService.getIndexNameOrDefault()));
            return ops.exists();
        });
    }

    public String getIndexName() {
        return indexConfigService.getIndexNameOrDefault();
    }

    public Integer getEmbeddingDimsInMapping() {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        return dependencyCircuitBreakerService.run("ES", () -> {
            IndexOperations ops = template.indexOps(IndexCoordinates.of(indexConfigService.getIndexNameOrDefault()));
            if (!ops.exists()) return null;
            return readEmbeddingDims(ops);
        });
    }

    public Long countDocs() {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        try {
            return dependencyCircuitBreakerService.run("ES", () -> {
                IndexCoordinates idx = IndexCoordinates.of(indexConfigService.getIndexNameOrDefault());
                return template.count(Query.findAll(), idx);
            });
        } catch (Exception e) {
            return null;
        }
    }

    public void recreateIndex(int embeddingDims) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            IndexOperations ops = template.indexOps(IndexCoordinates.of(indexConfigService.getIndexNameOrDefault()));
            if (ops.exists()) {
                ops.delete();
            }
            tryCreate(ops, resolveIkEnabled(), embeddingDims);
            return null;
        });
    }

    /**
     * Create index if missing.
     *
     * Note: if embeddingDims is 0, we'll create the index without the vector field.
     */
    public void ensureIndex() {
        ensureIndex(resolveDefaultEmbeddingDims());
    }

    /**
     * Create index if missing, using provided dims for mapping creation.
     *
     * This is useful when embedding dims isn't configured yet;
     * we can infer dims from the embedding result and still bootstrap ES mapping correctly.
     */
    public void ensureIndex(int embeddingDims) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            IndexOperations ops = template.indexOps(IndexCoordinates.of(indexConfigService.getIndexNameOrDefault()));
            if (ops.exists()) {
                if (embeddingDims > 0) {
                    Integer existingDims = readEmbeddingDims(ops);
                    if (existingDims == null || existingDims != embeddingDims) {
                        ops.delete();
                        tryCreate(ops, resolveIkEnabled(), embeddingDims);
                    }
                }
                return null;
            }

            tryCreate(ops, resolveIkEnabled(), embeddingDims);
            return null;
        });
    }

    private boolean resolveIkEnabled() {
        boolean configured = indexConfigService.isIkEnabledOrDefault();
        if (!configured) return false;
        boolean supported = ikProbe.isIkSupported();
        if (!supported && ikDisabledWarned.compareAndSet(false, true)) {
            log.warn("IK analyzer is enabled in config but not available on this Elasticsearch cluster. Falling back to standard analyzer. index={}", indexConfigService.getIndexNameOrDefault());
        }
        return supported;
    }

    private int resolveDefaultEmbeddingDims() {
        try {
            ModerationSimilarityConfigEntity cfg = similarityConfigRepository.findAll().stream().findFirst().orElse(null);
            if (cfg != null && cfg.getEmbeddingDims() != null && cfg.getEmbeddingDims() > 0) {
                return cfg.getEmbeddingDims();
            }
        } catch (Exception ignore) {
        }
        int dims = indexConfigService.getEmbeddingDimsOrDefault();
        return Math.max(0, dims);
    }

    private Integer readEmbeddingDims(IndexOperations ops) {
        try {
            Map<String, Object> mapping = ops.getMapping();
            return extractEmbeddingDims(mapping);
        } catch (Exception e) {
            log.warn("Read ES mapping failed. err={}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Integer extractEmbeddingDims(Map<String, Object> mapping) {
        if (mapping == null) return null;
        Object props0 = mapping.get("properties");
        Integer dims = extractEmbeddingDimsFromProperties(props0);
        if (dims != null) return dims;

        Object mappings0 = mapping.get("mappings");
        if (mappings0 instanceof Map<?, ?> mm) {
            Object props1 = ((Map<String, Object>) mm).get("properties");
            dims = extractEmbeddingDimsFromProperties(props1);
            return dims;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Integer extractEmbeddingDimsFromProperties(Object props0) {
        if (!(props0 instanceof Map<?, ?>)) return null;
        Map<String, Object> props = (Map<String, Object>) props0;
        Object emb0 = props.get("embedding");
        if (!(emb0 instanceof Map<?, ?>)) return null;
        Map<String, Object> emb = (Map<String, Object>) emb0;
        Object dims0 = emb.get("dims");
        if (dims0 instanceof Number n) return n.intValue();
        if (dims0 instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled) {
        tryCreate(ops, ikEnabled, resolveDefaultEmbeddingDims());
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled, int embeddingDims) {
        Document settings = Document.from(buildSettings(ikEnabled));
        Document mapping = Document.from(buildMapping(ikEnabled, embeddingDims));

        try {
            // create index + mapping in one go
            ops.create(settings);
            ops.putMapping(mapping);
        } catch (Exception first) {
            // If IK analyzer is missing, index creation will fail.
            if (ikEnabled) {
                log.warn("Create ES index failed with IK enabled (maybe IK plugin missing). Retrying with IK disabled. err={}", first.getMessage());
                // Best-effort cleanup if partially created
                try {
                    if (ops.exists()) {
                        ops.delete();
                    }
                } catch (Exception ignore) {
                }

                Document settings2 = Document.from(buildSettings(false));
                Document mapping2 = Document.from(buildMapping(false, embeddingDims));
                ops.create(settings2);
                ops.putMapping(mapping2);
                return;
            }
            throw first;
        }
    }

    private Map<String, Object> buildSettings(boolean ikEnabled) {
        // IMPORTANT:
        // IndexOperations.create(Document) expects *index settings* (equivalent to the body of PUT /{index}/_settings),
        // NOT a full create-index request body. If we wrap with {"settings":{...}}, Spring Data will wrap again
        // and Elasticsearch will see unknown settings like "index.settings.index.number_of_shards".

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("number_of_shards", 1);
        index.put("number_of_replicas", 0);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", index);

        if (ikEnabled) {
            // Requires IK plugin installed on ES node.
            Map<String, Object> analysis = new LinkedHashMap<>();
            Map<String, Object> analyzer = new LinkedHashMap<>();

            analyzer.put("ik_max_word", Map.of("type", "ik_max_word"));
            analyzer.put("ik_smart", Map.of("type", "ik_smart"));

            analysis.put("analyzer", analyzer);
            settings.put("analysis", analysis);
        }

        return settings;
    }

    private Map<String, Object> buildMapping(boolean ikEnabled) {
        return buildMapping(ikEnabled, resolveDefaultEmbeddingDims());
    }

    private Map<String, Object> buildMapping(boolean ikEnabled, int embeddingDims) {
        int dims = embeddingDims;

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> propsMap = new LinkedHashMap<>();

        propsMap.put("id", Map.of("type", "long"));
        propsMap.put("category", Map.of("type", "keyword"));
        propsMap.put("source", Map.of("type", "keyword"));
        propsMap.put("risk_level", Map.of("type", "integer"));
        propsMap.put("enabled", Map.of("type", "boolean"));
        propsMap.put("text_hash", Map.of("type", "keyword"));
        propsMap.put("created_at", Map.of("type", "date"));
        propsMap.put("updated_at", Map.of("type", "date"));
        propsMap.put("labels", Map.of("type", "keyword"));

        propsMap.put("raw_text", Map.of("type", "text"));

        Map<String, Object> normText = new LinkedHashMap<>();
        normText.put("type", "text");
        if (ikEnabled) {
            normText.put("analyzer", "ik_max_word");
            normText.put("search_analyzer", "ik_smart");
        }
        propsMap.put("normalized_text", normText);

        if (dims > 0) {
            Map<String, Object> emb = new LinkedHashMap<>();
            emb.put("type", "dense_vector");
            emb.put("dims", dims);
            emb.put("index", true);
            emb.put("similarity", "cosine");
            propsMap.put("embedding", emb);
        }

        root.put("properties", propsMap);
        return root;
    }
}
